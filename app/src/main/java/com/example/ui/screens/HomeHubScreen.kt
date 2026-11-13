package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.domain.model.StudioProject
import com.example.domain.model.UserProfileDto
import com.example.ui.auth.signOut
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
import com.example.ui.theme.TimecodeTextStyle
import com.example.ui.viewmodel.StudioViewModel

@Composable
fun HomeScreen(
    projects: List<StudioProject>,
    userProfile: UserProfileDto?,
    displayName: String,
    viewModel: StudioViewModel,
    onOpenEditor: (String) -> Unit,
    onNavigateTemplates: () -> Unit,
    modifier: Modifier = Modifier
) {
    HomeHubScreen(
        projects = projects,
        userProfile = userProfile,
        displayName = displayName,
        viewModel = viewModel,
        onOpenEditor = onOpenEditor,
        onNavigateTemplates = onNavigateTemplates,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeHubScreen(
    projects: List<StudioProject>,
    userProfile: UserProfileDto?,
    displayName: String,
    viewModel: StudioViewModel,
    onOpenEditor: (String) -> Unit,
    onNavigateTemplates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }

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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(ElectricCyan, RoyalViolet))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "VM",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = ObsidianBg
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "VICTOR MOTION",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val tier = userProfile?.subscriptionTier?.uppercase() ?: "FREE"
                            Text(
                                text = "$tier STUDIO • NO ADS",
                                style = TimecodeTextStyle,
                                color = if (tier == "FREE") ElectricCyan else AmberPlayhead
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = StudioSurfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier
                                .border(1.dp, StudioOutline, CircleShape)
                                .clickable { showAccountDialog = true }
                                .testTag("account_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = "Account",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = displayName.substringBefore(" ").take(12),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // Hero New Project Banner
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = StudioSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioOutline, RoundedCornerShape(22.dp))
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_studio_hero),
                            contentDescription = "Victor Motion Studio Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(205.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(205.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            ObsidianBg.copy(alpha = 0.94f),
                                            ObsidianBg.copy(alpha = 0.65f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Create Your Next Cut",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                            Text(
                                text = "Multi-layer timeline, Bezier keyframe curves, AI auto-captions & 4K export.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(0.85f)
                            )
                            Spacer(Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showNewProjectDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = ObsidianBg
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("new_project_button")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Create New Project", fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = onNavigateTemplates,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Templates", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Studio Projects (${projects.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Autosaved Offline + Cloud",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonEmerald
                    )
                }
            }

            if (projects.isEmpty()) {
                item {
                    Surface(
                        color = StudioSurface,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, StudioOutline, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MovieCreation,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                text = "No projects yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap Create New Project or launch a ready-made Cinematic or Velocity template.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(projects, key = { it.id }) { proj ->
                    ProjectCard(
                        project = proj,
                        onOpen = {
                            viewModel.openProjectById(proj.id)
                            onOpenEditor(proj.id)
                        },
                        onDelete = { viewModel.deleteProject(proj.id) }
                    )
                }
            }
        }
    }

    if (showNewProjectDialog) {
        NewProjectDialog(
            defaultRes = userProfile?.preferredExportQuality ?: "1080p",
            defaultFps = userProfile?.preferredFps?.toInt() ?: 60,
            onDismiss = { showNewProjectDialog = false },
            onCreate = { title, aspect, res, fps ->
                showNewProjectDialog = false
                viewModel.createNewProject(title, aspect, res, fps) { id ->
                    onOpenEditor(id)
                }
            }
        )
    }

    if (showAccountDialog) {
        AccountAndPlanDialog(
            displayName = displayName,
            userProfile = userProfile,
            onDismiss = { showAccountDialog = false },
            onToggleBackup = { viewModel.toggleCloudBackupSetting(it) },
            onSelectTier = { viewModel.upgradeSubscriptionTier(it) },
            onSignOut = {
                showAccountDialog = false
                signOut(credentialManager, scope)
            }
        )
    }
}

@Composable
private fun ProjectCard(
    project: StudioProject,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StudioOutline, RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .testTag("project_card_${project.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(listOf(StudioElevated, RoyalViolet.copy(alpha = 0.45f)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = project.aspectRatio,
                    style = TimecodeTextStyle,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${project.resolution} • ${project.fps}FPS • ${project.durationMs / 1000}s",
                        style = TimecodeTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Layers,
                            contentDescription = null,
                            tint = RoyalViolet,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${project.payload.layers.size} layers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (project.isSyncedToCloud) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = if (project.isSyncedToCloud) NeonEmerald else AmberPlayhead,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (project.isSyncedToCloud) "Synced" else "Offline Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (project.isSyncedToCloud) NeonEmerald else AmberPlayhead
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete Project",
                    tint = CrimsonAccent
                )
            }
        }
    }
}

@Composable
private fun NewProjectDialog(
    defaultRes: String,
    defaultFps: Int,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Int) -> Unit
) {
    var title by remember { mutableStateOf("Untitled Cut") }
    var aspectRatio by remember { mutableStateOf("9:16") }
    var resolution by remember { mutableStateOf(defaultRes) }
    var fps by remember { mutableIntStateOf(defaultFps) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        title = { Text("New Studio Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_project_title_input")
                )
                Text("Aspect Ratio", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("9:16", "16:9", "1:1", "4:5").forEach { ar ->
                        FilterChip(
                            selected = aspectRatio == ar,
                            onClick = { aspectRatio = ar },
                            label = { Text(ar) }
                        )
                    }
                }
                Text("Canvas Resolution", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("720p", "1080p", "2K", "4K").forEach { r ->
                        FilterChip(
                            selected = resolution == r,
                            onClick = { resolution = r },
                            label = { Text(r) }
                        )
                    }
                }
                Text("Frame Rate (FPS)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 60, 120).forEach { f ->
                        FilterChip(
                            selected = fps == f,
                            onClick = { fps = f },
                            label = { Text("${f}FPS") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, aspectRatio, resolution, fps) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                modifier = Modifier.testTag("confirm_create_project_button")
            ) {
                Text("Create & Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AccountAndPlanDialog(
    displayName: String,
    userProfile: UserProfileDto?,
    onDismiss: () -> Unit,
    onToggleBackup: (Boolean) -> Unit,
    onSelectTier: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val activeTier = userProfile?.subscriptionTier ?: "free"
    val backupEnabled = userProfile?.cloudBackupEnabled ?: true

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = AmberPlayhead)
                Spacer(Modifier.width(8.dp))
                Text("Creator Studio Account")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Signed in as $displayName", style = MaterialTheme.typography.titleSmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Cloud Backup", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Sync timeline edits to Firestore automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = backupEnabled, onCheckedChange = onToggleBackup)
                }

                Text("Studio Membership (Zero Forced Ads)", style = MaterialTheme.typography.labelMedium)
                listOf(
                    Triple("free", "Free Creator", "Core multi-layer editor & watermark-free 1080p"),
                    Triple("pro", "VM Pro", "All Velocity packs, AI Studio & 4K 120FPS export"),
                    Triple("studio", "VM Studio", "Creator Marketplace publishing & team cloud backup")
                ).forEach { (id, title, desc) ->
                    Surface(
                        color = if (activeTier == id) ElectricCyan.copy(alpha = 0.14f) else StudioElevated,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (activeTier == id) ElectricCyan else StudioOutline,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectTier(id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleSmall)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (activeTier == id) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ElectricCyan)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onSignOut) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = CrimsonAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sign Out", color = CrimsonAccent)
            }
        }
    )
}
