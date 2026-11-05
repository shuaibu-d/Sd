package com.sdtech.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Scrolling page. Pass [onBack] for a detail screen (adds a back bar and handles navigation-bar and keyboard insets).
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    statusBarPadding: Boolean = true,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    if (onBack != null) {
        Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { SDIcon(SDIconKind.Back, tint = MaterialTheme.colorScheme.onBackground) }
                Text(title.orEmpty(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            Column(Modifier.weight(1f).fillMaxWidth().then(scrollModifier).padding(horizontal = 20.dp, vertical = 8.dp), content = content)
        }
    } else {
        val inset = if (statusBarPadding) Modifier.statusBarsPadding() else Modifier
        Column(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(inset).then(scrollModifier).padding(horizontal = 18.dp, vertical = 16.dp),
            content = content
        )
    }
}
