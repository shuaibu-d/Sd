package com.sdtech.studio.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.audio.EbuR128NormalizationAudioProcessor
import com.sdtech.studio.audio.LiveWaveformBuffer
import com.sdtech.studio.audio.WaveformCache
import com.sdtech.studio.data.model.ProjectType
import com.sdtech.studio.media.BindPlayerHolderLifecycle
import com.sdtech.studio.video.VideoProjectPayload
import com.sdtech.studio.video.effectiveDurationMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

private class SceneVoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L
    private var output: File? = null
    val isRecording: Boolean get() = recorder != null

    fun start(file: File) {
        stop()
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        @Suppress("DEPRECATION")
        val media = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setAudioSamplingRate(44_100)
            media.setAudioChannels(1)
            media.setAudioEncodingBitRate(128_000)
            media.setOutputFile(file.absolutePath)
            media.prepare()
            media.start()
            recorder = media
            output = file
            startedAt = System.currentTimeMillis()
        } catch (error: Exception) {
            runCatching { media.reset(); media.release() }
            throw error
        }
    }

    fun level(): Float = runCatching { (recorder?.maxAmplitude ?: 0).toFloat().coerceIn(0f, 32767f) / 32767f }.getOrDefault(0f)

    fun stop(): Pair<File?, Long> {
        val current = recorder ?: return output to 0L
        val duration = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        runCatching { current.stop() }
        runCatching { current.reset(); current.release() }
        recorder = null
        return output to duration
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceOverRecorderScreen(projectId: String, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = remember(context) { (context.applicationContext as SDStudioApplication).appContainer }
    val playerHolder = app.playerHolder
    val json = remember { Json { ignoreUnknownKeys = true; encodeDefaults = true } }
    val scope = rememberCoroutineScope()
    var payload by remember { mutableStateOf<VideoProjectPayload?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var recordAllMode by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var permissionDenied by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    var liveLevel by remember { mutableFloatStateOf(0f) }
    val liveWaveBuffer = remember { LiveWaveformBuffer() }
    var liveWaveVersion by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var normalizing by remember { mutableStateOf(false) }
    var normalizationMessage by remember { mutableStateOf<String?>(null) }
    var manualDurationInput by remember { mutableStateOf("") }
    val recorder = remember(context) { SceneVoiceRecorder(context) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var focusRequest by remember { mutableStateOf<AudioFocusRequest?>(null) }

    suspend fun persist(next: VideoProjectPayload) {
        payload = next
        val old = app.projectRepository.getProject(projectId) ?: return
        val now = System.currentTimeMillis()
        val v2Doc = com.sdtech.studio.video.domain.ProjectDocumentMigrator.migrateV1ToV2(
            projectId = projectId,
            canvasRatio = old.ratio,
            payload = next,
            createdAtMs = old.createdAt,
            updatedAtMs = now
        )
        app.projectRepository.saveProject(
            old.copy(
                payloadJson = com.sdtech.studio.video.domain.ProjectDocumentMigrator.encodeProjectDocument(v2Doc),
                updatedAt = now,
                documentVersion = com.sdtech.studio.video.domain.TimelineProject.CURRENT_VERSION
            )
        )
    }

    fun stopAndKeepTake() {
        if (!recorder.isRecording) return
        val (file, duration) = recorder.stop()
        recording = false
        liveLevel = 0f
        if (file != null && duration > 250L && file.length() > 1024L) {
            val sampledArray = WaveformCache.put(file.absolutePath, liveWaveBuffer.toFloatArray())
            val levels = List(sampledArray.size) { sampledArray[it] }
            val current = payload ?: return
            val scene = current.scenes.getOrNull(currentIndex) ?: return
            val updated = current.copy(scenes = current.scenes.toMutableList().also { list ->
                val index = list.indexOfFirst { it.id == scene.id }
                if (index >= 0) list[index] = list[index].copy(
                    voiceFilePath = file.absolutePath,
                    voiceOriginalFilePath = null,
                    normalizedLufs = null,
                    normalizedTruePeakDbtp = null,
                    normalizationGainDb = null,
                    voiceDurationMs = duration,
                    waveformLevels = levels
                )
            })
            scene.voiceFilePath?.let(::File)?.takeIf { it.absolutePath != file.absolutePath }?.delete()
            scene.voiceOriginalFilePath?.let(::File)?.takeIf { it.absolutePath != file.absolutePath }?.delete()
            payload = updated
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val old = app.projectRepository.getProject(projectId)
                if (old != null) {
                    val now = System.currentTimeMillis()
                    val v2Doc = com.sdtech.studio.video.domain.ProjectDocumentMigrator.migrateV1ToV2(
                        projectId = projectId,
                        canvasRatio = old.ratio,
                        payload = updated,
                        createdAtMs = old.createdAt,
                        updatedAtMs = now
                    )
                    app.projectRepository.saveProject(
                        old.copy(
                            payloadJson = com.sdtech.studio.video.domain.ProjectDocumentMigrator.encodeProjectDocument(v2Doc),
                            updatedAt = now,
                            documentVersion = com.sdtech.studio.video.domain.TimelineProject.CURRENT_VERSION
                        )
                    )
                }
            }
        } else if (file != null) file.delete()
        liveWaveBuffer.reset()
        liveWaveVersion = 0
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(projectId) {
        val project = app.projectRepository.getProject(projectId)
        if (project != null && project.type == ProjectType.VIDEO) {
            payload = runCatching {
                com.sdtech.studio.video.domain.ProjectDocumentMigrator.decodeAsLegacyPayload(
                    projectId = project.id,
                    canvasRatio = project.ratio,
                    payloadJson = project.payloadJson
                )
            }.getOrNull()
        }
        isLoading = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                permissionDenied = !hasPermission
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopAndKeepTake()
        }
    }

    BindPlayerHolderLifecycle(
        playerHolder = playerHolder,
        onStop = {
            countdownJob?.cancel()
            countdown = 0
            stopAndKeepTake()
            playing = false
        }
    )

    LaunchedEffect(recording) {
        while (recording && recorder.isRecording) {
            val level = recorder.level().coerceIn(0f, 1f)
            liveLevel = level
            liveWaveBuffer.push(level)
            liveWaveVersion++
            delay(100)
        }
    }

    val scenes = payload?.scenes.orEmpty()
    val scene = scenes.getOrNull(currentIndex)
    val audioFile = scene?.voiceFilePath?.let(::File)
    val hasTake = audioFile?.exists() == true
    val durationMs = scene?.effectiveDurationMs

    var cachedWaveform by remember(scene?.id, scene?.voiceFilePath) {
        mutableStateOf(
            WaveformCache.get(scene?.voiceFilePath)
                ?: scene?.waveformLevels?.takeIf { it.isNotEmpty() }?.let { list ->
                    FloatArray(list.size) { list[it] }
                }
                ?: FloatArray(0)
        )
    }

    LaunchedEffect(scene?.id, scene?.voiceFilePath, scene?.waveformLevels) {
        manualDurationInput = scene?.manualDurationMs?.div(1000L)?.toString().orEmpty()
        normalizationMessage = null
        playerHolder.stop()
        playing = false
        cachedWaveform = WaveformCache.getOrSample(
            context = context,
            filePath = scene?.voiceFilePath,
            storedLevels = scene?.waveformLevels.orEmpty()
        )
    }

    fun startRecording() {
        val activeScene = scene ?: return
        playerHolder.stop()
        playing = false
        val dir = File(context.filesDir, "voiceovers/$projectId")
        val file = File(dir, "${activeScene.id}-${System.currentTimeMillis()}.m4a")
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change -> if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopAndKeepTake() }
            .build()
        focusRequest = request
        val focus = audioManager.requestAudioFocus(request)
        if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            errorMessage = "Audio is being used by another app. Close it and try again."
            return
        }
        try {
            liveWaveBuffer.reset()
            liveWaveVersion = 0
            recorder.start(file)
            recording = true
            errorMessage = null
        } catch (error: Exception) {
            audioManager.abandonAudioFocusRequest(request)
            focusRequest = null
            errorMessage = "Could not start recording. Check your microphone and try again."
        }
    }

    fun beginCountdown() {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        countdownJob?.cancel()
        countdownJob = scope.launch {
            countdown = 3
            while (countdown > 0) { delay(1000); countdown-- }
            startRecording()
        }
    }

    fun normalizeCurrentTake() {
        val activeScene = scene ?: return
        val activeFile = audioFile?.takeIf { it.isFile } ?: return
        if (normalizing || recording || countdown > 0) return
        normalizing = true
        normalizationMessage = null
        scope.launch {
            try {
                val original = activeScene.voiceOriginalFilePath?.let(::File)?.takeIf { it.isFile } ?: activeFile
                val result = EbuR128NormalizationAudioProcessor.analyzeAudioFile(
                    context = context,
                    input = original,
                    targetLufs = EbuR128NormalizationAudioProcessor.DEFAULT_TARGET_LUFS,
                    truePeakCeilingDbtp = EbuR128NormalizationAudioProcessor.DEFAULT_TRUE_PEAK_CEILING_DBTP
                )
                if (result.alreadyWithinTolerance) {
                    normalizationMessage = "Already close to the target loudness (${"%.1f".format(result.inputLufs)} LUFS). No gain adjustment needed."
                } else {
                    val updated = (payload ?: return@launch).copy(scenes = scenes.toMutableList().also { list ->
                        val index = list.indexOfFirst { it.id == activeScene.id }
                        if (index >= 0) list[index] = list[index].copy(
                            voiceFilePath = original.absolutePath,
                            voiceOriginalFilePath = original.absolutePath,
                            normalizedLufs = result.outputLufsEstimate,
                            normalizedTruePeakDbtp = result.outputTruePeakEstimateDbtp,
                            normalizationGainDb = result.appliedGainDb
                        )
                    })
                    persist(updated)
                    normalizationMessage = buildString {
                        append("Normalized to approximately ${"%.1f".format(result.outputLufsEstimate)} LUFS; gain ${"%+.1f".format(result.appliedGainDb)} dB (applied via Media3 AudioProcessor during export).")
                        if (result.peakLimited) append(" Gain was limited by the true-peak ceiling, so the target loudness could not be reached without risking clipping.")
                        if (result.boostLimited) append(" Boost was capped at +18 dB to avoid excessively raising room noise.")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                normalizationMessage = error.message ?: "Normalization failed. Your original recording has been kept unchanged."
            } finally {
                normalizing = false
            }
        }
    }

    fun restoreOriginalTake() {
        val activeScene = scene ?: return
        val original = activeScene.voiceOriginalFilePath?.let(::File)?.takeIf { it.isFile } ?: return
        val normalized = activeScene.voiceFilePath?.let(::File)
        val updated = (payload ?: return).copy(scenes = scenes.toMutableList().also { list ->
            val index = list.indexOfFirst { it.id == activeScene.id }
            if (index >= 0) list[index] = list[index].copy(
                voiceFilePath = original.absolutePath,
                voiceOriginalFilePath = null,
                normalizedLufs = null,
                normalizedTruePeakDbtp = null,
                normalizationGainDb = null
            )
        })
        scope.launch {
            persist(updated)
            normalized?.takeIf { it.absolutePath != original.absolutePath }?.delete()
            normalizationMessage = "Original recording restored."
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Voice-over recorder") }, navigationIcon = { TextButton(onClick = { stopAndKeepTake(); playerHolder.release(); onBack() }) { Text("Back") } }) }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            payload == null || scene == null -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("No video scenes found", style = MaterialTheme.typography.titleLarge)
                Text("Return to the video editor and create or save your scenes first.")
                Button(onClick = onBack) { Text("Back to editor") }
            }
            !hasPermission -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(30.dp))
                Text("Microphone access", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("SD Studio needs microphone access to record narration for your scenes. Audio is saved privately on this device inside this project.", style = MaterialTheme.typography.bodyLarge)
                if (permissionDenied) {
                    Text("Microphone permission was denied. You can try again, or enable it in Android Settings → Apps → SD Studio → Permissions → Microphone.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Try again") }
                    Text("If Android no longer shows the permission prompt, open App info and allow Microphone access.", style = MaterialTheme.typography.bodySmall)
                } else Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow microphone") }
            }
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (recordAllMode) "Record all • ${currentIndex + 1}/${scenes.size}" else "Scene ${currentIndex + 1} of ${scenes.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { recordAllMode = true; currentIndex = 0 }, enabled = !recording && countdown == 0 && !normalizing) { Text(if (recordAllMode) "Restart sequence" else "Record all") }
                }
                LinearProgressIndicator(progress = { (currentIndex + 1).toFloat() / scenes.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("READ THIS ALOUD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(scene.text.ifBlank { "Add your scene script in the video editor." }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (countdown > 0) Text(countdown.toString(), modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (recording) "Recording…" else if (hasTake) "Voice take saved" else "Ready to record", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            if (recording) Text("LIVE", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                            // Read liveWaveVersion when recording to trigger redraw without allocating lists
                            if (recording) {
                                val tick = liveWaveVersion
                                val count = if (tick >= 0) liveWaveBuffer.count.coerceAtLeast(1) else 1
                                val step = size.width / count
                                for (index in 0 until liveWaveBuffer.count) {
                                    val value = liveWaveBuffer.getAt(index)
                                    val h = (value.coerceIn(0.04f, 1f) * size.height * 0.88f).coerceAtLeast(3f)
                                    drawLine(
                                        color = Color(0xFFE45B62),
                                        start = androidx.compose.ui.geometry.Offset(index * step + step / 2, (size.height - h) / 2),
                                        end = androidx.compose.ui.geometry.Offset(index * step + step / 2, (size.height + h) / 2),
                                        strokeWidth = (step * 0.55f).coerceAtLeast(2f),
                                        cap = StrokeCap.Round
                                    )
                                }
                            } else {
                                val values = cachedWaveform
                                val count = values.size.coerceAtLeast(1)
                                val step = size.width / count
                                for (index in values.indices) {
                                    val h = (values[index].coerceIn(0.04f, 1f) * size.height * 0.88f).coerceAtLeast(3f)
                                    drawLine(
                                        color = Color(0xFF16A6A0),
                                        start = androidx.compose.ui.geometry.Offset(index * step + step / 2, (size.height - h) / 2),
                                        end = androidx.compose.ui.geometry.Offset(index * step + step / 2, (size.height + h) / 2),
                                        strokeWidth = (step * 0.55f).coerceAtLeast(2f),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                        if (recording) LinearProgressIndicator(progress = { liveLevel }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { if (recording) stopAndKeepTake() else beginCountdown() }, enabled = countdown == 0 && !normalizing, modifier = Modifier.weight(1f)) { Text(if (recording) "Stop" else if (hasTake) "Re-record" else "Record") }
                            OutlinedButton(onClick = {
                                if (hasTake && !recording) {
                                    if (playing) {
                                        playerHolder.stop()
                                        playing = false
                                    } else {
                                        playing = true
                                        playerHolder.playFile(
                                            file = audioFile!!,
                                            isSpeech = true,
                                            onEnded = { playing = false },
                                            onError = { playing = false }
                                        )
                                    }
                                }
                            }, enabled = hasTake && !recording && !normalizing, modifier = Modifier.weight(1f)) { Text(if (playing) "Playing" else "Play") }
                        }
                        OutlinedButton(onClick = {
                            if (!recording && hasTake) {
                                playerHolder.stop()
                                playing = false
                                audioFile?.delete()
                                scene.voiceOriginalFilePath?.let(::File)?.delete()
                                cachedWaveform = FloatArray(0)
                                val updated = payload!!.copy(scenes = scenes.toMutableList().also { list -> list[currentIndex] = list[currentIndex].copy(voiceFilePath = null, voiceOriginalFilePath = null, normalizedLufs = null, normalizedTruePeakDbtp = null, normalizationGainDb = null, voiceDurationMs = null, waveformLevels = emptyList()) })
                                normalizationMessage = null
                                scope.launch { persist(updated) }
                            }
                        }, enabled = hasTake && !recording && !normalizing, modifier = Modifier.fillMaxWidth()) { Text("Delete take") }
                        if (scene.voiceDurationMs != null) Text("Take: ${formatDuration(scene.voiceDurationMs)}", style = MaterialTheme.typography.bodySmall)
                        if (durationMs != null) Text("Scene duration: ${formatDuration(durationMs)}", style = MaterialTheme.typography.bodySmall)
                        if (hasTake) {
                            OutlinedButton(
                                onClick = { normalizeCurrentTake() },
                                enabled = !normalizing && !recording && countdown == 0,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (normalizing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(if (normalizing) "Analysing and normalizing…" else "Normalize voice loudness")
                            }
                            Text("EBU R128 speech target: −16 LUFS (mono-to-stereo compensated) · final AAC peak verified at or below −1.0 dBTP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (scene.voiceOriginalFilePath?.let(::File)?.isFile == true) {
                                OutlinedButton(onClick = { restoreOriginalTake() }, enabled = !normalizing && !recording, modifier = Modifier.fillMaxWidth()) { Text("Restore original recording") }
                            }
                            scene.normalizedLufs?.let { Text("Normalized: ${"%.1f".format(it)} LUFS · peak ${"%.1f".format(scene.normalizedTruePeakDbtp ?: 0.0)} dBTP · ${"%+.1f".format(scene.normalizationGainDb ?: 0.0)} dB gain", style = MaterialTheme.typography.bodySmall) }
                            normalizationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                        }
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("SCENE DURATION · SECONDS (OPTIONAL)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            BasicTextField(
                                value = manualDurationInput,
                                onValueChange = { manualDurationInput = it.filter { char -> char.isDigit() }.take(5) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                singleLine = true
                            )
                            Text("Leave blank to use voice length + 0.4 seconds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        OutlinedButton(onClick = {
                            val seconds = manualDurationInput.toLongOrNull()
                            val updated = payload!!.copy(scenes = scenes.toMutableList().also { list ->
                                list[currentIndex] = list[currentIndex].copy(manualDurationMs = seconds?.takeIf { it > 0 }?.times(1000L))
                            })
                            scope.launch { persist(updated) }
                        }, enabled = !recording && !normalizing, modifier = Modifier.fillMaxWidth()) { Text("Apply manual duration") }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Recording tip", fontWeight = FontWeight.Bold)
                        Text("Choose a quiet room and use a headset microphone. Keep the mic a short, steady distance from your mouth for clearer narration.")
                    }
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { if (!recording) currentIndex = (currentIndex - 1).coerceAtLeast(0) }, enabled = currentIndex > 0 && !recording && !normalizing, modifier = Modifier.weight(1f)) { Text("Previous") }
                    Button(onClick = {
                        if (!recording) {
                            if (currentIndex < scenes.lastIndex) currentIndex++ else recordAllMode = false
                        }
                    }, enabled = !recording && (currentIndex < scenes.lastIndex || recordAllMode) && !normalizing, modifier = Modifier.weight(1f)) { Text(if (recordAllMode && currentIndex == scenes.lastIndex) "Finish" else "Next") }
                }
                Text("Unrecorded scenes keep their place in the sequence. Your takes are saved locally in app storage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
