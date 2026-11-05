package com.sdtech.studio.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import com.sdtech.studio.data.model.ProjectType
import com.sdtech.studio.ui.components.SDIcon
import com.sdtech.studio.ui.components.SDIconKind
import com.sdtech.studio.ui.screens.BrandScreen
import com.sdtech.studio.ui.screens.CaptionEditorScreen
import com.sdtech.studio.ui.screens.HomeScreen
import com.sdtech.studio.ui.screens.LibraryScreen
import com.sdtech.studio.ui.screens.PlannerScreen
import com.sdtech.studio.ui.screens.QuickPreviewScreen
import com.sdtech.studio.ui.screens.SettingsScreen
import com.sdtech.studio.ui.screens.TemplateEditorScreen
import com.sdtech.studio.ui.screens.TemplateGalleryScreen
import com.sdtech.studio.ui.screens.VideoExportScreen
import com.sdtech.studio.ui.screens.VideoHubScreen
import com.sdtech.studio.ui.screens.VideoScriptEditorScreen
import com.sdtech.studio.ui.screens.VoiceOverRecorderScreen

private data class Tab(val route: String, val label: String, val icon: SDIconKind)

private val tabs = listOf(
    Tab("home", "Home", SDIconKind.Home),
    Tab("studio", "Posts", SDIconKind.Posts),
    Tab("video-hub", "Videos", SDIconKind.Video),
    Tab("library", "Library", SDIconKind.Library),
    Tab("brand", "Brand", SDIconKind.Brand)
)

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showBar = route == null || tabs.any { it.route == route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    NavigationBar(
                        modifier = Modifier.height(70.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        tabs.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { if (!selected) navController.openTab(tab.route) },
                                icon = {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                                RoundedCornerShape(14.dp)
                                            )
                                            .padding(horizontal = 13.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) { SDIcon(tab.icon, contentDescription = tab.label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = "home", modifier = Modifier.padding(innerPadding)) {
            composable("home") {
                HomeScreen(
                    onCreatePost = { navController.openTab("studio") },
                    onCreateVideo = { navController.navigate("video-script") },
                    onOpenPlanner = { navController.navigate("planner") },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenBrand = { navController.openTab("brand") },
                    onOpenLibrary = { navController.openTab("library") },
                    onOpenProject = { project ->
                        if (project.type == ProjectType.VIDEO) navController.navigate("video-script?projectId=${project.id}")
                        else navController.navigate("template-editor/${project.id.removePrefix("template-draft-")}")
                    }
                )
            }
            composable("studio") { TemplateGalleryScreen(onOpenTemplate = { navController.navigate("template-editor/$it") }) }
            composable("video-hub") {
                VideoHubScreen(
                    onNewVideo = { navController.navigate("video-script") },
                    onOpenVideo = { navController.navigate("video-script?projectId=$it") }
                )
            }
            composable("library") {
                LibraryScreen(
                    onEditAgain = { navController.navigate("template-editor/$it") },
                    onOpenProject = { project ->
                        if (project.type == ProjectType.VIDEO) navController.navigate("video-script?projectId=${project.id}")
                        else navController.navigate("template-editor/${project.id.removePrefix("template-draft-")}")
                    }
                )
            }
            composable("brand") { BrandScreen() }
            composable("template-editor/{templateId}") { entry ->
                TemplateEditorScreen(
                    templateId = entry.arguments?.getString("templateId").orEmpty(),
                    onExit = { navController.popBackStack() },
                    onOpenCaption = { id -> navController.navigate("caption-editor/$id") }
                )
            }
            composable("caption-editor/{projectId}") { entry ->
                CaptionEditorScreen(projectId = entry.arguments?.getString("projectId").orEmpty(), onBack = { navController.popBackStack() })
            }
            composable("planner") { PlannerScreen(onBack = { navController.popBackStack() }) }
            composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable(
                "video-script?projectId={projectId}",
                arguments = listOf(navArgument("projectId") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                VideoScriptEditorScreen(
                    projectIdArg = entry.arguments?.getString("projectId"),
                    onBack = { navController.popBackStack() },
                    onOpenVoiceOver = { id -> navController.navigate("voice-over/$id") },
                    onOpenPreview = { id -> navController.navigate("quick-preview/$id") },
                    onExport = { id -> navController.navigate("video-export/$id") }
                )
            }
            composable("quick-preview/{projectId}") { entry ->
                QuickPreviewScreen(
                    projectId = entry.arguments?.getString("projectId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onExport = { id -> navController.navigate("video-export/$id") }
                )
            }
            composable("video-export/{projectId}") { entry ->
                VideoExportScreen(projectId = entry.arguments?.getString("projectId").orEmpty(), onBack = { navController.popBackStack() })
            }
            composable("voice-over/{projectId}") { entry ->
                VoiceOverRecorderScreen(projectId = entry.arguments?.getString("projectId").orEmpty(), onBack = { navController.popBackStack() })
            }
        }
    }
}
