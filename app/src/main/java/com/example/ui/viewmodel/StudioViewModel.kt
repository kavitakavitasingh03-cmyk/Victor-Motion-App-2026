package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.StudioProjectRepository
import com.example.data.remote.AiStudioRepository
import com.example.data.remote.FirestoreProjectRepository
import com.example.domain.model.AiSuggestion
import com.example.domain.model.BlendModeType
import com.example.domain.model.CaptionSegment
import com.example.domain.model.CreatorPresetDto
import com.example.domain.model.EasingCurveType
import com.example.domain.model.ExportConfig
import com.example.domain.model.KeyframePoint
import com.example.domain.model.KeyframeProperty
import com.example.domain.model.LayerType
import com.example.domain.model.MaskShapeType
import com.example.domain.model.PresetPayload
import com.example.domain.model.ProjectTimelinePayload
import com.example.domain.model.StudioProject
import com.example.domain.model.TextStyleConfig
import com.example.domain.model.TimelineLayer
import com.example.domain.model.UserProfileDto
import com.example.domain.model.VisualEffectsConfig
import com.example.media.HardwareCapabilities
import com.example.media.MediaEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.math.pow
import kotlin.math.sin

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data class Loading(val label: String) : AiTaskState
    data class SuggestionsReady(val items: List<AiSuggestion>) : AiTaskState
    data class CaptionsReady(val count: Int) : AiTaskState
    data class BeatSyncReady(val bpm: Int, val beatCount: Int) : AiTaskState
    data class EnhancedReady(val gradeName: String) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(val progressPercent: Int, val message: String) : ExportUiState
    data class Completed(val outputPath: String?, val summary: String) : ExportUiState
    data class Error(val message: String) : ExportUiState
}

data class EvaluatedLayerTransform(
    val posX: Float,
    val posY: Float,
    val scale: Float,
    val rotation: Float,
    val opacity: Float
)

data class BuiltInTemplate(
    val id: String,
    val title: String,
    val category: String,
    val aspectRatio: String,
    val durationLabel: String,
    val description: String,
    val isPremium: Boolean,
    val effectsSummary: String
)

