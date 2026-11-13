package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.BlendModeType
import com.example.domain.model.EasingCurveType
import com.example.domain.model.KeyframeProperty
import com.example.domain.model.MaskShapeType
import com.example.domain.model.TimelineLayer
import com.example.ui.theme.AmberPlayhead
import com.example.ui.theme.CrimsonAccent
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoyalViolet
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.StudioViewModel

enum class EditorToolTab(val label: String, val icon: ImageVector) {
    TRANSFORM("Motion", Icons.Filled.Animation),
    EFFECTS("FX & Grade", Icons.Filled.AutoFixHigh),
    SPEED("Speed & Cut", Icons.Filled.Speed),
    AUDIO("Audio", Icons.Filled.GraphicEq),
    AI_STUDIO("AI Studio", Icons.Filled.AutoAwesome)
}

@Composable
fun EditorToolPanels(
    selectedTab: EditorToolTab,
    onSelectTab: (EditorToolTab) -> Unit,
    selectedLayer: TimelineLayer?,
    playheadMs: Long,
    aiState: AiTaskState,
    viewModel: StudioViewModel,
    onOpenPublishPreset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioSurface)
    ) {
        // Horizontal Tool Dock Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditorToolTab.entries.forEach { tab ->
                val active = tab == selectedTab
                FilterChip(
                    selected = active,
                    onClick = { onSelectTab(tab) },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                        selectedLabelColor = ElectricCyan,
                        selectedLeadingIconColor = ElectricCyan,
                        containerColor = StudioSurfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("tool_tab_${tab.name.lowercase()}")
                )
            }
        }

        // Scrollable Tool Inspector Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (selectedLayer == null && selectedTab != EditorToolTab.AI_STUDIO) {
                Text(
                    text = "Select a clip or layer on the timeline to edit keyframes, effects, or speed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                when (selectedTab) {
                    EditorToolTab.TRANSFORM -> selectedLayer?.let {
                        TransformAndKeyframePanel(it, playheadMs, viewModel)
                    }
                    EditorToolTab.EFFECTS -> selectedLayer?.let {
                        VisualEffectsAndGradePanel(it, viewModel, onOpenPublishPreset)
                    }
                    EditorToolTab.SPEED -> selectedLayer?.let {
                        SpeedAndTrimPanel(it, viewModel)
                    }
                    EditorToolTab.AUDIO -> selectedLayer?.let {
                        AudioAndBeatPanel(it, viewModel)
                    }
                    EditorToolTab.AI_STUDIO -> AiStudioPanel(aiState, viewModel)
                }
            }
        }
    }
}

