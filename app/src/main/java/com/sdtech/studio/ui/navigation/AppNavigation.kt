package com.sdtech.studio.ui.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sdtech.studio.R
import com.sdtech.studio.ui.screens.BrandScreen
import com.sdtech.studio.ui.screens.HomeScreen
import com.sdtech.studio.ui.screens.LibraryScreen
import com.sdtech.studio.ui.screens.StudioScreen
import com.sdtech.studio.ui.screens.TemplateDebugScreen
import com.sdtech.studio.ui.screens.TemplateGalleryScreen
import com.sdtech.studio.ui.screens.TemplateEditorScreen
import com.sdtech.studio.ui.screens.PlannerScreen
import com.sdtech.studio.ui.screens.CaptionEditorScreen
import com.sdtech.studio.ui.screens.VideoScriptEditorScreen
import com.sdtech.studio.ui.screens.VoiceOverRecorderScreen
import com.sdtech.studio.ui.screens.QuickPreviewScreen
import com.sdtech.studio.ui.screens.VideoExportScreen
import com.sdtech.studio.ui.screens.SettingsScreen

private data class BottomDestination(
    val route: String,
    val labelResource: Int,
    val symbol: String
)

private val bottomDestinations = listOf(
    BottomDestination("home", R.string.nav_home, "⌂"),
    BottomDestination("studio", R.string.nav_studio, "✦"),
    BottomDestination("library", R.string.nav_library, "▤"),
    BottomDestination("brand", R.string.nav_brand, "◈"),
    BottomDestination("planner", R.string.nav_planner, "▦"),
    BottomDestination("settings", R.string.nav_settings, "⚙")
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            if (currentDestination?.route?.startsWith("voice-over") != true && currentDestination?.route?.startsWith("quick-preview") != true && currentDestination?.route?.startsWith("video-export") != true) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(destination.symbol) },
                            label = { Text(stringResource(destination.labelResource)) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    onCreatePost = { navController.navigate("studio") },
                    onCreateVideo = { navController.navigate("video-script") }
                )
            }
            composable("studio") { TemplateGalleryScreen(onOpenTemplate = { navController.navigate("template-editor/$it") }, onOpenDebug = { navController.navigate("template-debug") }) }
            composable("template-editor/{templateId}") { entry ->
                TemplateEditorScreen(templateId = entry.arguments?.getString("templateId").orEmpty(), onExit = { navController.popBackStack() }, onOpenCaption = { id -> navController.navigate("caption-editor/$id") })
            }
            composable("caption-editor/{projectId}") { entry ->
                CaptionEditorScreen(projectId = entry.arguments?.getString("projectId").orEmpty(), onBack = { navController.popBackStack() })
            }
            composable("planner") { PlannerScreen() }
            composable("video-script") { VideoScriptEditorScreen(onBack = { navController.popBackStack() }, onOpenVoiceOver = { id -> navController.navigate("voice-over/$id") }, onOpenPreview = { id -> navController.navigate("quick-preview/$id") }) }
            composable("quick-preview/{projectId}") { entry -> QuickPreviewScreen(projectId = entry.arguments?.getString("projectId").orEmpty(), onBack = { navController.popBackStack() }, onExport = { id -> navController.navigate("video-export/$id") }) }
            composable("video-export/{projectId}") { entry -> VideoExportScreen(projectId = entry.arguments?.getString("projectId").orEmpty(), onBack = { navController.popBackStack() }) }
            composable("voice-over/{projectId}") { entry -> VoiceOverRecorderScreen(projectId = entry.arguments?.getString("projectId").orEmpty(), onBack = { navController.popBackStack() }) }
            composable("template-debug") { TemplateDebugScreen() }
            composable("library") { LibraryScreen(onEditAgain = { navController.navigate("template-editor/$it") }) }
            composable("brand") { BrandScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
