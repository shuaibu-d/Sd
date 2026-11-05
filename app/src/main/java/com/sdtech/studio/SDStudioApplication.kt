package com.sdtech.studio

import android.app.Application
import com.sdtech.studio.di.AppContainer
import com.sdtech.studio.performance.ErrorBoundary
import com.sdtech.studio.performance.MemoryGuard
import com.sdtech.studio.performance.ThermalGuard
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
        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
        CrashLogger.install(this)
        ErrorBoundary.install(this)
        MemoryGuard.install(this)
        ThermalGuard.install(this)
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

    fun recordHandledException(context: android.content.Context, operationName: String, throwable: Throwable) {
        recordHandledExceptionToDir(context.filesDir, operationName, throwable)
    }

    fun recordHandledExceptionToDir(filesDir: File, operationName: String, throwable: Throwable) {
        runCatching {
            filesDir.mkdirs()
            val stackTrace = StringWriter().also { writer ->
                throwable.printStackTrace(PrintWriter(writer))
            }.toString()
            val entry = buildString {
                appendLine("--- Handled error in $operationName (${java.time.Instant.now()}) ---")
                append("Thread: ")
                appendLine(Thread.currentThread().name)
                appendLine(stackTrace)
                appendLine()
            }
            val crashFile = File(filesDir, CRASH_FILE_NAME)
            val existing = if (crashFile.isFile) crashFile.readText().takeLast(30_000) else ""
            crashFile.writeText((existing + entry).takeLast(60_000))
        }
    }

    fun consumeCrashPrompt(context: android.content.Context): String? {
        val marker = File(context.filesDir, CRASH_PROMPT_MARKER)
        if (marker.exists()) return null
        val text = readLatest(context) ?: return null
        runCatching { marker.writeText("shown") }
        return text
    }

    fun readLatest(context: android.content.Context): String? = readLatestFromDir(context.filesDir)

    fun readLatestFromDir(filesDir: File): String? {
        val crashFile = File(filesDir, CRASH_FILE_NAME)
        if (!crashFile.isFile) return null
        return try { crashFile.readText().takeLast(60_000).takeIf { it.isNotBlank() } } catch (_: Exception) { null }
    }

    fun read(context: android.content.Context): String = readLatest(context).orEmpty()

    fun clear(context: android.content.Context) = clearDir(context.filesDir)

    fun clearDir(filesDir: File) {
        runCatching { File(filesDir, CRASH_FILE_NAME).delete() }
        runCatching { File(filesDir, CRASH_PROMPT_MARKER).delete() }
    }
}
