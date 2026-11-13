package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ExportConfig
import com.example.ui.theme.AmberPlayhead
import com.example.ui.theme.CrimsonAccent
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TimecodeTextStyle
import com.example.ui.viewmodel.ExportUiState
import com.example.ui.viewmodel.StudioViewModel

@Composable
fun ExportScreen(
    viewModel: StudioViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val project by viewModel.activeProject.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val hw = viewModel.hardwareCapabilities

    var resolution by remember(project) { mutableStateOf(project?.resolution ?: "1080p") }
    var fps by remember(project) { mutableIntStateOf(project?.fps ?: 60) }
    var bitrateMbps by remember { mutableIntStateOf(24) }
    var watermarkFree by remember { mutableStateOf(true) }

    BackHandler {
        viewModel.resetExportState()
        onNavigateBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    viewModel.resetExportState()
                    onNavigateBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(6.dp))
                Column {
                    Text("Export Master", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = project?.title ?: "Studio Project",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Hardware Codec Probe Card
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, StudioOutline, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Memory, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Hardware Pipeline: ${hw.supportedEncoderName}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "4K Support: ${if (hw.supports4K) "Hardware Accelerated" else "Fallback"} • Max ${hw.maxSupportedFps} FPS",
                            style = TimecodeTextStyle,
                            color = NeonEmerald
                        )
                    }
                }
            }

            // Resolution Selector
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, StudioOutline, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.HighQuality, contentDescription = null, tint = ElectricCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Resolution & Frame Rate", style = MaterialTheme.typography.titleMedium)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("720p", "1080p", "2K", "4K").forEach { res ->
                            val supported = when (res) {
                                "4K" -> hw.supports4K
                                "2K" -> hw.supports2K
                                else -> true
                            }
                            FilterChip(
                                selected = resolution == res,
                                onClick = { resolution = res },
                                label = {
                                    Text(if (res == "4K" && !supported) "4K (SW)" else res)
                                }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 120).forEach { targetFps ->
                            FilterChip(
                                selected = fps == targetFps,
                                onClick = { fps = targetFps },
                                label = {
                                    Text(
                                        if (targetFps == 120 && hw.maxSupportedFps < 120) "120 FPS*" else "${targetFps} FPS"
                                    )
                                }
                            )
                        }
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Target Video Bitrate", style = MaterialTheme.typography.labelMedium)
                            Text("$bitrateMbps Mbps", style = TimecodeTextStyle, color = ElectricCyan)
                        }
                        Slider(
                            value = bitrateMbps.toFloat(),
                            onValueChange = { bitrateMbps = it.toInt() },
                            valueRange = 6f..80f,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan,
                                inactiveTrackColor = StudioElevated
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Watermark-Free Export", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Included free for basic 720p & 1080p studio exports",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = watermarkFree, onCheckedChange = { watermarkFree = it })
                    }
                }
            }

            // Export Progress / Action
            when (val state = exportState) {
                is ExportUiState.Idle -> {
                    Button(
                        onClick = {
                            viewModel.startExport(
                                context,
                                ExportConfig(resolution, fps, bitrateMbps, watermarkFree)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("start_export_button")
                    ) {
                        Text("Render & Export Video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                is ExportUiState.Exporting -> {
                    Surface(
                        color = StudioSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                                Text("${state.progressPercent}%", style = TimecodeTextStyle, color = ElectricCyan)
                            }
                            LinearProgressIndicator(
                                progress = { state.progressPercent / 100f },
                                color = ElectricCyan,
                                trackColor = StudioElevated,
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                            )
                        }
                    }
                }

                is ExportUiState.Completed -> {
                    Surface(
                        color = NeonEmerald.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = NeonEmerald)
                                Spacer(Modifier.width(8.dp))
                                Text("Export Complete", style = MaterialTheme.typography.titleMedium, color = NeonEmerald)
                            }
                            Text(state.summary, style = MaterialTheme.typography.bodySmall)
                            if (state.outputPath != null) {
                                Text(
                                    text = "Saved: ${state.outputPath}",
                                    style = TimecodeTextStyle,
                                    color = AmberPlayhead
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "Rendered in Victor Motion: ${state.summary}")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Export Info"))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share")
                                }
                                Button(
                                    onClick = { viewModel.resetExportState() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Export Again")
                                }
                            }
                        }
                    }
                }

                is ExportUiState.Error -> {
                    Surface(
                        color = CrimsonAccent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, CrimsonAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.message, style = MaterialTheme.typography.bodySmall, color = CrimsonAccent)
                            OutlinedButton(onClick = { viewModel.resetExportState() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}
