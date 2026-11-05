@file:Suppress("UnsafeOptInUsageError")

package com.sdtech.studio.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.inspector.MediaExtractorCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Summary of an EBU R128 loudness analysis and normalization pass.
 */
data class LoudnessNormalizationSummary(
    val inputLufs: Double,
    val outputLufsEstimate: Double,
    val inputTruePeakDbtp: Double,
    val outputTruePeakEstimateDbtp: Double,
    val appliedGainDb: Double,
    val peakLimited: Boolean,
    val boostLimited: Boolean,
    val alreadyWithinTolerance: Boolean
)

/**
 * Media3 [AudioProcessor] that normalizes voice audio to the EBU R128 target (-16.0 LUFS)
 * while respecting a true-peak ceiling (-1.5 dBTP) and maximum boost (+18.0 dB) directly
 * inside the Media3 Transformer export or ExoPlayer playback pipeline, eliminating a separate
 * AAC decode/re-encode pass.
 *
 * When [precomputedGainDb] is provided, samples are scaled in streaming fashion with zero
 * buffering delay. When [precomputedGainDb] is null, the processor buffers the clip's PCM
 * samples, runs [EbuR128Loudness.measure] and [EbuR128Loudness.plan] on end-of-stream,
 * and emits the normalized PCM buffer.
 */
