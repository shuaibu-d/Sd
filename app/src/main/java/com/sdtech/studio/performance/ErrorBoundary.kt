package com.sdtech.studio.performance

import android.app.Application
import com.sdtech.studio.CrashLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class RecoverableErrorEvent(
    val id: Long = System.nanoTime(),
    val operationName: String,
    val userMessage: String,
    val technicalDetails: String,
    val onRetry: (() -> Unit)? = null
)

/**
 * Global error boundary for rendering and export operations.
 * Any non-cancellation exception in a render or export is logged to [CrashLogger]
 * (`last_crash.txt`) and surfaced as a friendly retry dialog — never a hard crash.
 */
object ErrorBoundary {
    private val _currentError = MutableStateFlow<RecoverableErrorEvent?>(null)
    val currentError: StateFlow<RecoverableErrorEvent?> = _currentError.asStateFlow()

    @Volatile
    private var application: Application? = null

    @Volatile
    private var fallbackFilesDir: File? = null

    fun install(app: Application) {
        application = app
        fallbackFilesDir = app.filesDir
    }

    fun installForTesting(filesDir: File) {
        fallbackFilesDir = filesDir
    }

    fun report(
        operationName: String,
        throwable: Throwable,
        userMessage: String? = null,
        onRetry: (() -> Unit)? = null
    ) {
        if (throwable is CancellationException) throw throwable
        val app = application
        val dir = fallbackFilesDir
        if (app != null) {
            CrashLogger.recordHandledException(app, operationName, throwable)
        } else if (dir != null) {
            CrashLogger.recordHandledExceptionToDir(dir, operationName, throwable)
        }
        val friendly = userMessage
            ?: throwable.message?.takeIf { it.isNotBlank() }
            ?: "Something went wrong while running $operationName. Please try again."
        _currentError.value = RecoverableErrorEvent(
            operationName = operationName,
            userMessage = friendly,
            technicalDetails = "${throwable::class.simpleName}: ${throwable.message.orEmpty()}",
            onRetry = onRetry
        )
    }

    fun report(
        source: String,
        throwable: Throwable,
        title: String,
        message: String,
        onRetry: (() -> Unit)? = null
    ) {
        report(
            operationName = "$title ($source)",
            throwable = throwable,
            userMessage = message,
            onRetry = onRetry
        )
    }

    fun dismiss() {
        _currentError.value = null
    }

    fun retryAndDismiss() {
        val current = _currentError.value
        _currentError.value = null
        current?.onRetry?.invoke()
    }

    fun retryCurrent() = retryAndDismiss()

    suspend inline fun <T> runGuarded(
        operationName: String,
        userMessage: String? = null,
        noinline onRetry: (() -> Unit)? = null,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            report(operationName, error, userMessage, onRetry)
            null
        }
    }

    suspend inline fun <T> runGuarded(
        source: String,
        title: String,
        message: String,
        noinline onRetry: (() -> Unit)? = null,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            report(source = source, throwable = error, title = title, message = message, onRetry = onRetry)
            null
        }
    }
}
