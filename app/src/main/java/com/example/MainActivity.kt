package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.StudioProjectRepository
import com.example.data.local.VictorMotionDatabase
import com.example.data.remote.AiStudioRepository
import com.example.data.remote.FirestoreProjectRepository
import com.example.security.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.EditorWorkspaceScreen
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.TemplatesMarketplaceScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VictorMotionTheme
import com.example.ui.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object TemplatesRoute

@Serializable
data object EditorRoute

@Serializable
data object ExportRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            VictorMotionTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = auth.currentUser })
    } else {
        key(user.uid) {
            VictorMotionStudioApp(
                currentUserId = user.uid,
                displayName = user.displayName ?: "Creator",
                photoUrl = user.photoUrl?.toString()
            )
        }
    }
}

@Composable
private fun VictorMotionStudioApp(
    currentUserId: String,
    displayName: String,
    photoUrl: String?
) {
    val studioViewModel: StudioViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val firestoreRepo = FirestoreProjectRepository(db)
                val localDb = VictorMotionDatabase.getInstance(app)
                val projectRepo = StudioProjectRepository(localDb.localProjectDao(), firestoreRepo)
                val aiRepo = AiStudioRepository()
                StudioViewModel(
                    projectRepository = projectRepo,
                    firestoreRepository = firestoreRepo,
                    aiRepository = aiRepo,
                    currentUserId = currentUserId,
                    displayName = displayName,
                    photoUrl = photoUrl
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showTopLevelNav = currentRoute == HomeRoute::class.qualifiedName ||
        currentRoute == TemplatesRoute::class.qualifiedName

    val projects by studioViewModel.localProjects.collectAsStateWithLifecycle()
    val userProfile by studioViewModel.userProfile.collectAsStateWithLifecycle()
    val presetsState by studioViewModel.publishedPresets.collectAsStateWithLifecycle()
    val activeProject by studioViewModel.activeProject.collectAsStateWithLifecycle()
    val statusBanner by studioViewModel.statusBanner.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            studioViewModel.clearStatusBanner()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val useNavRail = maxWidth >= 680.dp && showTopLevelNav

        Scaffold(
            containerColor = ObsidianBg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showTopLevelNav && !useNavRail) {
                    NavigationBar(containerColor = StudioSurface) {
                        NavigationBarItem(
                            selected = currentRoute == HomeRoute::class.qualifiedName,
                            onClick = {
                                navController.navigate(HomeRoute) {
                                    launchSingleTop = true
                                    popUpTo(HomeRoute) { inclusive = false }
                                }
                            },
                            icon = { Icon(Icons.Filled.MovieCreation, contentDescription = "Studio") },
                            label = { Text("Studio") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = ElectricCyan.copy(alpha = 0.16f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_studio")
                        )
                        NavigationBarItem(
                            selected = currentRoute == TemplatesRoute::class.qualifiedName,
                            onClick = {
                                navController.navigate(TemplatesRoute) {
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(Icons.Filled.AutoAwesomeMosaic, contentDescription = "Templates") },
                            label = { Text("Templates") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = ElectricCyan.copy(alpha = 0.16f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_templates")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = StudioSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        NavigationRailItem(
                            selected = currentRoute == HomeRoute::class.qualifiedName,
                            onClick = {
                                navController.navigate(HomeRoute) { launchSingleTop = true }
                            },
                            icon = { Icon(Icons.Filled.MovieCreation, contentDescription = "Studio") },
                            label = { Text("Studio") },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = ElectricCyan.copy(alpha = 0.16f)
                            )
                        )
                        NavigationRailItem(
                            selected = currentRoute == TemplatesRoute::class.qualifiedName,
                            onClick = {
                                navController.navigate(TemplatesRoute) { launchSingleTop = true }
                            },
                            icon = { Icon(Icons.Filled.AutoAwesomeMosaic, contentDescription = "Templates") },
                            label = { Text("Templates") },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = ElectricCyan.copy(alpha = 0.16f)
                            )
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute
                    ) {
                        composable<HomeRoute> {
                            HomeScreen(
                                projects = projects,
                                userProfile = userProfile,
                                displayName = displayName,
                                viewModel = studioViewModel,
                                onOpenEditor = {
                                    navController.navigate(EditorRoute) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateTemplates = {
                                    navController.navigate(TemplatesRoute) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable<TemplatesRoute> {
                            TemplatesMarketplaceScreen(
                                templates = studioViewModel.builtInTemplates,
                                presetsState = presetsState,
                                hasActiveProject = activeProject != null,
                                viewModel = studioViewModel,
                                onOpenEditor = {
                                    navController.navigate(EditorRoute) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable<EditorRoute> {
                            EditorWorkspaceScreen(
                                viewModel = studioViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateExport = {
                                    navController.navigate(ExportRoute) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable<ExportRoute> {
                            ExportScreen(
                                viewModel = studioViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