@Composable
private fun TransformAndKeyframePanel(
    layer: TimelineLayer,
    playheadMs: Long,
    viewModel: StudioViewModel
) {
    var selectedProperty by remember { mutableStateOf(KeyframeProperty.SCALE) }
    var selectedEasing by remember { mutableStateOf(EasingCurveType.EASE_IN_OUT) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Text editing shortcut if TEXT layer
        if (layer.textConfig != null) {
            var textValue by remember(layer.id, layer.textConfig.text) {
                mutableStateOf(layer.textConfig.text)
            }
            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    viewModel.updateSelectedLayer { l ->
                        l.copy(textConfig = l.textConfig?.copy(text = it))
                    }
                },
                label = { Text("Text Content") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Kinetic Pop", "Velocity Glitch", "Fade Slide", "Neon Pulse").forEach { anim ->
                    FilterChip(
                        selected = layer.textConfig.animationPreset == anim,
                        onClick = {
                            viewModel.updateSelectedLayer { l ->
                                l.copy(textConfig = l.textConfig?.copy(animationPreset = anim))
                            }
                        },
                        label = { Text(anim) }
                    )
                }
            }
        }

        val currentVal = when (selectedProperty) {
            KeyframeProperty.POS_X -> layer.posX
            KeyframeProperty.POS_Y -> layer.posY
            KeyframeProperty.SCALE -> layer.scale
            KeyframeProperty.ROTATION -> layer.rotation
            KeyframeProperty.OPACITY -> layer.opacity
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Keyframe Property (${layer.keyframes.size} pts)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(
                onClick = {
                    viewModel.addOrUpdateKeyframe(selectedProperty, currentVal, selectedEasing)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberPlayhead,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("add_keyframe_button")
            ) {
                Icon(Icons.Filled.Diamond, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Keyframe @ ${playheadMs}ms", style = MaterialTheme.typography.labelMedium)
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyframeProperty.entries.forEach { prop ->
                FilterChip(
                    selected = prop == selectedProperty,
                    onClick = { selectedProperty = prop },
                    label = { Text(prop.label) }
                )
            }
        }

        val (minV, maxV) = when (selectedProperty) {
            KeyframeProperty.POS_X, KeyframeProperty.POS_Y -> -1f to 1f
            KeyframeProperty.SCALE -> 0.25f to 2.5f
            KeyframeProperty.ROTATION -> -180f to 180f
            KeyframeProperty.OPACITY -> 0f to 1f
        }

        StudioSliderRow(
            label = selectedProperty.label,
            value = currentVal,
            range = minV..maxV,
            valueLabel = String.format("%.2f", currentVal),
            onValueChange = { v ->
                viewModel.updateSelectedLayer { l ->
                    when (selectedProperty) {
                        KeyframeProperty.POS_X -> l.copy(posX = v)
                        KeyframeProperty.POS_Y -> l.copy(posY = v)
                        KeyframeProperty.SCALE -> l.copy(scale = v)
                        KeyframeProperty.ROTATION -> l.copy(rotation = v)
                        KeyframeProperty.OPACITY -> l.copy(opacity = v)
                    }
                }
            }
        )

        Text(
            text = "Graph Editor & Motion Curve",
            style = MaterialTheme.typography.titleSmall
        )
        BezierGraphEditorCanvas(easing = selectedEasing)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EasingCurveType.entries.forEach { curve ->
                FilterChip(
                    selected = curve == selectedEasing,
                    onClick = { selectedEasing = curve },
                    label = { Text(curve.displayName) }
                )
            }
        }
    }
}

@Composable
private fun VisualEffectsAndGradePanel(
    layer: TimelineLayer,
    viewModel: StudioViewModel,
    onOpenPublishPreset: () -> Unit
) {
    val fx = layer.effects
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cinematic LUT Presets", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = onOpenPublishPreset, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Filled.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Share Preset", style = MaterialTheme.typography.labelMedium)
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Natural", "Cyber Neon", "Teal & Orange", "Noir Film", "Golden Alpine").forEach { lut ->
                FilterChip(
                    selected = fx.colorGradeName == lut,
                    onClick = { viewModel.applyCinematicGradePreset(lut) },
                    label = { Text(lut) }
                )
            }
        }

        StudioSliderRow("Glow Bloom", fx.glowIntensity, 0f..1f, "${(fx.glowIntensity * 100).toInt()}%") { v ->
            viewModel.updateSelectedLayer { it.copy(effects = it.effects.copy(glowIntensity = v)) }
        }
        StudioSliderRow("Gaussian Blur", fx.blurRadius, 0f..1f, "${(fx.blurRadius * 100).toInt()}%") { v ->
            viewModel.updateSelectedLayer { it.copy(effects = it.effects.copy(blurRadius = v)) }
        }
        StudioSliderRow("RGB Split / Chromatic", fx.rgbSplit, 0f..1f, "${(fx.rgbSplit * 100).toInt()}%") { v ->
            viewModel.updateSelectedLayer { it.copy(effects = it.effects.copy(rgbSplit = v, chromaticAberration = v)) }
        }
        StudioSliderRow("Camera Shake", fx.shakeAmplitude, 0f..1f, "${(fx.shakeAmplitude * 100).toInt()}%") { v ->
            viewModel.updateSelectedLayer { it.copy(effects = it.effects.copy(shakeAmplitude = v)) }
        }
        StudioSliderRow("Contrast", fx.contrast, 0.6f..1.6f, String.format("%.2fx", fx.contrast)) { v ->
            viewModel.updateSelectedLayer { it.copy(effects = it.effects.copy(contrast = v)) }
        }
        StudioSliderRow("Saturation", fx.saturation, 0f..1.8f, String.format("%.2fx", fx.saturation)) { v ->
            viewModel.updateSelectedLayer { it.copy(effects = it.effects.copy(saturation = v)) }
        }
        StudioSliderRow("Color Temperature", fx.temperature, -0.5f..0.5f, String.format("%+.2f", fx.temperature)) { v ->
            viewModel.updateSelectedLayer { it.copy(effects = it.effects.copy(temperature = v)) }
        }
        StudioSliderRow("Vignette", fx.vignette, 0f..0.8f, "${(fx.vignette * 100).toInt()}%") { v ->
            viewModel.updateSelectedLayer { it.copy(effects = it.effects.copy(vignette = v)) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Motion Blur", style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = fx.motionBlur,
                onCheckedChange = { enabled ->
                    viewModel.updateSelectedLayer { it.copy(effects = it.effects.copy(motionBlur = enabled)) }
                }
            )
        }

        Text("Masking & Blending Mode", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MaskShapeType.entries.forEach { mask ->
                FilterChip(
                    selected = fx.maskShape == mask,
                    onClick = { viewModel.updateSelectedLayer { it.copy(effects = it.effects.copy(maskShape = mask)) } },
                    label = { Text(mask.displayName) }
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BlendModeType.entries.forEach { mode ->
                FilterChip(
                    selected = layer.blendMode == mode,
                    onClick = { viewModel.updateSelectedLayer { it.copy(blendMode = mode) } },
                    label = { Text(mode.displayName) }
                )
            }
        }
    }
}

