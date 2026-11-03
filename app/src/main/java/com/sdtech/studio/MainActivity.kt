package com.sdtech.studio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdtech.studio.ui.navigation.AppNavigation
import com.sdtech.studio.ui.theme.SDStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SDStudioApplication
        val previousCrash = CrashLogger.consumeCrashPrompt(app)
        setContent {
            val settings by app.appContainer.settingsRepository.settings.collectAsState(
                initial = com.sdtech.studio.data.model.AppSettings()
            )
            val dark = when (settings.themeMode) {
                com.sdtech.studio.data.model.AppSettings.THEME_DARK -> true
                com.sdtech.studio.data.model.AppSettings.THEME_LIGHT -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                )
                onDispose { }
            }
            SDStudioTheme(darkTheme = dark) {
                AppNavigation()
                if (!previousCrash.isNullOrBlank()) CrashReportDialog(crashReport = previousCrash)
            }
        }
    }
}

@Composable
private fun CrashReportDialog(crashReport: String) {
    var showDialog by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.crash_dialog_title)) },
            text = {
                Text(
                    text = crashReport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(context.getString(R.string.crash_clip_label), crashReport)
                        )
                    }
                ) {
                    Text(stringResource(R.string.crash_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.crash_dismiss))
                }
            }
        )
    }
}
