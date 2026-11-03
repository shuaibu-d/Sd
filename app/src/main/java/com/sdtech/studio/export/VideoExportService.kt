@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sdtech.studio.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.sdtech.studio.MainActivity
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.data.local.entity.ExportRecord
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class VideoExportService : Service() {
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var transformer: Transformer? = null
    private var prepared: PreparedVideoExport? = null
    private val cancelled = AtomicBoolean(false)
    private var projectId = ""
    private var preset = VideoExportPreset.REELS
    private var logoVariant = ExportLogoVariant.FULL
    private var use720p = false
    private var burnCaptions = false
    private var showProgressBar = false
    private var backgroundMusicUri: String? = null
    private var musicVolume = 0.20f
    private var musicLoop = true
    private var autoDuckMusic = true
    private var logoPlacementMode = com.sdtech.studio.render.LogoPlacementMode.SAFE
    private val progressHolder = ProgressHolder()
    private var progressRunnable: Runnable? = null
    private var lastMemoryLogAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelExport()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        if (thread != null) return START_NOT_STICKY

        projectId = intent.getStringExtra(EXTRA_PROJECT).orEmpty()
        preset = runCatching { VideoExportPreset.valueOf(intent.getStringExtra(EXTRA_PRESET) ?: "REELS") }.getOrDefault(VideoExportPreset.REELS)
        logoVariant = runCatching { ExportLogoVariant.valueOf(intent.getStringExtra(EXTRA_LOGO) ?: "FULL") }.getOrDefault(ExportLogoVariant.FULL)
        use720p = intent.getBooleanExtra(EXTRA_720P, false)
        burnCaptions = intent.getBooleanExtra(EXTRA_CAPTIONS, false)
        showProgressBar = intent.getBooleanExtra(EXTRA_PROGRESS_BAR, false)
        backgroundMusicUri = intent.getStringExtra(EXTRA_MUSIC_URI)
        musicVolume = intent.getFloatExtra(EXTRA_MUSIC_VOLUME, 0.20f).coerceIn(0f, 1f)
        musicLoop = intent.getBooleanExtra(EXTRA_MUSIC_LOOP, true)
        autoDuckMusic = intent.getBooleanExtra(EXTRA_MUSIC_DUCK, true)
        logoPlacementMode = runCatching { com.sdtech.studio.render.LogoPlacementMode.valueOf(intent.getStringExtra(EXTRA_LOGO_MODE) ?: "SAFE") }.getOrDefault(com.sdtech.studio.render.LogoPlacementMode.SAFE)
        cancelled.set(false)
        createChannel()
        startForegroundCompat(buildNotification("Preparing video export…", 0, false))

        thread = HandlerThread("SDStudio-Transformer").apply { start() }
        handler = Handler(thread!!.looper)
        handler!!.post { prepareAndStart() }
        return START_NOT_STICKY
    }

    private fun prepareAndStart() {
        try {
            val request = VideoExportRequest(
                projectId = projectId,
                preset = preset,
                logoVariant = logoVariant,
                use720p = use720p,
                burnCaptions = burnCaptions,
                showProgressBar = showProgressBar,
                backgroundMusicUri = backgroundMusicUri,
                musicVolume = musicVolume,
                musicLoop = musicLoop,
                autoDuckMusic = autoDuckMusic,
                logoPlacementMode = logoPlacementMode
            )
            prepared = VideoExportPreparer(this).prepare(request)
            if (cancelled.get()) return finishCancelled()
            val export = prepared!!
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cancelled.get()) return finishCancelled()
                    try {
                        val savedUri = copyToMovies(export.outputFile)
                        val app = (application as SDStudioApplication).appContainer
                        runBlocking {
                            app.exportRepository.recordExport(
                                ExportRecord(
                                    projectId = projectId,
                                    filePath = savedUri.toString(),
                                    mimeType = "video/mp4",
                                    ratio = preset.ratio,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                        export.tempFiles.forEach { it.delete() }
                        export.outputFile.delete()
                        sendEvent(ACTION_COMPLETE) {
                            putExtra(EXTRA_URI, savedUri.toString())
                            putExtra(EXTRA_MISSING, export.missingVoiceFiles.joinToString(", "))
                            putExtra(EXTRA_720P, use720p)
                        }
                        updateNotification("Export complete", 100, true)
                    } catch (error: Throwable) {
                        fail(error)
                    } finally {
                        stopAfterDelay()
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (cancelled.get()) return finishCancelled()
                    val codecFailure = exportException.codecInfo != null ||
                        exportException.errorCode == ExportException.ERROR_CODE_ENCODER_INIT_FAILED ||
                        exportException.errorCode == ExportException.ERROR_CODE_ENCODING_FAILED ||
                        exportException.errorCode == ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED
                    val message = when {
                        codecFailure ->
                            "This device could not encode the selected resolution (${exportException.errorCodeName}). Try the 720p export."
                        exportException.message?.contains("space", true) == true ->
                            "There is not enough storage space. Free up storage and try again."
                        else -> "Video export failed (${exportException.errorCodeName}): ${exportException.message ?: "Unknown media error"}"
                    }
                    fail(IllegalStateException(message, exportException))
                }
            }
            transformer = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()
            transformer!!.start(export.composition, export.outputFile.absolutePath)
            sendEvent(ACTION_PROGRESS) { putExtra(EXTRA_PROGRESS, 0) }
            scheduleProgress()
        } catch (error: Throwable) {
            fail(error)
        }
    }

    private fun scheduleProgress() {
        val h = handler ?: return
        val task = object : Runnable {
            override fun run() {
                val active = transformer ?: return
                val state = runCatching { active.getProgress(progressHolder) }.getOrDefault(Transformer.PROGRESS_STATE_NOT_STARTED)
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastMemoryLogAt >= 5_000L) {
                    lastMemoryLogAt = now
                    runCatching {
                        val info = android.os.Debug.MemoryInfo()
                        android.os.Debug.getMemoryInfo(info)
                        android.util.Log.i("SDStudioExportMemory", "stage=transformer pssKb=${info.totalPss} javaHeapCommittedKb=${Runtime.getRuntime().totalMemory() / 1024L}")
                    }
                }
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val percent = progressHolder.progress.coerceIn(0, 100)
                    updateNotification("Exporting video… $percent%", percent, true)
                    sendEvent(ACTION_PROGRESS) { putExtra(EXTRA_PROGRESS, percent) }
                }
                if (thread != null && !cancelled.get()) h.postDelayed(this, 500)
            }
        }
        progressRunnable = task
        h.post(task)
    }

    private fun copyToMovies(source: File): Uri {
        if (!source.isFile || source.length() <= 0L) throw IOException("The exported video file is empty.")
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "SDStudio_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/SD Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Android could not create the Movies/SD Studio media entry.")
            try {
                contentResolver.openOutputStream(uri, "w")?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IOException("Could not open the destination video file.")
                val publish = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(uri, publish, null, null)
                return uri
            } catch (error: Throwable) {
                contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SD Studio")
            if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create Movies/SD Studio.")
            val target = File(directory, "SDStudio_${System.currentTimeMillis()}.mp4")
            source.copyTo(target, overwrite = false)
            return Uri.fromFile(target)
        }
    }

    private fun cancelExport() {
        cancelled.set(true)
        handler?.post {
            runCatching { transformer?.cancel() }
            prepared?.let { export ->
                export.tempFiles.forEach { it.delete() }
                export.outputFile.delete()
            }
            finishCancelled()
        } ?: finishCancelled()
    }

    private fun finishCancelled() {
        sendEvent(ACTION_CANCELLED) {}
        updateNotification("Export cancelled", 0, false)
        stopAfterDelay()
    }

    private fun fail(error: Throwable) {
        prepared?.let { export ->
            export.tempFiles.forEach { it.delete() }
            export.outputFile.delete()
        }
        val message = when {
            error is IOException || error.message?.contains("space", true) == true ->
                "Not enough storage or unable to save the video. Free up space and retry."
            else -> error.message ?: "The video could not be exported."
        }
        sendEvent(ACTION_ERROR) {
            putExtra(EXTRA_ERROR, message)
            putExtra(EXTRA_CAN_RETRY_720P, message.contains("720p", true) || message.contains("codec", true) || message.contains("encoder", true))
        }
        updateNotification("Export failed", 0, false)
        stopAfterDelay()
    }

    private fun sendEvent(action: String, extras: Intent.() -> Unit) {
        val intent = Intent(action).setPackage(packageName).apply(extras)
        sendBroadcast(intent)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Video exports", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String, progress: Int, ongoing: Boolean): Notification {
        val cancel = PendingIntent.getService(
            this, 92, Intent(this, VideoExportService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(this, 93, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.sdtech.studio.R.drawable.ic_stat_sd)
            .setContentTitle("SD Studio")
            .setContentText(message)
            .setContentIntent(open)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress == 0 && ongoing)
            .apply { if (ongoing) addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel) }
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        when {
            Build.VERSION.SDK_INT >= 35 -> ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            Build.VERSION.SDK_INT >= 29 -> ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(message: String, progress: Int, ongoing: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message, progress, ongoing))
    }

    private fun stopAfterDelay() {
        handler?.removeCallbacks(progressRunnable ?: Runnable {})
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
        thread?.quitSafely()
        thread = null
        handler = null
        transformer = null
        exportActive.set(false)
    }

    override fun onDestroy() {
        handler?.removeCallbacksAndMessages(null)
        runCatching { transformer?.cancel() }
        prepared?.let { export ->
            export.tempFiles.forEach { runCatching { it.delete() } }
            runCatching { export.outputFile.delete() }
        }
        thread?.quitSafely()
        thread = null
        exportActive.set(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.sdtech.studio.export.START"
        const val ACTION_CANCEL = "com.sdtech.studio.export.CANCEL"
        const val ACTION_PROGRESS = "com.sdtech.studio.export.PROGRESS"
        const val ACTION_COMPLETE = "com.sdtech.studio.export.COMPLETE"
        const val ACTION_ERROR = "com.sdtech.studio.export.ERROR"
        const val ACTION_CANCELLED = "com.sdtech.studio.export.CANCELLED"
        const val EXTRA_PROJECT = "project_id"
        const val EXTRA_PRESET = "preset"
        const val EXTRA_LOGO = "logo_variant"
        const val EXTRA_720P = "use_720p"
        const val EXTRA_CAPTIONS = "burn_captions"
        const val EXTRA_PROGRESS_BAR = "show_progress_bar"
        const val EXTRA_MUSIC_URI = "background_music_uri"
        const val EXTRA_MUSIC_VOLUME = "music_volume"
        const val EXTRA_MUSIC_LOOP = "music_loop"
        const val EXTRA_MUSIC_DUCK = "music_auto_duck"
        const val EXTRA_LOGO_MODE = "logo_placement_mode"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_URI = "uri"
        const val EXTRA_ERROR = "error"
        const val EXTRA_MISSING = "missing_voice_files"
        const val EXTRA_CAN_RETRY_720P = "can_retry_720p"
        private const val CHANNEL_ID = "sd_studio_video_export"
        private const val NOTIFICATION_ID = 7104
        private val exportActive = AtomicBoolean(false)
        fun isExportInProgress(): Boolean = exportActive.get()

        fun start(context: android.content.Context, request: VideoExportRequest) {
            check(exportActive.compareAndSet(false, true)) { "An export is already in progress. Wait for it to finish or cancel it first." }
            val intent = Intent(context, VideoExportService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROJECT, request.projectId)
                putExtra(EXTRA_PRESET, request.preset.name)
                putExtra(EXTRA_LOGO, request.logoVariant.name)
                putExtra(EXTRA_720P, request.use720p)
                putExtra(EXTRA_CAPTIONS, request.burnCaptions)
                putExtra(EXTRA_PROGRESS_BAR, request.showProgressBar)
                putExtra(EXTRA_MUSIC_URI, request.backgroundMusicUri)
                putExtra(EXTRA_MUSIC_VOLUME, request.musicVolume)
                putExtra(EXTRA_MUSIC_LOOP, request.musicLoop)
                putExtra(EXTRA_MUSIC_DUCK, request.autoDuckMusic)
                putExtra(EXTRA_LOGO_MODE, request.logoPlacementMode.name)
            }
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (error: Throwable) {
                exportActive.set(false)
                throw error
            }
        }
    }
}