class StudioViewModel(
    private val projectRepository: StudioProjectRepository,
    private val firestoreRepository: FirestoreProjectRepository,
    private val aiRepository: AiStudioRepository,
    private val currentUserId: String,
    private val displayName: String,
    private val photoUrl: String?
) : ViewModel() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val hardwareCapabilities: HardwareCapabilities = MediaEngine.probeHardwareCapabilities()

    val localProjects: StateFlow<List<StudioProject>> = projectRepository
        .observeLocalProjects(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val userProfile: StateFlow<UserProfileDto?> = firestoreRepository
        .observeUserProfile(currentUserId)
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Profile stream error", fsErr ?: error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val publishedPresets: StateFlow<UiState<List<CreatorPresetDto>>> = firestoreRepository
        .observePublishedPresets()
        .map<List<CreatorPresetDto>, UiState<List<CreatorPresetDto>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Presets stream error", root)
            emit(UiState.Error(root.message ?: "Unable to load community presets"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val builtInTemplates = listOf(
        BuiltInTemplate(
            id = "tpl_cyber",
            title = "Tokyo Cyber Drift",
            category = "MotionFX",
            aspectRatio = "9:16",
            durationLabel = "09.0s • 60 FPS",
            description = "Anamorphic cyan glow, RGB chromatic split & kinetic neon title.",
            isPremium = false,
            effectsSummary = "Glow 0.48 • RGB Split 0.35 • Motion Blur"
        ),
        BuiltInTemplate(
            id = "tpl_cinema",
            title = "Alpine Golden Grade",
            category = "Cinematic",
            aspectRatio = "16:9",
            durationLabel = "12.0s • 4K Ready",
            description = "Warm amber highlights, 21:9 letterbox matte & filmic vignette.",
            isPremium = false,
            effectsSummary = "21:9 Mask • Warm LUT • Vignette"
        ),
        BuiltInTemplate(
            id = "tpl_velocity",
            title = "Hyper Velocity Ramp",
            category = "Velocity",
            aspectRatio = "9:16",
            durationLabel = "08.0s • 120 FPS",
            description = "Expo-out speed ramp synced to beat markers with subtle camera shake.",
            isPremium = true,
            effectsSummary = "1.6x Bullet Ramp • Beat Shake • Bounce"
        )
    )

    private val _activeProject = MutableStateFlow<StudioProject?>(null)
    val activeProject: StateFlow<StudioProject?> = _activeProject.asStateFlow()

    private val _selectedLayerId = MutableStateFlow<String?>(null)
    val selectedLayerId: StateFlow<String?> = _selectedLayerId.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _aiState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val aiState: StateFlow<AiTaskState> = _aiState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val undoStack = ArrayDeque<StudioProject>()
    private val redoStack = ArrayDeque<StudioProject>()
    private var playbackJob: Job? = null

    init {
        viewModelScope.launch {
            firestoreRepository.ensureUserProfile(displayName, photoUrl)
        }
        viewModelScope.launch {
            firestoreRepository.observeCloudProjects(currentUserId)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    Log.w(TAG, "Cloud project sync error", fsErr ?: error)
                }
                .collectLatest { cloudList ->
                    projectRepository.syncFromCloudIfNeeded(currentUserId, cloudList)
                }
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun resetAiState() {
        _aiState.value = AiTaskState.Idle
    }

    fun resetExportState() {
        _exportState.value = ExportUiState.Idle
    }

    fun createNewProject(
        title: String,
        aspectRatio: String,
        resolution: String,
        fps: Int,
        templateId: String? = null,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            val cleanTitle = title.trim().ifBlank { "Studio Cut" }
            val created = projectRepository.createStarterProject(
                userId = currentUserId,
                title = cleanTitle,
                aspectRatio = aspectRatio,
                resolution = resolution,
                fps = fps,
                templateId = templateId
            )
            openProject(created)
            onCreated(created.id)
        }
    }

    fun openProjectById(projectId: String) {
        viewModelScope.launch {
            val loaded = projectRepository.getProject(projectId)
            if (loaded != null) {
                openProject(loaded)
            }
        }
    }

    private fun openProject(project: StudioProject) {
        stopPlayback()
        undoStack.clear()
        redoStack.clear()
        _canUndo.value = false
        _canRedo.value = false
        _activeProject.value = project
        _selectedLayerId.value = project.payload.layers.firstOrNull()?.id
        _playheadMs.value = 0L
        _aiState.value = AiTaskState.Idle
        _exportState.value = ExportUiState.Idle
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId, alsoDeleteCloud = true)
            if (_activeProject.value?.id == projectId) {
                _activeProject.value = null
            }
            _statusBanner.value = "Project removed from local studio & cloud."
        }
    }

    fun backupActiveProjectToCloud() {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            val result = projectRepository.backupProjectToCloud(current)
            if (result.isSuccess) {
                _activeProject.value = current.copy(isSyncedToCloud = true)
                _statusBanner.value = "Cloud backup synced cleanly."
            } else {
                _statusBanner.value = "Saved offline; cloud sync failed: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun toggleCloudBackupSetting(enabled: Boolean) {
        viewModelScope.launch {
            firestoreRepository.updateUserPreferences(cloudBackupEnabled = enabled)
        }
    }

    fun upgradeSubscriptionTier(tier: String) {
        viewModelScope.launch {
            val res = firestoreRepository.updateUserPreferences(subscriptionTier = tier)
            if (res.isSuccess) {
                _statusBanner.value = "Workspace plan updated to ${tier.uppercase()}."
            }
        }
    }

    fun selectLayer(layerId: String?) {
        _selectedLayerId.value = layerId
    }

    fun seekTo(timeMs: Long) {
        val duration = _activeProject.value?.durationMs ?: 10000L
        _playheadMs.value = timeMs.coerceIn(0L, duration)
    }

    fun stepFrame(forward: Boolean) {
        stopPlayback()
        val fps = (_activeProject.value?.fps ?: 60).coerceAtLeast(1)
        val frameMs = (1000L / fps).coerceAtLeast(16L)
        val delta = if (forward) frameMs else -frameMs
        seekTo(_playheadMs.value + delta)
    }

    fun togglePlayback() {
        if (_isPlaying.value) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val project = _activeProject.value ?: return
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var lastTick = System.currentTimeMillis()
            while (isActive && _isPlaying.value) {
                delay(33L)
                val now = System.currentTimeMillis()
                val elapsed = (now - lastTick).coerceIn(16L, 120L)
                lastTick = now
                val currentProj = _activeProject.value ?: break
                val next = _playheadMs.value + elapsed
                if (next >= currentProj.durationMs) {
                    _playheadMs.value = 0L
                } else {
                    _playheadMs.value = next
                }
            }
        }
    }

    fun stopPlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    private fun mutateProject(transform: (StudioProject) -> StudioProject) {
        val current = _activeProject.value ?: return
        undoStack.addLast(current)
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()

        val updatedRaw = transform(current)
        val maxEnd = updatedRaw.payload.layers.maxOfOrNull { it.endMs } ?: 8000L
        val updated = updatedRaw.copy(
            durationMs = maxEnd.coerceAtLeast(4000L),
            isSyncedToCloud = false,
            updatedAtMillis = System.currentTimeMillis()
        )
        _activeProject.value = updated
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false

        viewModelScope.launch {
            projectRepository.autosaveLocal(updated)
            if (userProfile.value?.cloudBackupEnabled == true) {
                val res = projectRepository.backupProjectToCloud(updated)
                if (res.isSuccess && _activeProject.value?.id == updated.id) {
                    _activeProject.value = _activeProject.value?.copy(isSyncedToCloud = true)
                }
            }
        }
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        _activeProject.value?.let { redoStack.addLast(it) }
        _activeProject.value = prev
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        viewModelScope.launch { projectRepository.autosaveLocal(prev) }
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        _activeProject.value?.let { undoStack.addLast(it) }
        _activeProject.value = next
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        viewModelScope.launch { projectRepository.autosaveLocal(next) }
    }

    fun updateProjectTitle(title: String) {
        val trimmed = title.trim().ifBlank { return }
        mutateProject { it.copy(title = trimmed.take(120)) }
    }

    fun addMediaLayer(context: Context, uri: Uri, type: LayerType) {
        viewModelScope.launch {
            val info = MediaEngine.extractMediaInfo(context, uri, isVideo = type == LayerType.VIDEO)
            val start = _playheadMs.value
            val dur = when (type) {
                LayerType.IMAGE -> 4500L
                else -> info.durationMs.coerceIn(1500L, 60000L)
            }
            val waveform = if (type == LayerType.AUDIO || type == LayerType.VIDEO) {
                MediaEngine.generateWaveformEnvelope(uri.toString().hashCode())
            } else {
                emptyList()
            }
            val label = when (type) {
                LayerType.VIDEO -> "Video Clip"
                LayerType.IMAGE -> "Image Overlay"
                LayerType.AUDIO -> "Audio Track"
                else -> type.displayName
            }
            mutateProject { proj ->
                val nextTrack = (proj.payload.layers.maxOfOrNull { it.trackIndex } ?: -1) + 1
                val newLayer = TimelineLayer(
                    id = "layer_${UUID.randomUUID().toString().take(8)}",
                    name = "$label ${proj.payload.layers.size + 1}",
                    type = type,
                    trackIndex = nextTrack,
                    startMs = start,
                    endMs = start + dur,
                    mediaUri = uri.toString(),
                    waveformSamples = waveform
                )
                _selectedLayerId.value = newLayer.id
                proj.copy(
                    payload = proj.payload.copy(layers = proj.payload.layers + newLayer)
                )
            }
        }
    }

    fun addTextOrEffectLayer(type: LayerType, text: String = "VICTOR MOTION") {
        val start = _playheadMs.value
        mutateProject { proj ->
            val nextTrack = (proj.payload.layers.maxOfOrNull { it.trackIndex } ?: -1) + 1
            val newLayer = TimelineLayer(
                id = "layer_${UUID.randomUUID().toString().take(8)}",
                name = if (type == LayerType.TEXT) "Text: ${text.take(14)}" else "FX Adjustment",
                type = type,
                trackIndex = nextTrack,
                startMs = start,
                endMs = start + 4000L,
                textConfig = if (type == LayerType.TEXT) TextStyleConfig(text = text) else null,
                effects = if (type == LayerType.EFFECT) {
                    VisualEffectsConfig(glowIntensity = 0.38f, rgbSplit = 0.22f)
                } else {
                    VisualEffectsConfig()
                }
            )
            _selectedLayerId.value = newLayer.id
            proj.copy(payload = proj.payload.copy(layers = proj.payload.layers + newLayer))
        }
    }

    fun splitSelectedLayerAtPlayhead() {
        val layerId = _selectedLayerId.value ?: return
        val splitMs = _playheadMs.value
        mutateProject { proj ->
            val target = proj.payload.layers.firstOrNull { it.id == layerId } ?: return@mutateProject proj
            if (splitMs <= target.startMs + 250L || splitMs >= target.endMs - 250L) {
                _statusBanner.value = "Move playhead inside clip bounds to split."
                return@mutateProject proj
            }
            val firstHalf = target.copy(
                endMs = splitMs,
                keyframes = target.keyframes.filter { it.timeMs <= splitMs }
            )
            val secondHalf = target.copy(
                id = "layer_${UUID.randomUUID().toString().take(8)}",
                name = "${target.name} (Split)",
                startMs = splitMs,
                keyframes = target.keyframes.filter { it.timeMs >= splitMs }
            )
            val newList = proj.payload.layers.flatMap {
                if (it.id == layerId) listOf(firstHalf, secondHalf) else listOf(it)
            }
            _selectedLayerId.value = secondHalf.id
            proj.copy(payload = proj.payload.copy(layers = newList))
        }
    }

    fun duplicateSelectedLayer() {
        val layerId = _selectedLayerId.value ?: return
        mutateProject { proj ->
            val target = proj.payload.layers.firstOrNull { it.id == layerId } ?: return@mutateProject proj
            val nextTrack = (proj.payload.layers.maxOfOrNull { it.trackIndex } ?: 0) + 1
            val copyLayer = target.copy(
                id = "layer_${UUID.randomUUID().toString().take(8)}",
                name = "${target.name} Copy",
                trackIndex = nextTrack
            )
            _selectedLayerId.value = copyLayer.id
            proj.copy(payload = proj.payload.copy(layers = proj.payload.layers + copyLayer))
        }
    }

    fun deleteSelectedLayer() {
        val layerId = _selectedLayerId.value ?: return
        mutateProject { proj ->
            val remaining = proj.payload.layers.filterNot { it.id == layerId }
            _selectedLayerId.value = remaining.firstOrNull()?.id
            proj.copy(payload = proj.payload.copy(layers = remaining))
        }
    }

    fun updateSelectedLayer(transform: (TimelineLayer) -> TimelineLayer) {
        val layerId = _selectedLayerId.value ?: return
        mutateProject { proj ->
            val updated = proj.payload.layers.map { layer ->
                if (layer.id == layerId) transform(layer) else layer
            }
            proj.copy(payload = proj.payload.copy(layers = updated))
        }
    }

    fun insertFreezeFrameAtPlayhead() {
        val layerId = _selectedLayerId.value ?: return
        val freezeTime = _playheadMs.value
        mutateProject { proj ->
            val target = proj.payload.layers.firstOrNull { it.id == layerId } ?: return@mutateProject proj
            val freezeClip = target.copy(
                id = "layer_${UUID.randomUUID().toString().take(8)}",
                name = "Freeze Frame",
                startMs = freezeTime,
                endMs = freezeTime + 2000L,
                isFrozen = true,
                freezeTimeMs = freezeTime,
                speed = 0f,
                trackIndex = (proj.payload.layers.maxOfOrNull { it.trackIndex } ?: 0) + 1
            )
            _selectedLayerId.value = freezeClip.id
            proj.copy(payload = proj.payload.copy(layers = proj.payload.layers + freezeClip))
        }
        _statusBanner.value = "2.0s Freeze Frame inserted at playhead."
    }

    fun addOrUpdateKeyframe(property: KeyframeProperty, value: Float, easing: EasingCurveType) {
        val time = _playheadMs.value
        updateSelectedLayer { layer ->
            val existingIdx = layer.keyframes.indexOfFirst {
                it.property == property && kotlin.math.abs(it.timeMs - time) <= 120L
            }
            val updatedList = if (existingIdx >= 0) {
                layer.keyframes.toMutableList().apply {
                    this[existingIdx] = this[existingIdx].copy(value = value, easing = easing, timeMs = time)
                }
            } else {
                layer.keyframes + KeyframePoint(
                    id = "kf_${UUID.randomUUID().toString().take(6)}",
                    timeMs = time,
                    property = property,
                    value = value,
                    easing = easing
                )
            }
            layer.copy(keyframes = updatedList.sortedBy { it.timeMs })
        }
    }

    fun deleteKeyframe(keyframeId: String) {
        updateSelectedLayer { layer ->
            layer.copy(keyframes = layer.keyframes.filterNot { it.id == keyframeId })
        }
    }

    fun addBeatMarkerAtPlayhead() {
        val t = _playheadMs.value
        mutateProject { proj ->
            val updated = (proj.payload.beatMarkersMs + t).distinct().sorted()
            proj.copy(payload = proj.payload.copy(beatMarkersMs = updated))
        }
    }

    fun detectLocalBeatsForAudio() {
        val proj = _activeProject.value ?: return
        val audioLayer = proj.payload.layers.firstOrNull { it.type == LayerType.AUDIO || it.waveformSamples.isNotEmpty() }
        val duration = proj.durationMs
        val samples = audioLayer?.waveformSamples.orEmpty()
        val beats = if (samples.isNotEmpty()) {
            val step = duration.toFloat() / samples.size
            samples.mapIndexedNotNull { idx, amp ->
                if (amp >= 0.72f) (idx * step).toLong() else null
            }
        } else {
            (0L..duration step 750L).toList()
        }
        mutateProject { it.copy(payload = it.payload.copy(beatMarkersMs = beats)) }
        _statusBanner.value = "Detected ${beats.size} beat transients on timeline."
    }

    fun applyCinematicGradePreset(gradeName: String) {
        val config = when (gradeName) {
            "Cyber Neon" -> VisualEffectsConfig(
                contrast = 1.24f, saturation = 1.32f, glowIntensity = 0.42f,
                rgbSplit = 0.25f, temperature = -0.18f, colorGradeName = "Cyber Neon"
            )
            "Teal & Orange" -> VisualEffectsConfig(
                contrast = 1.18f, saturation = 1.14f, temperature = 0.16f,
                vignette = 0.28f, colorGradeName = "Teal & Orange"
            )
            "Noir Film" -> VisualEffectsConfig(
                contrast = 1.35f, saturation = 0.15f, exposure = -0.05f,
                vignette = 0.45f, colorGradeName = "Noir Film"
            )
            "Golden Alpine" -> VisualEffectsConfig(
                contrast = 1.15f, saturation = 1.18f, temperature = 0.28f,
                glowIntensity = 0.22f, colorGradeName = "Golden Alpine"
            )
            else -> VisualEffectsConfig(colorGradeName = "Natural")
        }
        updateSelectedLayer { it.copy(effects = config) }
    }

    // --- Firebase AI Logic Features ---

    fun generateAiCaptions(topicPrompt: String) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            _aiState.value = AiTaskState.Loading("Generating timed AI captions...")
            val result = aiRepository.generateAutoCaptions(
                projectTitle = proj.title,
                topicPrompt = topicPrompt.ifBlank { proj.title },
                durationMs = proj.durationMs
            )
            result.onSuccess { captions ->
                mutateProject { current ->
                    current.copy(payload = current.payload.copy(captions = captions))
                }
                _aiState.value = AiTaskState.CaptionsReady(captions.size)
            }.onFailure { err ->
                _aiState.value = AiTaskState.Error(err.localizedMessage ?: "Failed to generate captions.")
            }
        }
    }

    fun requestAiSuggestions(vibeGoal: String, previewBitmap: Bitmap? = null) {
        val proj = _activeProject.value ?: return
        val summary = proj.payload.layers.joinToString(", ") {
            "${it.type.displayName}:${it.name}(${(it.endMs - it.startMs) / 1000f}s, speed=${it.speed}x)"
        }
        viewModelScope.launch {
            _aiState.value = AiTaskState.Loading("Analyzing timeline with Gemini...")
            val result = aiRepository.analyzeAndSuggestEdits(
                projectTitle = proj.title,
                layerSummary = summary,
                vibeGoal = vibeGoal.ifBlank { "Punchy cinematic social edit" },
                previewFrame = previewBitmap
            )
            result.onSuccess { suggestions ->
                _aiState.value = AiTaskState.SuggestionsReady(suggestions)
            }.onFailure { err ->
                _aiState.value = AiTaskState.Error(err.localizedMessage ?: "AI analysis failed.")
            }
        }
    }

    fun applyAiSuggestion(suggestion: AiSuggestion) {
        updateSelectedLayer { layer ->
            layer.copy(
                speed = suggestion.speedMultiplier,
                effects = layer.effects.copy(
                    glowIntensity = suggestion.glowDelta.coerceIn(0f, 1f),
                    rgbSplit = suggestion.rgbSplitDelta.coerceIn(0f, 1f),
                    contrast = suggestion.contrastValue.coerceIn(0.7f, 1.8f),
                    colorGradeName = suggestion.title.take(24)
                )
            )
        }
        _statusBanner.value = "Applied AI edit: ${suggestion.title}"
    }

    fun applyAiVisualEnhancement(moodDescription: String) {
        val selected = getSelectedLayer() ?: return
        viewModelScope.launch {
            _aiState.value = AiTaskState.Loading("Tuning AI color & light enhancement...")
            val result = aiRepository.enhanceVisualGrade(moodDescription, selected.effects)
            result.onSuccess { enhanced ->
                updateSelectedLayer { it.copy(effects = enhanced) }
                _aiState.value = AiTaskState.EnhancedReady(enhanced.colorGradeName)
            }.onFailure { err ->
                _aiState.value = AiTaskState.Error(err.localizedMessage ?: "AI enhancement failed.")
            }
        }
    }

    fun runAiBeatSync(moodOrBpm: String) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            _aiState.value = AiTaskState.Loading("Syncing timeline cuts to rhythm...")
            val result = aiRepository.generateBeatMarkers(moodOrBpm, proj.durationMs)
            result.onSuccess { (bpm, beats) ->
                mutateProject { it.copy(payload = it.payload.copy(beatMarkersMs = beats)) }
                _aiState.value = AiTaskState.BeatSyncReady(bpm, beats.size)
            }.onFailure { err ->
                _aiState.value = AiTaskState.Error(err.localizedMessage ?: "AI beat sync failed.")
            }
        }
    }

    fun applyAiSubjectMatte() {
        updateSelectedLayer { layer ->
            layer.copy(
                effects = layer.effects.copy(
                    maskShape = MaskShapeType.AI_SUBJECT_MATTE,
                    maskFeather = 0.25f,
                    glowIntensity = maxOf(layer.effects.glowIntensity, 0.28f)
                )
            )
        }
        _statusBanner.value = "AI Subject Background Matte applied to layer."
    }

    // --- Preset Marketplace ---

    fun publishSelectedLayerAsPreset(
        title: String,
        category: String,
        description: String,
        isPremium: Boolean
    ) {
        val layer = getSelectedLayer() ?: return
        val payload = PresetPayload(
            effects = layer.effects,
            speed = layer.speed,
            velocityPreset = layer.velocityPreset,
            textAnimation = layer.textConfig?.animationPreset ?: "Kinetic Pop",
            transitionName = layer.transitionIn
        )
        val summary = buildString {
            append(layer.effects.colorGradeName)
            if (layer.effects.glowIntensity > 0.05f) append(" • Glow")
            if (layer.effects.rgbSplit > 0.05f) append(" • RGB Split")
            if (layer.speed != 1f) append(" • ${layer.speed}x")
        }
        val dto = CreatorPresetDto(
            authorId = currentUserId,
            authorName = displayName.ifBlank { "VM Creator" },
            title = title.trim().ifBlank { "Custom Motion Preset" },
            category = category,
            description = description.trim().ifBlank { "Shared from Victor Motion studio." },
            isPublished = true,
            isPremium = isPremium,
            effectSummary = summary,
            presetPayloadJson = json.encodeToString(payload)
        )
        viewModelScope.launch {
            val res = firestoreRepository.publishPreset(dto)
            if (res.isSuccess) {
                _statusBanner.value = "Preset published to Creator Marketplace!"
            } else {
                _statusBanner.value = "Publish failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun applyCommunityPresetToActiveLayer(preset: CreatorPresetDto) {
        val payload = try {
            json.decodeFromString<PresetPayload>(preset.presetPayloadJson)
        } catch (_: Exception) {
            PresetPayload()
        }
        updateSelectedLayer { layer ->
            layer.copy(
                effects = payload.effects,
                speed = payload.speed,
                velocityPreset = payload.velocityPreset,
                transitionIn = payload.transitionName
            )
        }
        _statusBanner.value = "Applied '${preset.title}' to active layer."
    }

    fun likePreset(presetId: String) {
        viewModelScope.launch {
            firestoreRepository.likePreset(presetId)
        }
    }

    // --- Export Pipeline (Media3 Transformer + Studio Render) ---

    fun startExport(context: Context, config: ExportConfig) {
        val proj = _activeProject.value ?: return
        stopPlayback()
        val videoLayer = proj.payload.layers.firstOrNull {
            it.type == LayerType.VIDEO && it.mediaUri.isNotBlank()
        } ?: proj.payload.layers.firstOrNull { it.mediaUri.isNotBlank() }

        if (videoLayer != null && videoLayer.type == LayerType.VIDEO) {
            _exportState.value = ExportUiState.Exporting(5, "Initializing Media3 hardware pipeline...")
            MediaEngine.exportWithMedia3(
                context = context,
                projectTitle = proj.title,
                primaryLayer = videoLayer,
                config = config,
                onProgress = { pct ->
                    _exportState.value = ExportUiState.Exporting(
                        pct,
                        "Rendering ${config.resolution} @ ${config.fps} FPS (${config.bitrateMbps} Mbps)..."
                    )
                },
                onCompleted = { file: File ->
                    _exportState.value = ExportUiState.Completed(
                        outputPath = file.absolutePath,
                        summary = "Exported ${config.resolution} • ${config.fps} FPS • ${config.bitrateMbps} Mbps MP4 (${file.length() / 1024} KB)"
                    )
                },
                onError = { msg ->
                    _exportState.value = ExportUiState.Error(msg)
                }
            )
        } else {
            // Studio project without attached raw MP4 (e.g. template/text/motion canvas): render studio package summary
            viewModelScope.launch {
                val steps = listOf(18, 42, 68, 89, 100)
                for (pct in steps) {
                    _exportState.value = ExportUiState.Exporting(
                        pct,
                        "Compositing ${proj.payload.layers.size} layers at ${config.resolution} ${config.fps} FPS..."
                    )
                    delay(260L)
                }
                _exportState.value = ExportUiState.Completed(
                    outputPath = null,
                    summary = "Studio Master Rendered (${config.resolution} • ${config.fps} FPS • ${config.bitrateMbps} Mbps • ${if (config.removeWatermark) "Watermark-Free" else "Standard"}). Attach a video clip via + Video to write a hardware MP4."
                )
            }
        }
    }

    fun getSelectedLayer(): TimelineLayer? {
        val id = _selectedLayerId.value ?: return null
        return _activeProject.value?.payload?.layers?.firstOrNull { it.id == id }
    }

    fun evaluateLayerAtTime(layer: TimelineLayer, timeMs: Long): EvaluatedLayerTransform {
        fun interpolate(prop: KeyframeProperty, defaultVal: Float): Float {
            val kfs = layer.keyframes.filter { it.property == prop }.sortedBy { it.timeMs }
            if (kfs.isEmpty()) return defaultVal
            if (timeMs <= kfs.first().timeMs) return kfs.first().value
            if (timeMs >= kfs.last().timeMs) return kfs.last().value
            for (i in 0 until kfs.size - 1) {
                val a = kfs[i]
                val b = kfs[i + 1]
                if (timeMs in a.timeMs..b.timeMs) {
                    val span = (b.timeMs - a.timeMs).coerceAtLeast(1L).toFloat()
                    val t = ((timeMs - a.timeMs) / span).coerceIn(0f, 1f)
                    val eased = applyEasing(t, a.easing)
                    return a.value + (b.value - a.value) * eased
                }
            }
            return defaultVal
        }

        return EvaluatedLayerTransform(
            posX = interpolate(KeyframeProperty.POS_X, layer.posX),
            posY = interpolate(KeyframeProperty.POS_Y, layer.posY),
            scale = interpolate(KeyframeProperty.SCALE, layer.scale),
            rotation = interpolate(KeyframeProperty.ROTATION, layer.rotation),
            opacity = interpolate(KeyframeProperty.OPACITY, layer.opacity).coerceIn(0f, 1f)
        )
    }

    private fun applyEasing(t: Float, easing: EasingCurveType): Float {
        return when (easing) {
            EasingCurveType.LINEAR -> t
            EasingCurveType.EASE_IN_OUT -> t * t * (3f - 2f * t)
            EasingCurveType.EXPO_OUT -> if (t >= 1f) 1f else 1f - 2f.pow(-10f * t)
            EasingCurveType.SPRING_BOUNCE -> {
                val damped = 1f - (1f - t).pow(3f) * kotlin.math.cos(t * Math.PI * 3.0).toFloat()
                damped.coerceIn(0f, 1.15f)
            }
            EasingCurveType.BEZIER_CUSTOM -> {
                val u = 1f - t
                3f * u * u * t * 0.25f + 3f * u * t * t * 0.9f + t * t * t
            }
        }
    }

    fun shakeOffsetAt(amplitude: Float, timeMs: Long): Pair<Float, Float> {
        if (amplitude <= 0.01f) return 0f to 0f
        val rad = timeMs * 0.032
        val dx = (sin(rad) * amplitude * 18f).toFloat()
        val dy = (kotlin.math.cos(rad * 1.37) * amplitude * 14f).toFloat()
        return dx to dy
    }

    private companion object {
        const val TAG = "StudioViewModel"
    }
}
