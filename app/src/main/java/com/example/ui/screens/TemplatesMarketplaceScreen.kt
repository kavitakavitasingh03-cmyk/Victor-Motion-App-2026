package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.CreatorPresetDto
import com.example.ui.theme.AmberPlayhead
import com.example.ui.theme.CrimsonAccent
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoyalViolet
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TimecodeTextStyle
import com.example.ui.viewmodel.BuiltInTemplate
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun TemplatesMarketplaceScreen(
    templates: List<BuiltInTemplate>,
    presetsState: UiState<List<CreatorPresetDto>>,
    hasActiveProject: Boolean,
    viewModel: StudioViewModel,
    onOpenEditor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Cinematic", "Velocity", "MotionFX", "Transitions", "TextAnim", "ColorGrade")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Templates & Creator Marketplace",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Ready-made cinematic grades, velocity ramps, transitions & community presets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            item {
                Text("Featured Studio Templates", style = MaterialTheme.typography.titleMedium)
            }

            val filteredTemplates = templates.filter {
                selectedCategory == "All" || it.category == selectedCategory
            }

            items(filteredTemplates, key = { it.id }) { tpl ->
                FeaturedTemplateCard(
                    template = tpl,
                    onUseTemplate = {
                        viewModel.createNewProject(
                            title = tpl.title,
                            aspectRatio = tpl.aspectRatio,
                            resolution = "1080p",
                            fps = 60,
                            templateId = tpl.id,
                            onCreated = onOpenEditor
                        )
                    }
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Community Shared Presets", style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = RoyalViolet, modifier = Modifier.size(18.dp))
                }
            }

            when (presetsState) {
                is UiState.Loading -> {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ElectricCyan)
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Surface(
                            color = StudioSurface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, StudioOutline, RoundedCornerShape(14.dp))
                        ) {
                            Text(
                                text = presetsState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = CrimsonAccent,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    val filteredPresets = presetsState.data.filter {
                        selectedCategory == "All" || it.category == selectedCategory
                    }
                    if (filteredPresets.isEmpty()) {
                        item {
                            Surface(
                                color = StudioSurface,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().border(1.dp, StudioOutline, RoundedCornerShape(16.dp))
                            ) {
                                Text(
                                    text = "No community presets published in '$selectedCategory' yet. Share your custom FX stack from inside the Editor!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(filteredPresets, key = { it.id }) { preset ->
                            CommunityPresetCard(
                                preset = preset,
                                hasActiveProject = hasActiveProject,
                                onApply = { viewModel.applyCommunityPresetToActiveLayer(preset) },
                                onLike = { viewModel.likePreset(preset.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedTemplateCard(
    template: BuiltInTemplate,
    onUseTemplate: () -> Unit
) {
    val drawableId = when (template.id) {
        "tpl_cyber" -> R.drawable.img_template_cyber
        "tpl_cinema" -> R.drawable.img_template_cinema
        else -> R.drawable.img_template_velocity
    }

    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StudioOutline, RoundedCornerShape(18.dp))
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(145.dp)) {
                Image(
                    painter = painterResource(id = drawableId),
                    contentDescription = template.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, ObsidianBg.copy(alpha = 0.85f)))
                        )
                )
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (template.isPremium) {
                        Surface(color = AmberPlayhead, shape = RoundedCornerShape(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("PRO", style = TimecodeTextStyle, color = ObsidianBg, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Surface(color = ObsidianBg.copy(alpha = 0.78f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = template.aspectRatio,
                            style = TimecodeTextStyle,
                            color = ElectricCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(template.effectsSummary, style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                    Spacer(Modifier.height(2.dp))
                    Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onUseTemplate,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("use_template_${template.id}")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Use")
                }
            }
        }
    }
}

@Composable
private fun CommunityPresetCard(
    preset: CreatorPresetDto,
    hasActiveProject: Boolean,
    onApply: () -> Unit,
    onLike: () -> Unit
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, StudioOutline, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(preset.title, style = MaterialTheme.typography.titleSmall)
                    if (preset.isPremium) {
                        Text("PRO", style = TimecodeTextStyle, color = AmberPlayhead)
                    }
                }
                Text(
                    text = "${preset.category} • by ${preset.authorName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = RoyalViolet
                )
                Text(
                    text = preset.effectSummary.ifBlank { preset.description },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onLike) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Like preset", tint = CrimsonAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("${preset.likesCount}", style = TimecodeTextStyle)
                }
            }

            if (hasActiveProject) {
                OutlinedButton(onClick = onApply, shape = RoundedCornerShape(10.dp)) {
                    Text("Apply")
                }
            }
        }
    }
}
