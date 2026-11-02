package com.sdtech.studio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(scrollModifier)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        content = content
    )
}
