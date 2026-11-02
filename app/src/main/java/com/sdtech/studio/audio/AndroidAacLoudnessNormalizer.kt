package com.sdtech.studio.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Decodes AAC, performs EBU R128 analysis and constrained linear gain, then encodes a new AAC/M4A. */
class AndroidAacLoudnessNormalizer(
    private val targetLufs: Double = -16.0,
    private val outputTruePeakCeilingDbtp: Double = -1.5
) {
    data class Result(
        val outputFile: File,
        val inputLufs: Double,
        val outputLufsEstimate: Double,
        val inputTruePeakDbtp: Double,
        val outputTruePeakEstimateDbtp: Double,
        val appliedGainDb: Double,
        val peakLimited: Boolean,
        val boostLimited: Boolean,
        val alreadyWithinTolerance: Boolean
    )

    suspend fun normalize(input: File, output: File): Result = withContext(Dispatchers.IO) {
        require(input.isFile && input.length() > 1024L) { "The voice recording could not be found or is incomplete." }
        output.parentFile?.mkdirs()
        val jobContext = currentCoroutineContext()
        val decoded = decodeAac(input, jobContext)
        try {
            require(decoded.channels == 1) { "This take is not mono. Re-record it with the SD Studio voice-over recorder before normalizing." }
            val resampled = if (decoded.sampleRate == TARGET_SAMPLE_RATE) decoded.samples else resampleBandLimited(decoded.samples, decoded.sampleRate, TARGET_SAMPLE_RATE)
            val measurement = measureForStereoVideoPlayback(resampled)
            val plan = EbuR128Loudness.plan(measurement, targetLufs, outputTruePeakCeilingDbtp)
            val alreadyOkay = kotlin.math.abs(measurement.integratedLufs - targetLufs) <= 0.7 && measurement.truePeakDbtp <= outputTruePeakCeilingDbtp
            if (alreadyOkay) {
                output.delete()
                return@withContext Result(input, measurement.integratedLufs, measurement.integratedLufs, measurement.truePeakDbtp, measurement.truePeakDbtp, 0.0, false, false, true)
            }
            var appliedGainDb = plan.gainDb
            var verified: EbuR128Loudness.Measurement? = null
            var attempts = 0
            while (attempts < 3) {
                jobContext.ensureActive()
                val gained = EbuR128Loudness.applyGain(resampled, appliedGainDb)
                encodeAac(gained, TARGET_SAMPLE_RATE, output, jobContext)
                val encoded = decodeAac(output, jobContext)
                require(encoded.channels == 1) { "The device AAC encoder did not preserve mono audio." }
                val encodedPcm = if (encoded.sampleRate == TARGET_SAMPLE_RATE) encoded.samples else resampleBandLimited(encoded.samples, encoded.sampleRate, TARGET_SAMPLE_RATE)
                val measuredOutput = measureForStereoVideoPlayback(encodedPcm)
                verified = measuredOutput
                val peakSafe = measuredOutput.truePeakDbtp <= -1.0
                val loudnessClose = kotlin.math.abs(measuredOutput.integratedLufs - targetLufs) <= 0.6
                val peakLimited = measuredOutput.truePeakDbtp >= -2.0 && measuredOutput.integratedLufs < targetLufs - 0.6
                val boostLimited = appliedGainDb >= 17.99 && measuredOutput.integratedLufs < targetLufs - 0.6
                if (peakSafe && (loudnessClose || peakLimited || boostLimited)) break
                if (attempts == 2) break
                val correction = minOf(targetLufs - measuredOutput.integratedLufs, outputTruePeakCeilingDbtp - measuredOutput.truePeakDbtp)
                if (kotlin.math.abs(correction) < 0.05) break
                appliedGainDb = (appliedGainDb + correction).coerceAtMost(18.0)
                attempts++
            }
            val finalMeasurement = requireNotNull(verified)
            require(finalMeasurement.truePeakDbtp <= -1.0) {
                "The AAC encoder could not meet the true-peak safety limit on this device. Your original recording has been kept unchanged."
            }
            val peakLimited = finalMeasurement.integratedLufs < targetLufs - 0.6 && finalMeasurement.truePeakDbtp >= -2.0
            val boostLimited = finalMeasurement.integratedLufs < targetLufs - 0.6 && appliedGainDb >= 17.99
            require(kotlin.math.abs(finalMeasurement.integratedLufs - targetLufs) <= 0.6 || peakLimited || boostLimited) {
                "The encoded take did not converge on the target loudness. Your original recording has been kept unchanged."
            }
            Result(output, measurement.integratedLufs, finalMeasurement.integratedLufs, measurement.truePeakDbtp, finalMeasurement.truePeakDbtp, appliedGainDb, peakLimited, boostLimited, false)
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }

    private data class DecodedAudio(val samples: FloatArray, val sampleRate: Int, val channels: Int)

    /** A mono voice track is normally duplicated to L/R when played in a stereo video. */
    private fun measureForStereoVideoPlayback(pcm: FloatArray): EbuR128Loudness.Measurement {
        val mono = EbuR128Loudness.measure(pcm, TARGET_SAMPLE_RATE)
        return mono.copy(integratedLufs = mono.integratedLufs + MONO_TO_STEREO_COMPENSATION_DB)
    }

    private fun decodeAac(file: File, jobContext: kotlin.coroutines.CoroutineContext): DecodedAudio {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("No audio track was found in this recording.")
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
                        if (decodedFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) pcmEncoding = decodedFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
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
                                    output.write(short.toInt() and 0xff); output.write((short.toInt() shr 8) and 0xff)
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
            require(frameBytes > 0 && bytes.size >= frameBytes * (sampleRate / 2)) { "The recording is too short or contains no usable audio." }
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

    /** Windowed-sinc band-limited resampler for devices that encode the requested take at another rate. */
    private fun resampleBandLimited(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        require(fromRate > 0 && toRate > 0 && input.isNotEmpty())
        val outputSize = (input.size.toDouble() * toRate / fromRate).roundToInt().coerceAtLeast(1)
        val cutoff = minOf(1.0, toRate.toDouble() / fromRate) * 0.94
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
                if (kotlin.math.abs(normalized) >= 1.0) continue
                val x = cutoff * distance
                val sinc = if (kotlin.math.abs(x) < 1e-12) 1.0 else kotlin.math.sin(Math.PI * x) / (Math.PI * x)
                val window = 0.5 + 0.5 * kotlin.math.cos(Math.PI * normalized)
                val weight = cutoff * sinc * window
                weighted += input[tap] * weight
                weightTotal += weight
            }
            if (kotlin.math.abs(weightTotal) < 1e-12) 0f else (weighted / weightTotal).toFloat().coerceIn(-1f, 1f)
        }
    }

    private fun encodeAac(pcm: FloatArray, sampleRate: Int, output: File, jobContext: kotlin.coroutines.CoroutineContext) {
        output.delete()
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var inputOffset = 0
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()
            val chunkFrames = 1024
            while (!outputDone) {
                jobContext.ensureActive()
                if (!inputDone) {
                    val inputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = encoder.getInputBuffer(inputIndex) ?: error("Encoder input buffer unavailable.")
                        buffer.clear()
                        buffer.order(ByteOrder.LITTLE_ENDIAN)
                        val remainingFrames = pcm.size - inputOffset
                        val frames = minOf(chunkFrames, remainingFrames)
                        if (frames <= 0) {
                            val pts = inputOffset.toLong() * 1_000_000L / sampleRate
                            encoder.queueInputBuffer(inputIndex, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val requiredBytes = frames * 2
                            require(buffer.remaining() >= requiredBytes) { "AAC encoder input buffer is unexpectedly small." }
                            for (i in 0 until frames) {
                                val sample = (pcm[inputOffset + i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
                                buffer.putShort(sample)
                            }
                            val pts = inputOffset.toLong() * 1_000_000L / sampleRate
                            encoder.queueInputBuffer(inputIndex, 0, requiredBytes, pts, 0)
                            inputOffset += frames
                        }
                    }
                }
                when (val outputIndex = encoder.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "AAC encoder changed output format more than once." }
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val buffer = encoder.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(muxerStarted && trackIndex >= 0) { "AAC output format was not ready." }
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, buffer, info)
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        encoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            check(muxerStarted) { "The AAC encoder did not produce an audio track." }
            muxer.stop()
            muxerStarted = false
            check(output.length() > 1024L) { "The normalized audio file could not be created." }
        } finally {
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    private companion object {
        const val TARGET_SAMPLE_RATE = 44_100
        const val TIMEOUT_US = 10_000L
        const val MONO_TO_STEREO_COMPENSATION_DB = 3.0102999566
    }

}