@Composable
private fun SpeedAndTrimPanel(
    layer: TimelineLayer,
    viewModel: StudioViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.splitSelectedLayerAtPlayhead() },
                modifier = Modifier.weight(1f).testTag("split_clip_button")
            ) {
                Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Split")
            }
            OutlinedButton(
                onClick = { viewModel.insertFreezeFrameAtPlayhead() },
                modifier = Modifier.weight(1f).testTag("freeze_frame_button")
            ) {
                Icon(Icons.Filled.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Freeze")
            }
            OutlinedButton(
                onClick = { viewModel.duplicateSelectedLayer() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clone")
            }
        }

        StudioSliderRow(
            label = "Playback Speed",
            value = layer.speed.coerceIn(0.25f, 4f),
            range = 0.25f..4f,
            valueLabel = String.format("%.2fx", layer.speed)
        ) { s ->
            viewModel.updateSelectedLayer { it.copy(speed = s, velocityPreset = "Custom") }
        }

        Text("Velocity Ramp Presets", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "Uniform" to 1.0f,
                "Bullet Ramp" to 1.75f,
                "Flash Out" to 2.4f,
                "Cinema Slow" to 0.5f
            ).forEach { (preset, mult) ->
                FilterChip(
                    selected = layer.velocityPreset == preset,
                    onClick = {
                        viewModel.updateSelectedLayer { it.copy(velocityPreset = preset, speed = mult) }
                    },
                    label = { Text("$preset ($mult)") }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Reverse Clip Playback", style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = layer.isReversed,
                onCheckedChange = { rev ->
                    viewModel.updateSelectedLayer { it.copy(isReversed = rev) }
                }
            )
        }

        StudioSliderRow(
            label = "Crop Horizontal Inset",
            value = layer.cropLeft,
            range = 0f..0.4f,
            valueLabel = "${(layer.cropLeft * 100).toInt()}%"
        ) { c ->
            viewModel.updateSelectedLayer { it.copy(cropLeft = c, cropRight = 1f - c) }
        }

        Button(
            onClick = { viewModel.deleteSelectedLayer() },
            colors = ButtonDefaults.buttonColors(containerColor = CrimsonAccent.copy(alpha = 0.18f), contentColor = CrimsonAccent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Delete Selected Layer")
        }
    }
}

