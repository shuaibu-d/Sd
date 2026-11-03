package com.sdtech.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.data.local.entity.Project
import com.sdtech.studio.ui.components.BrandScaffold
import com.sdtech.studio.ui.components.SDIcon
import com.sdtech.studio.ui.components.SDIconKind
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(onEditAgain: (String) -> Unit = {}, onOpenProject: (Project) -> Unit = {}) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as SDStudioApplication }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }
    val projects by app.appContainer.projectRepository.projects.collectAsStateWithLifecycle(initialValue = emptyList())

    BrandScaffold(title = "Library", subtitle = "Everything you have made") {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Exports") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Drafts (${projects.size})") })
            }
            Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
                if (tab == 0) ExportsContent(onEditAgain = onEditAgain)
                else if (projects.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        Text("No drafts yet. Start a post or a video and it will be saved here automatically.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(projects, key = { it.id }) { project ->
                            ProjectRow(project, onClick = { onOpenProject(project) }, trailing = {
                                IconButton(onClick = { scope.launch { app.appContainer.projectRepository.deleteProject(project.id) } }) {
                                    SDIcon(SDIconKind.Trash, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}
