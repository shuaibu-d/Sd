package com.sdtech.studio.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.sdtech.studio.R
import com.sdtech.studio.ui.components.EmptyState
import com.sdtech.studio.ui.components.ScreenScaffold
import com.sdtech.studio.ui.components.SectionHeader

@Composable
fun StudioScreen(onOpenTemplateDebug: () -> Unit = {}) {
    ScreenScaffold {
        SectionHeader(title = stringResource(R.string.studio_title))
        Button(onClick = onOpenTemplateDebug) { Text("Open template renderer preview") }
        EmptyState(
            title = stringResource(R.string.studio_empty_title),
            description = stringResource(R.string.studio_empty_description)
        )
    }
}

@Composable
fun LibraryScreen(onEditAgain: (String) -> Unit = {}) {
    ExportsContent(onEditAgain = {})
}