@Composable
private fun AudioAndBeatPanel(
    layer: TimelineLayer,
    viewModel: StudioViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Track Volume & Sync", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = { viewModel.updateSelectedLayer { it.copy(isMuted = !it.isMuted) } }
            ) {
                Icon(
                    imageVector = if (layer.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (layer.isMuted) "Unmute" else "Mute")
            }
        }

        StudioSliderRow("Volume", layer.volume, 0f..1.5f, "${(layer.volume * 100).toInt()}%") { v ->
            viewModel.updateSelectedLayer { it.copy(volume = v) }
        }
        StudioSliderRow("Fade In (ms)", layer.fadeInMs.toFloat(), 0f..3000f, "${layer.fadeInMs}ms") { v ->
            viewModel.updateSelectedLayer { it.copy(fadeInMs = v.toLong()) }
        }
        StudioSliderRow("Fade Out (ms)", layer.fadeOutMs.toFloat(), 0f..3000f, "${layer.fadeOutMs}ms") { v ->
            viewModel.updateSelectedLayer { it.copy(fadeOutMs = v.toLong()) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.detectLocalBeatsForAudio() },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                modifier = Modifier.weight(1f)
            ) {
                Text("Detect Waveform Beats")
            }
            OutlinedButton(
                onClick = { viewModel.addBeatMarkerAtPlayhead() },
                modifier = Modifier.weight(1f)
            ) {
                Text("+ Beat Marker")
            }
        }
    }
}

@Composable
private fun AiStudioPanel(
    aiState: AiTaskState,
    viewModel: StudioViewModel
) {
    var promptInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Victor Motion AI Studio (Gemini 3.8 Flash)",
            style = MaterialTheme.typography.titleSmall,
            color = ElectricCyan
        )
        OutlinedTextField(
            value = promptInput,
            onValueChange = { promptInput = it },
            label = { Text("Topic, mood, or target vibe (e.g. Tokyo night drift 130 BPM)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("ai_prompt_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.generateAiCaptions(promptInput) },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalViolet, contentColor = Color.White),
                modifier = Modifier.weight(1f).testTag("ai_captions_button")
            ) {
                Icon(Icons.Filled.ClosedCaption, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Auto Captions", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = { viewModel.requestAiSuggestions(promptInput) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                modifier = Modifier.weight(1f).testTag("ai_suggestions_button")
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit Ideas", style = MaterialTheme.typography.labelMedium)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.applyAiVisualEnhancement(promptInput.ifBlank { "Cinematic crisp contrast" }) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("AI Enhance", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { viewModel.runAiBeatSync(promptInput.ifBlank { "Energetic 128 BPM electronic" }) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Smart Beat Sync", style = MaterialTheme.typography.labelMedium)
            }
        }

        OutlinedButton(
            onClick = { viewModel.applyAiSubjectMatte() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("AI Background Cutout / Subject Matte")
        }

        when (aiState) {
            is AiTaskState.Idle -> Unit
            is AiTaskState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ElectricCyan, strokeWidth = 2.dp)
                    Text(aiState.label, style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                }
            }
            is AiTaskState.CaptionsReady -> {
                Surface(color = NeonEmerald.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Generated ${aiState.count} timed captions directly onto your timeline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonEmerald,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            is AiTaskState.BeatSyncReady -> {
                Surface(color = NeonEmerald.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Synced ${aiState.beatCount} rhythmic markers (${aiState.bpm} BPM) on the timeline ruler.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonEmerald,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            is AiTaskState.EnhancedReady -> {
                Surface(color = NeonEmerald.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Applied '${aiState.gradeName}' AI color & highlight balance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonEmerald,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            is AiTaskState.SuggestionsReady -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    aiState.items.forEach { item ->
                        Surface(
                            color = StudioElevated,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, StudioOutline, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${item.title} • ${item.category}", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                                    Text(item.rationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(item.recommendedEffect, style = MaterialTheme.typography.labelSmall, color = AmberPlayhead)
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { viewModel.applyAiSuggestion(item) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Apply")
                                }
                            }
                        }
                    }
                }
            }
            is AiTaskState.Error -> {
                Surface(
                    color = CrimsonAccent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, CrimsonAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = aiState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonAccent,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valueLabel, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ElectricCyan,
                activeTrackColor = ElectricCyan,
                inactiveTrackColor = StudioElevated
            ),
            modifier = Modifier.height(34.dp)
        )
    }
}
