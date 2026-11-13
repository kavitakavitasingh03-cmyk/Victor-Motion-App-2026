package com.example.data

import com.example.data.local.LocalProjectDao
import com.example.data.local.LocalProjectEntity
import com.example.data.remote.FirestoreProjectRepository
import com.example.domain.model.BlendModeType
import com.example.domain.model.CaptionSegment
import com.example.domain.model.EasingCurveType
import com.example.domain.model.KeyframePoint
import com.example.domain.model.KeyframeProperty
import com.example.domain.model.LayerType
import com.example.domain.model.MaskShapeType
import com.example.domain.model.ProjectTimelinePayload
import com.example.domain.model.StudioProject
import com.example.domain.model.TextStyleConfig
import com.example.domain.model.TimelineLayer
import com.example.domain.model.VideoProjectDto
import com.example.domain.model.VisualEffectsConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class StudioProjectRepository(
    private val localDao: LocalProjectDao,
    private val remoteRepo: FirestoreProjectRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun observeLocalProjects(userId: String): Flow<List<StudioProject>> =
        localDao.observeProjectsForUser(userId).map { entities ->
            entities.map { entity -> entity.toDomain() }
        }

    suspend fun getProject(projectId: String): StudioProject? =
        localDao.getProjectById(projectId)?.toDomain()

    suspend fun autosaveLocal(project: StudioProject) {
        localDao.upsertProject(
            LocalProjectEntity(
                id = project.id,
                userId = project.userId,
                title = project.title,
                aspectRatio = project.aspectRatio,
                resolution = project.resolution,
                fps = project.fps,
                durationMs = project.durationMs,
                layersJson = json.encodeToString(project.payload),
                templateOriginId = project.templateOriginId,
                isSyncedToCloud = project.isSyncedToCloud,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun backupProjectToCloud(project: StudioProject): Result<Unit> {
        val layersJson = json.encodeToString(project.payload)
        val dto = VideoProjectDto(
            id = project.id,
            userId = project.userId,
            title = project.title,
            aspectRatio = project.aspectRatio,
            resolution = project.resolution,
            fps = project.fps.toLong(),
            durationMs = project.durationMs,
            layerCount = project.payload.layers.size.toLong(),
            layersJson = layersJson,
            templateOriginId = project.templateOriginId
        )
        val result = remoteRepo.backupProject(dto)
        if (result.isSuccess) {
            localDao.markSynced(project.id, true)
        }
        return result
    }

    suspend fun syncFromCloudIfNeeded(userId: String, cloudProjects: List<VideoProjectDto>) {
        for (dto in cloudProjects) {
            val local = localDao.getProjectById(dto.id)
            val remoteMillis = dto.updatedAt?.toDate()?.time ?: 0L
            if (local == null || remoteMillis > local.updatedAtMillis) {
                localDao.upsertProject(
                    LocalProjectEntity(
                        id = dto.id,
                        userId = userId,
                        title = dto.title,
                        aspectRatio = dto.aspectRatio,
                        resolution = dto.resolution,
                        fps = dto.fps.toInt().let { if (it in listOf(30, 60, 120)) it else 60 },
                        durationMs = dto.durationMs,
                        layersJson = dto.layersJson,
                        templateOriginId = dto.templateOriginId,
                        isSyncedToCloud = true,
                        updatedAtMillis = remoteMillis.coerceAtLeast(System.currentTimeMillis())
                    )
                )
            }
        }
    }

    suspend fun deleteProject(projectId: String, alsoDeleteCloud: Boolean) {
        localDao.deleteProjectById(projectId)
        if (alsoDeleteCloud) {
            remoteRepo.deleteCloudProject(projectId)
        }
    }

    suspend fun createStarterProject(
        userId: String,
        title: String,
        aspectRatio: String,
        resolution: String,
        fps: Int,
        templateId: String? = null
    ): StudioProject {
        val id = "vm_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val payload = buildStarterPayload(title, templateId)
        val duration = payload.layers.maxOfOrNull { it.endMs } ?: 10000L
        val project = StudioProject(
            id = id,
            userId = userId,
            title = title,
            aspectRatio = aspectRatio,
            resolution = resolution,
            fps = fps,
            durationMs = duration.coerceAtLeast(8000L),
            payload = payload,
            templateOriginId = templateId,
            isSyncedToCloud = false,
            updatedAtMillis = System.currentTimeMillis()
        )
        autosaveLocal(project)
        return project
    }

    private fun buildStarterPayload(title: String, templateId: String?): ProjectTimelinePayload {
        val baseEffects = when (templateId) {
            "tpl_cyber" -> VisualEffectsConfig(
                glowIntensity = 0.48f,
                rgbSplit = 0.35f,
                chromaticAberration = 0.25f,
                contrast = 1.25f,
                saturation = 1.3f,
                colorGradeName = "Cyber Neon",
                motionBlur = true
            )
            "tpl_cinema" -> VisualEffectsConfig(
                exposure = 0.05f,
                contrast = 1.18f,
                saturation = 1.12f,
                temperature = 0.22f,
                vignette = 0.32f,
                colorGradeName = "Golden Alpine",
                maskShape = MaskShapeType.CINEMA_BARS
            )
            "tpl_velocity" -> VisualEffectsConfig(
                glowIntensity = 0.35f,
                shakeAmplitude = 0.22f,
                motionBlur = true,
                contrast = 1.2f,
                colorGradeName = "Velocity Punch"
            )
            else -> VisualEffectsConfig()
        }

        val speed = if (templateId == "tpl_velocity") 1.6f else 1.0f
        val velocityPreset = if (templateId == "tpl_velocity") "Bullet Ramp" else "Uniform"

        val mainLayer = TimelineLayer(
            id = "layer_main_${UUID.randomUUID().toString().take(6)}",
            name = "Main Canvas Track",
            type = LayerType.VIDEO,
            trackIndex = 0,
            startMs = 0L,
            endMs = 9000L,
            speed = speed,
            velocityPreset = velocityPreset,
            effects = baseEffects,
            keyframes = listOf(
                KeyframePoint(
                    id = "kf_1",
                    timeMs = 0L,
                    property = KeyframeProperty.SCALE,
                    value = 1.0f,
                    easing = EasingCurveType.EXPO_OUT
                ),
                KeyframePoint(
                    id = "kf_2",
                    timeMs = 3500L,
                    property = KeyframeProperty.SCALE,
                    value = 1.14f,
                    easing = EasingCurveType.EASE_IN_OUT
                ),
                KeyframePoint(
                    id = "kf_3",
                    timeMs = 7000L,
                    property = KeyframeProperty.SCALE,
                    value = 1.0f,
                    easing = EasingCurveType.SPRING_BOUNCE
                )
            )
        )

        val titleOverlay = TimelineLayer(
            id = "layer_text_${UUID.randomUUID().toString().take(6)}",
            name = "Kinetic Title",
            type = LayerType.TEXT,
            trackIndex = 1,
            startMs = 400L,
            endMs = 5200L,
            blendMode = BlendModeType.SCREEN,
            posY = -0.18f,
            textConfig = TextStyleConfig(
                text = title.uppercase(),
                colorHex = 0xFFF3F6FF,
                outlineColorHex = 0xFF00E5FF,
                outlineWidth = 2f,
                fontSizeSp = 26f,
                animationPreset = if (templateId == "tpl_velocity") "Velocity Glitch" else "Kinetic Pop"
            )
        )

        val audioBed = TimelineLayer(
            id = "layer_audio_${UUID.randomUUID().toString().take(6)}",
            name = "Studio Synth Pulse",
            type = LayerType.AUDIO,
            trackIndex = 2,
            startMs = 0L,
            endMs = 9000L,
            volume = 0.85f,
            fadeInMs = 400L,
            fadeOutMs = 600L,
            waveformSamples = listOf(
                0.22f, 0.48f, 0.76f, 0.35f, 0.88f, 0.62f, 0.94f, 0.41f,
                0.70f, 0.85f, 0.50f, 0.92f, 0.66f, 0.45f, 0.81f, 0.57f,
                0.95f, 0.72f, 0.38f, 0.64f, 0.89f, 0.51f, 0.42f, 0.25f
            )
        )

        return ProjectTimelinePayload(
            layers = listOf(mainLayer, titleOverlay, audioBed),
            beatMarkersMs = listOf(1200L, 2400L, 3600L, 4800L, 6000L, 7200L, 8400L),
            captions = listOf(
                CaptionSegment(
                    id = "cap_init_1",
                    startMs = 800L,
                    endMs = 3400L,
                    text = "VICTOR MOTION STUDIO"
                )
            )
        )
    }

    private fun LocalProjectEntity.toDomain(): StudioProject {
        val decoded = try {
            json.decodeFromString<ProjectTimelinePayload>(layersJson)
        } catch (_: Exception) {
            ProjectTimelinePayload()
        }
        return StudioProject(
            id = id,
            userId = userId,
            title = title,
            aspectRatio = aspectRatio,
            resolution = resolution,
            fps = fps,
            durationMs = durationMs,
            payload = decoded,
            templateOriginId = templateOriginId,
            isSyncedToCloud = isSyncedToCloud,
            updatedAtMillis = updatedAtMillis
        )
    }
}
