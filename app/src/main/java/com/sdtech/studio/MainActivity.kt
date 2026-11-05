package com.sdtech.studio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdtech.studio.performance.ErrorBoundary
import com.sdtech.studio.performance.RecoverableErrorEvent
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
            val recoverableError by ErrorBoundary.currentError.collectAsState()
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
                recoverableError?.let { event ->
                    ErrorBoundaryDialog(event = event)
                }
            }
        }
    }
}

@Composable
private fun ErrorBoundaryDialog(event: RecoverableErrorEvent) {
    AlertDialog(
        onDismissRequest = { ErrorBoundary.dismiss() },
        title = { Text("Could not complete ${event.operationName}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = event.userMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Details have been recorded to the local crash log.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (event.onRetry != null) {
                Button(onClick = { ErrorBoundary.retryAndDismiss() }) {
                    Text("Retry")
                }
            } else {
                Button(onClick = { ErrorBoundary.dismiss() }) {
                    Text("OK")
                }
            }
        },
        dismissButton = {
            if (event.onRetry != null) {
                TextButton(onClick = { ErrorBoundary.dismiss() }) {
                    Text("Dismiss")
                }
            }
        }
    )
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