class EbuR128NormalizationAudioProcessor(
    val targetLufs: Double = DEFAULT_TARGET_LUFS,
    val truePeakCeilingDbtp: Double = DEFAULT_TRUE_PEAK_CEILING_DBTP,
    val maxBoostDb: Double = DEFAULT_MAX_BOOST_DB,
    val toleranceLu: Double = DEFAULT_TOLERANCE_LU,
    val monoToStereoCompensation: Boolean = true,
    val precomputedGainDb: Double? = null
) : BaseAudioProcessor() {

    private var pendingSamples = FloatArray(16_384)
    private var pendingSampleCount = 0

    @Volatile
    var lastSummary: LoudnessNormalizationSummary? = null
        private set

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encodingSupported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!encodingSupported || inputAudioFormat.channelCount !in 1..2 || inputAudioFormat.sampleRate < 8_000) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val preGain = precomputedGainDb
        if (preGain != null) {
            streamWithGain(inputBuffer, preGain)
        } else {
            accumulateSamples(inputBuffer)
        }
    }

    private fun streamWithGain(inputBuffer: ByteBuffer, gainDb: Double) {
        val remainingBytes = inputBuffer.remaining()
        val out = replaceOutputBuffer(remainingBytes).order(ByteOrder.LITTLE_ENDIAN)
        val src = inputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN)
        val linearGain = dbToLinear(gainDb)
        val ceilingLinear = dbToLinear(truePeakCeilingDbtp).coerceAtMost(1.0)
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> {
                while (src.remaining() >= 4) {
                    val sample = src.float
                    appendPendingSample(sample)
                    val scaled = (sample.toDouble() * linearGain)
                        .coerceIn(-ceilingLinear, ceilingLinear)
                        .toFloat()
                    out.putFloat(scaled)
                }
            }
            else -> {
                while (src.remaining() >= 2) {
                    val shortSample = src.short
                    val normalized = shortSample / 32768f
                    appendPendingSample(normalized)
                    val scaled = (normalized.toDouble() * linearGain)
                        .coerceIn(-ceilingLinear, ceilingLinear)
                    val outShort = (scaled * 32767.0).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    out.putShort(outShort)
                }
            }
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    private fun accumulateSamples(inputBuffer: ByteBuffer) {
        val src = inputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN)
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> {
                while (src.remaining() >= 4) {
                    appendPendingSample(src.float.coerceIn(-1f, 1f))
                }
            }
            else -> {
                while (src.remaining() >= 2) {
                    appendPendingSample(src.short / 32768f)
                }
            }
        }
        inputBuffer.position(inputBuffer.limit())
    }

    private fun appendPendingSample(sample: Float) {
        if (pendingSampleCount == pendingSamples.size) {
            pendingSamples = pendingSamples.copyOf(pendingSamples.size * 2)
        }
        pendingSamples[pendingSampleCount++] = sample
    }

    override fun onQueueEndOfStream() {
        if (pendingSampleCount == 0) return
        val rawSamples = pendingSamples.copyOf(pendingSampleCount)
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleRate = inputAudioFormat.sampleRate

        val monoSamples = if (channels == 1) {
            rawSamples
        } else {
            val frameCount = rawSamples.size / channels
            FloatArray(frameCount) { frame ->
                var sum = 0f
                for (ch in 0 until channels) {
                    sum += rawSamples[frame * channels + ch]
                }
                sum / channels
            }
        }

        val minRequiredSamples = sampleRate * 2 / 5
        val summary: LoudnessNormalizationSummary? = if (monoSamples.size >= minRequiredSamples) {
            runCatching {
                analyzeMonoSamples(
                    monoSamples = monoSamples,
                    sampleRate = sampleRate,
                    isMonoInput = channels == 1 && monoToStereoCompensation,
                    targetLufs = targetLufs,
                    truePeakCeilingDbtp = truePeakCeilingDbtp,
                    maxBoostDb = maxBoostDb,
                    toleranceLu = toleranceLu,
                    overrideGainDb = precomputedGainDb
                )
            }.getOrNull()
        } else {
            null
        }
        lastSummary = summary

        if (precomputedGainDb == null) {
            val gainDb = summary?.appliedGainDb ?: 0.0
            val gainedSamples = if (abs(gainDb) < 1e-6) {
                rawSamples
            } else {
                EbuR128Loudness.applyGain(rawSamples, gainDb)
            }
            val bytesPerSample = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
            val out = replaceOutputBuffer(gainedSamples.size * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)
            if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
                for (sample in gainedSamples) {
                    out.putFloat(sample.coerceIn(-1f, 1f))
                }
            } else {
                for (sample in gainedSamples) {
                    val s = (sample.coerceIn(-1f, 1f) * 32767f).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    out.putShort(s)
                }
            }
            out.flip()
        }
        pendingSampleCount = 0
    }

    override fun onFlush() {
        pendingSampleCount = 0
    }

    override fun onReset() {
        pendingSampleCount = 0
        pendingSamples = FloatArray(16_384)
        lastSummary = null
    }

    companion object {
        const val DEFAULT_TARGET_LUFS = -16.0
        const val DEFAULT_TRUE_PEAK_CEILING_DBTP = -1.5
        const val DEFAULT_MAX_BOOST_DB = 18.0
        const val DEFAULT_TOLERANCE_LU = 0.7
        const val TARGET_SAMPLE_RATE = 44_100
        const val MONO_TO_STEREO_COMPENSATION_DB = 3.0102999566
        private const val TIMEOUT_US = 10_000L

        private fun dbToLinear(db: Double): Double = exp((db / 20.0) * ln(10.0))

        /**
         * Computes the EBU R128 loudness measurement and constrained gain plan with the same
         * target (-16 LUFS), true-peak ceiling (-1.5 dBTP), and dual-mono compensation (+3.0103 dB)
         * used across SD Studio.
         */
        fun analyzeMonoSamples(
            monoSamples: FloatArray,
            sampleRate: Int = TARGET_SAMPLE_RATE,
            isMonoInput: Boolean = true,
            targetLufs: Double = DEFAULT_TARGET_LUFS,
            truePeakCeilingDbtp: Double = DEFAULT_TRUE_PEAK_CEILING_DBTP,
            maxBoostDb: Double = DEFAULT_MAX_BOOST_DB,
            toleranceLu: Double = DEFAULT_TOLERANCE_LU,
            overrideGainDb: Double? = null
        ): LoudnessNormalizationSummary {
            val resampled = if (sampleRate == TARGET_SAMPLE_RATE) {
                monoSamples
            } else {
                resampleBandLimited(monoSamples, sampleRate, TARGET_SAMPLE_RATE)
            }
            val rawMeasurement = EbuR128Loudness.measure(resampled, TARGET_SAMPLE_RATE)
            val compensationDb = if (isMonoInput) MONO_TO_STEREO_COMPENSATION_DB else 0.0
            val measurement = rawMeasurement.copy(
                integratedLufs = rawMeasurement.integratedLufs + compensationDb
            )
            val plan = EbuR128Loudness.plan(
                measurement = measurement,
                targetLufs = targetLufs,
                maxTruePeakDbtp = truePeakCeilingDbtp,
                maxBoostDb = maxBoostDb
            )
            val alreadyWithinTolerance = abs(measurement.integratedLufs - targetLufs) <= toleranceLu &&
                measurement.truePeakDbtp <= truePeakCeilingDbtp
            val appliedGainDb = overrideGainDb ?: if (alreadyWithinTolerance) 0.0 else plan.gainDb
            val gained = if (abs(appliedGainDb) < 1e-6) resampled else EbuR128Loudness.applyGain(resampled, appliedGainDb)
            val verifiedRaw = EbuR128Loudness.measure(gained, TARGET_SAMPLE_RATE)
            val verified = verifiedRaw.copy(
                integratedLufs = verifiedRaw.integratedLufs + compensationDb
            )
            return LoudnessNormalizationSummary(
                inputLufs = measurement.integratedLufs,
                outputLufsEstimate = verified.integratedLufs,
                inputTruePeakDbtp = measurement.truePeakDbtp,
                outputTruePeakEstimateDbtp = verified.truePeakDbtp,
                appliedGainDb = appliedGainDb,
                peakLimited = plan.peakLimited && !alreadyWithinTolerance,
                boostLimited = plan.boostLimited && !alreadyWithinTolerance,
                alreadyWithinTolerance = alreadyWithinTolerance
            )
        }

        /**
         * Processes a mono/stereo [FloatArray] through an instance of [EbuR128NormalizationAudioProcessor]
         * and returns the normalized samples alongside the [LoudnessNormalizationSummary].
         */
        fun processPcmFloatArray(
            input: FloatArray,
            sampleRate: Int = TARGET_SAMPLE_RATE,
            channelCount: Int = 1,
            encoding: Int = C.ENCODING_PCM_16BIT,
            targetLufs: Double = DEFAULT_TARGET_LUFS,
            truePeakCeilingDbtp: Double = DEFAULT_TRUE_PEAK_CEILING_DBTP,
            monoToStereoCompensation: Boolean = true,
            precomputedGainDb: Double? = null
        ): Pair<FloatArray, LoudnessNormalizationSummary> {
            val processor = EbuR128NormalizationAudioProcessor(
                targetLufs = targetLufs,
                truePeakCeilingDbtp = truePeakCeilingDbtp,
                monoToStereoCompensation = monoToStereoCompensation,
                precomputedGainDb = precomputedGainDb
            )
            val format = AudioProcessor.AudioFormat(sampleRate, channelCount, encoding)
            processor.configure(format)
            processor.flush()

            val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
            val inBuffer = ByteBuffer.allocateDirect(input.size * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)
            if (encoding == C.ENCODING_PCM_FLOAT) {
                for (sample in input) inBuffer.putFloat(sample.coerceIn(-1f, 1f))
            } else {
                for (sample in input) {
                    val s = (sample.coerceIn(-1f, 1f) * 32767f).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    inBuffer.putShort(s)
                }
            }
            inBuffer.flip()

            val collectedBytes = ByteArrayOutputStream(input.size * bytesPerSample)
            processor.queueInput(inBuffer)
            drainProcessorOutput(processor, collectedBytes)
            processor.queueEndOfStream()
            drainProcessorOutput(processor, collectedBytes)

            val outByteArray = collectedBytes.toByteArray()
            val outWrap = ByteBuffer.wrap(outByteArray).order(ByteOrder.LITTLE_ENDIAN)
            val outputFloats = if (encoding == C.ENCODING_PCM_FLOAT) {
                val floatBuf = outWrap.asFloatBuffer()
                FloatArray(floatBuf.remaining()) { floatBuf.get(it) }
            } else {
                val shortBuf = outWrap.asShortBuffer()
                FloatArray(shortBuf.remaining()) { shortBuf.get(it) / 32768f }
            }
            val summary = processor.lastSummary ?: analyzeMonoSamples(
                monoSamples = input,
                sampleRate = sampleRate,
                isMonoInput = channelCount == 1 && monoToStereoCompensation,
                targetLufs = targetLufs,
                truePeakCeilingDbtp = truePeakCeilingDbtp,
                overrideGainDb = precomputedGainDb
            )
            processor.reset()
            return outputFloats to summary
        }

        private fun drainProcessorOutput(processor: AudioProcessor, sink: ByteArrayOutputStream) {
            while (true) {
                val out = processor.output
                if (!out.hasRemaining()) break
                val chunk = ByteArray(out.remaining())
                out.get(chunk)
                sink.write(chunk)
            }
        }

        /**
         * Analyzes an audio file off the main thread using Media3 [MediaExtractorCompat] and
         * computes its [LoudnessNormalizationSummary] without creating a re-encoded duplicate file.
         * The resulting gain is applied inside the Media3 [androidx.media3.transformer.Transformer]
         * export pipeline via [EbuR128NormalizationAudioProcessor].
         */
        suspend fun analyzeAudioFile(
            context: Context,
            input: File,
            targetLufs: Double = DEFAULT_TARGET_LUFS,
            truePeakCeilingDbtp: Double = DEFAULT_TRUE_PEAK_CEILING_DBTP
        ): LoudnessNormalizationSummary = withContext(Dispatchers.IO) {
            require(input.isFile && input.length() > 1024L) {
                "The voice recording could not be found or is incomplete."
            }
            val jobContext = currentCoroutineContext()
            val decoded = decodeWithMedia3Extractor(context, input, jobContext)
            require(decoded.channels == 1) {
                "This take is not mono. Re-record it with the SD Studio voice-over recorder before normalizing."
            }
            analyzeMonoSamples(
                monoSamples = decoded.samples,
                sampleRate = decoded.sampleRate,
                isMonoInput = true,
                targetLufs = targetLufs,
                truePeakCeilingDbtp = truePeakCeilingDbtp
            )
        }

        private data class DecodedAudio(val samples: FloatArray, val sampleRate: Int, val channels: Int)

        private fun decodeWithMedia3Extractor(
            context: Context,
            file: File,
            jobContext: kotlin.coroutines.CoroutineContext
        ): DecodedAudio {
            val extractor = MediaExtractorCompat(context.applicationContext)
            var decoder: MediaCodec? = null
            try {
                extractor.setDataSource(file.absolutePath)
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: error("No audio track was found in this recording.")
                val format = extractor.getTrackFormat(track)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio format.")
                extractor.selectTrack(track)
                decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(format, null, null, 0)
                decoder.start()
                var inputDone = false
                var outputDone = false
                var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
                val output = ByteArrayOutputStream()
                val info = MediaCodec.BufferInfo()
                while (!outputDone) {
                    jobContext.ensureActive()
                    if (!inputDone) {
                        val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val buffer = decoder.getInputBuffer(inputIndex) ?: error("Decoder input buffer unavailable.")
                            buffer.clear()
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val outputIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val decodedFormat = decoder.outputFormat
                            sampleRate = decodedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = decodedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            if (decodedFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                pcmEncoding = decodedFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            val buffer = decoder.getOutputBuffer(outputIndex)
                            if (buffer != null && info.size > 0) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val slice = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
                                when (pcmEncoding) {
                                    AudioFormat.ENCODING_PCM_FLOAT -> while (slice.remaining() >= 4) {
                                        val value = slice.float.coerceIn(-1f, 1f)
                                        val short = (value * 32767f).roundToInt().toShort()
                                        output.write(short.toInt() and 0xff)
                                        output.write((short.toInt() shr 8) and 0xff)
                                    }
                                    else -> {
                                        val chunk = ByteArray(slice.remaining())
                                        slice.get(chunk)
                                        output.write(chunk)
                                    }
                                }
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
                val bytes = output.toByteArray()
                val frameBytes = channels * 2
                require(frameBytes > 0 && bytes.size >= frameBytes * (sampleRate / 2)) {
                    "The recording is too short or contains no usable audio."
                }
                val shortBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val shorts = ShortArray(shortBuffer.remaining())
                shortBuffer.get(shorts)
                val floats = FloatArray(shorts.size) { shorts[it] / 32768f }
                return DecodedAudio(floats, sampleRate, channels)
            } finally {
                runCatching { decoder?.stop() }
                runCatching { decoder?.release() }
                extractor.release()
            }
        }

        internal fun resampleBandLimited(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
            require(fromRate > 0 && toRate > 0 && input.isNotEmpty())
            val outputSize = (input.size.toDouble() * toRate / fromRate).roundToInt().coerceAtLeast(1)
            val cutoff = min(1.0, toRate.toDouble() / fromRate) * 0.94
            val radius = 16
            return FloatArray(outputSize) { outIndex ->
                val sourcePosition = outIndex.toDouble() * fromRate / toRate
                val center = sourcePosition.toInt()
                var weighted = 0.0
                var weightTotal = 0.0
                for (tap in center - radius + 1..center + radius) {
                    if (tap !in input.indices) continue
                    val distance = sourcePosition - tap
                    val normalized = distance / radius
                    if (abs(normalized) >= 1.0) continue
                    val x = cutoff * distance
                    val sinc = if (abs(x) < 1e-12) 1.0 else kotlin.math.sin(Math.PI * x) / (Math.PI * x)
                    val window = 0.5 + 0.5 * kotlin.math.cos(Math.PI * normalized)
                    val weight = cutoff * sinc * window
                    weighted += input[tap] * weight
                    weightTotal += weight
                }
                if (abs(weightTotal) < 1e-12) 0f else (weighted / weightTotal).toFloat().coerceIn(-1f, 1f)
            }
        }
    }
}
