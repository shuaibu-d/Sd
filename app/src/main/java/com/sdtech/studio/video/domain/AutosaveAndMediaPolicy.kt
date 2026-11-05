package com.sdtech.studio.video.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates debounced autosaving 1.5 seconds (1,500 ms) after the last edit,
 * plus immediate saving on lifecycle stop.
 * Pure Kotlin (no Android imports) for fast deterministic unit testing.
 */
class TimelineAutosaveController(
    private val scope: CoroutineScope,
    val debounceMs: Long = DEFAULT_AUTOSAVE_DEBOUNCE_MS,
    private val onSave: suspend (TimelineProject) -> Unit
) {
    private val mutex = Mutex()
    private var debounceJob: Job? = null
    private var pendingProject: TimelineProject? = null

    @Volatile
    var isDirty: Boolean = false
        private set

    @Volatile
    var saveCount: Int = 0
        private set

    @Volatile
    var lastSavedProject: TimelineProject? = null
        private set

    /**
     * Schedules an autosave [debounceMs] (1.5 s) after the most recent edit.
     * Rapid edits reset the 1.5 s timer so only the latest state is persisted.
     */
    fun onEdit(project: TimelineProject) {
        pendingProject = project
        isDirty = true
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            flushNow()
        }
    }

    /**
     * Triggered when the editor stops (e.g. Activity/Composable ON_STOP).
     * Cancels the debounce timer and flushes any pending edit immediately.
     */
    fun onStop() {
        debounceJob?.cancel()
        debounceJob = null
        if (isDirty && pendingProject != null) {
            scope.launch {
                flushNow()
            }
        }
    }

    /**
     * Immediately persists the pending project if dirty.
     */
    suspend fun flushNow(): Boolean {
        debounceJob?.cancel()
        debounceJob = null
        return mutex.withLock {
            val toSave = pendingProject
            if (!isDirty || toSave == null) {
                false
            } else {
                isDirty = false
                onSave(toSave)
                lastSavedProject = toSave
                saveCount++
                true
            }
        }
    }

    companion object {
        const val DEFAULT_AUTOSAVE_DEBOUNCE_MS = 1_500L
    }
}

data class ResolvedMediaReference(
    val storedUriOrPath: String,
    val copiedToAppStorage: Boolean
)

/**
 * Pure Kotlin gateway interface for checking persistable URI permissions and copying fallback streams.
 */
interface MediaPersistenceGateway {
    fun isLocalFileOrAppPath(uriOrPath: String): Boolean
    fun tryTakePersistableReadPermission(uriString: String): Boolean
    fun copyToAppStorage(uriString: String, preferredExtension: String = "media"): String
}

/**
 * Implements the media storage policy:
 * - Store media by URI/path without copying large originals when local or when source permission is persistable.
 * - Copy to app storage ONLY when the source permission is not persistable.
 */
class MediaReferenceResolver(
    private val gateway: MediaPersistenceGateway
) {
    fun resolve(uriOrPath: String, preferredExtension: String = "media"): ResolvedMediaReference {
        val trimmed = uriOrPath.trim()
        if (trimmed.isEmpty()) {
            return ResolvedMediaReference(storedUriOrPath = "", copiedToAppStorage = false)
        }
        if (gateway.isLocalFileOrAppPath(trimmed)) {
            return ResolvedMediaReference(storedUriOrPath = trimmed, copiedToAppStorage = false)
        }
        val persistable = runCatching {
            gateway.tryTakePersistableReadPermission(trimmed)
        }.getOrDefault(false)

        if (persistable) {
            return ResolvedMediaReference(storedUriOrPath = trimmed, copiedToAppStorage = false)
        }

        val copiedPath = gateway.copyToAppStorage(trimmed, preferredExtension)
        return ResolvedMediaReference(storedUriOrPath = copiedPath, copiedToAppStorage = true)
    }
}
