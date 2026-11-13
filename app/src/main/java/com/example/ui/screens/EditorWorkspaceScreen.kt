package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.LayerType
import com.example.ui.components.EditorToolPanels
import com.example.ui.components.EditorToolTab
import com.example.ui.components.MultiLayerTimelineView
import com.example.ui.components.StudioPreviewCanvas
import com.example.ui.theme.AmberPlayhead
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TimecodeTextStyle
import com.example.ui.viewmodel.StudioViewModel

@Composable
fun EditorWorkspaceScreen(
    viewModel: StudioViewModel,
    onNavigateBack: () -> Unit,
    onNavigateExport: () -> Unit
) {
    val context = LocalContext.current
    val project by viewModel.activeProject.collectAsStateWithLifecycle()
    val selectedLayerId by viewModel.selectedLayerId.collectAsStateWithLifecycle()
    val playheadMs by viewModel.playheadMs.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(EditorToolTab.TRANSFORM) }
    var timelineZoom by remember { mutableFloatStateOf(1.0f) }
    var showAddLayerDialog by remember { mutableStateOf(false) }
    var showPublishPresetDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    BackHandler {
        viewModel.stopPlayback()
        onNavigateBack()
    }

    // Zero-permission Android Photo Picker & SAF Pickers (Google Play compliant)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.addMediaLayer(context, uri, LayerType.VIDEO)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.addMediaLayer(context, uri, LayerType.IMAGE)
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.addMediaLayer(context, uri, LayerType.AUDIO)
    }

    val activeProj = project
    if (activeProj == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(ObsidianBg),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading Studio Workspace...", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val selectedLayer = activeProj.payload.layers.firstOrNull { it.id == selectedLayerId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Top Studio Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                viewModel.stopPlayback()
                onNavigateBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Hub")
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showRenameDialog = true }
            ) {
                Text(
                    text = activeProj.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (activeProj.isSyncedToCloud) "Autosaved • Cloud Synced" else "Autosaved Locally",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (activeProj.isSyncedToCloud) NeonEmerald else AmberPlayhead
                )
            }

            IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            }
            IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            }
            IconButton(onClick = { viewModel.backupActiveProjectToCloud() }) {
                Icon(Icons.Filled.CloudUpload, contentDescription = "Backup to Cloud", tint = ElectricCyan)
            }

            Button(
                onClick = {
                    viewModel.stopPlayback()
                    onNavigateExport()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("open_export_button")
            ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Export", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }

        // 2. Live Preview Canvas
        StudioPreviewCanvas(
            project = activeProj,
            playheadMs = playheadMs,
            isPlaying = isPlaying,
            selectedLayerId = selectedLayerId,
            evaluateTransform = viewModel::evaluateLayerAtTime,
            shakeOffsetProvider = viewModel::shakeOffsetAt,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.36f)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        // 3. Transport & Scrubber Bar
        Surface(
            color = StudioSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Slider(
                    value = playheadMs.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..activeProj.durationMs.coerceAtLeast(1000L).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = AmberPlayhead,
                        activeTrackColor = AmberPlayhead,
                        inactiveTrackColor = StudioElevated
                    ),
                    modifier = Modifier.height(26.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatTimecode(playheadMs)} / ${formatTimecode(activeProj.durationMs)}",
                        style = TimecodeTextStyle,
                        color = ElectricCyan
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.stepFrame(false) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Prev frame")
                        }
                        IconButton(
                            onClick = { viewModel.togglePlayback() },
                            modifier = Modifier.size(40.dp).testTag("play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = ElectricCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.stepFrame(true) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next frame")
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { timelineZoom = (timelineZoom - 0.25f).coerceAtLeast(0.5f) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out timeline", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { timelineZoom = (timelineZoom + 0.25f).coerceAtMost(2.5f) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in timeline", modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = { showAddLayerDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StudioElevated, contentColor = ElectricCyan),
                            modifier = Modifier.height(34.dp).testTag("add_layer_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Layer", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // 4. Multi-Layer Timeline
        MultiLayerTimelineView(
            project = activeProj,
            playheadMs = playheadMs,
            selectedLayerId = selectedLayerId,
            zoomScale = timelineZoom,
            onSelectLayer = { viewModel.selectLayer(it) },
            onSeek = { viewModel.seekTo(it) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.28f)
        )

        // 5. Context Tool Panels (Motion/Keyframes, FX/Grade, Speed/Trim, Audio, AI Studio)
        EditorToolPanels(
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
            selectedLayer = selectedLayer,
            playheadMs = playheadMs,
            aiState = aiState,
            viewModel = viewModel,
            onOpenPublishPreset = { showPublishPresetDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.36f)
        )
    }

    if (showAddLayerDialog) {
        AlertDialog(
            onDismissRequest = { showAddLayerDialog = false },
            containerColor = StudioSurface,
            title = { Text("Add Timeline Layer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddLayerOptionRow(Icons.Filled.Videocam, "Video Clip (Photo Picker)", "Import video without storage permissions") {
                        showAddLayerDialog = false
                        videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                    AddLayerOptionRow(Icons.Filled.Image, "Image / Overlay (Photo Picker)", "PNG/JPG overlay with keyframe transforms") {
                        showAddLayerDialog = false
                        imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    AddLayerOptionRow(Icons.Filled.AudioFile, "Audio / Music Track", "Add multi-track audio & beat waveforms") {
                        showAddLayerDialog = false
                        audioPickerLauncher.launch(arrayOf("audio/*"))
                    }
                    AddLayerOptionRow(Icons.Filled.TextFields, "Kinetic Text Layer", "Animated typography with RGB split") {
                        showAddLayerDialog = false
                        viewModel.addTextOrEffectLayer(LayerType.TEXT, "NEW TITLE")
                    }
                    AddLayerOptionRow(Icons.Filled.AutoFixHigh, "FX Adjustment Layer", "Apply glow, blur & shake across tracks") {
                        showAddLayerDialog = false
                        viewModel.addTextOrEffectLayer(LayerType.EFFECT)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddLayerDialog = false }) { Text("Close") }
            }
        )
    }

    if (showPublishPresetDialog) {
        PublishPresetDialog(
            onDismiss = { showPublishPresetDialog = false },
            onPublish = { title, cat, desc, premium ->
                showPublishPresetDialog = false
                viewModel.publishSelectedLayerAsPreset(title, cat, desc, premium)
            }
        )
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(activeProj.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = StudioSurface,
            title = { Text("Rename Project") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateProjectTitle(newName)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddLayerOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = StudioElevated,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StudioOutline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PublishPresetDialog(
    onDismiss: () -> Unit,
    onPublish: (String, String, String, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("My Motion Preset") }
    var category by remember { mutableStateOf("MotionFX") }
    var description by remember { mutableStateOf("Custom glow, curve & color grade") }
    var isPremium by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        title = { Text("Publish to Marketplace") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Preset Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Cinematic", "Velocity", "MotionFX", "ColorGrade").forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) }
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mark as Pro Marketplace Preset", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = isPremium, onCheckedChange = { isPremium = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPublish(title, category, description, isPremium) }) {
                Text("Publish")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatTimecode(ms: Long): String {
    val totalSec = ms / 1000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val tenths = (ms % 1000L) / 100L
    return "%02d:%02d.%01d".format(mins, secs, tenths)
}
