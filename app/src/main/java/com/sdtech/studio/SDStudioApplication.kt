package com.sdtech.studio

import android.app.Application
import com.sdtech.studio.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

class SDStudioApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        appContainer = AppContainer(this)
        applicationScope.launch {
            runCatching { appContainer.firstLaunchSeeder.seedIfNeeded() }
                .onFailure { android.util.Log.e("SDStudioSeeder", "Initial local data seeding failed", it) }
        }
    }
}

object CrashLogger {
    private const val CRASH_FILE_NAME = "last_crash.txt"
    private const val CRASH_PROMPT_MARKER = "crash_prompt_seen"
    private val installed = AtomicBoolean(false)

    fun install(application: Application) {
        if (!installed.compareAndSet(false, true)) return

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = StringWriter().also { writer ->
                    throwable.printStackTrace(PrintWriter(writer))
                }.toString()

                File(application.filesDir, CRASH_FILE_NAME).writeText(
                    buildString {
                        append("Thread: ")
                        append(thread.name)
                        appendLine()
                        appendLine()
                        append(stackTrace)
                    }
                )
                File(application.filesDir, CRASH_PROMPT_MARKER).delete()
            } catch (_: Exception) {
                // Preserve the original crash behavior even if writing the log fails.
            } finally {
                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, throwable)
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }
        }
    }

    fun consumeCrashPrompt(application: Application): String? {
        val marker = File(application.filesDir, CRASH_PROMPT_MARKER)
        if (marker.exists()) return null
        val text = readLatest(application) ?: return null
        runCatching { marker.writeText("shown") }
        return text
    }

    fun readLatest(application: Application): String? {
        val crashFile = File(application.filesDir, CRASH_FILE_NAME)
        if (!crashFile.isFile) return null
        return try { crashFile.readText().takeLast(60_000).takeIf { it.isNotBlank() } } catch (_: Exception) { null }
    }

    fun clear(application: Application) {
        runCatching { File(application.filesDir, CRASH_FILE_NAME).delete() }
        runCatching { File(application.filesDir, CRASH_PROMPT_MARKER).delete() }
    }
}
