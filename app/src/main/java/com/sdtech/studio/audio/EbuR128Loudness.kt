package com.sdtech.studio.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** EBU R128 / ITU-R BS.1770 integrated loudness analysis for mono programme audio. */
internal object EbuR128Loudness {
    data class Measurement(val integratedLufs: Double, val truePeakDbtp: Double)
    data class GainPlan(val gainDb: Double, val predictedLufs: Double, val predictedTruePeakDbtp: Double, val peakLimited: Boolean, val boostLimited: Boolean)

    fun measure(input: FloatArray, sampleRate: Int): Measurement {
        require(sampleRate >= 8_000) { "Unsupported sample rate" }
        require(input.size >= sampleRate * 2 / 5) { "Record at least 0.4 seconds for reliable loudness analysis." }
        val shelf = highShelf(sampleRate, 1681.974, 3.999843, 0.707175)
        val rlb = highPass(sampleRate, 38.135, 0.500327)
        var shelfState = BiquadState()
        var rlbState = BiquadState()
        val weighted = FloatArray(input.size)
        for (i in input.indices) {
            val first = shelf.process(input[i].toDouble(), shelfState).also { shelfState = it.second }
            val second = rlb.process(first.first, rlbState)
            rlbState = second.second
            weighted[i] = second.first.toFloat()
        }

        val blockSize = (sampleRate * 0.400).toInt().coerceAtLeast(1)
        val step = (sampleRate * 0.100).toInt().coerceAtLeast(1)
        val energies = ArrayList<Double>()
        var start = 0
        while (start + blockSize <= weighted.size) {
            var sum = 0.0
            for (i in start until start + blockSize) {
                val v = weighted[i].toDouble()
                sum += v * v
            }
            energies += sum / blockSize
            start += step
        }
        if (energies.isEmpty()) {
            var sum = 0.0
            weighted.forEach { sum += it.toDouble() * it.toDouble() }
            energies += sum / weighted.size
        }

        fun lufs(energy: Double): Double = if (energy <= 1e-20) Double.NEGATIVE_INFINITY else -0.691 + 10.0 * log10(energy)
        val absoluteGate = energies.filter { lufs(it) >= -70.0 }
        require(absoluteGate.isNotEmpty()) { "The recording is too quiet to measure reliably. Record closer to the microphone." }
        val preliminaryMean = absoluteGate.average()
        val relativeThreshold = lufs(preliminaryMean) - 10.0
        val gated = absoluteGate.filter { lufs(it) >= relativeThreshold }
        require(gated.isNotEmpty()) { "Could not calculate integrated loudness for this recording." }
        val integrated = lufs(gated.average())
        val truePeak = measureTruePeak(input)
        return Measurement(integrated, 20.0 * log10(truePeak.coerceAtLeast(1e-10)))
    }

    /** Static gain is lossless in the PCM domain. Peak constraint leaves AAC encoder headroom. */
    fun plan(measurement: Measurement, targetLufs: Double = -16.0, maxTruePeakDbtp: Double = -1.5, maxBoostDb: Double = 18.0): GainPlan {
        val loudnessGain = targetLufs - measurement.integratedLufs
        val peakGain = maxTruePeakDbtp - measurement.truePeakDbtp
        val gain = min(min(loudnessGain, peakGain), maxBoostDb)
        return GainPlan(
            gainDb = gain,
            predictedLufs = measurement.integratedLufs + gain,
            predictedTruePeakDbtp = measurement.truePeakDbtp + gain,
            peakLimited = peakGain < loudnessGain,
            boostLimited = maxBoostDb < min(loudnessGain, peakGain)
        )
    }

    fun applyGain(input: FloatArray, gainDb: Double): FloatArray {
        val multiplier = pow10(gainDb / 20.0)
        return FloatArray(input.size) { i -> (input[i].toDouble() * multiplier).coerceIn(-1.0, 1.0).toFloat() }
    }

    /** Four-phase, 24-tap windowed-sinc oversampling estimate of inter-sample peak. */
    private fun measureTruePeak(samples: FloatArray): Double {
        var peak = 0.0
        val half = 12
        for (n in samples.indices) {
            peak = max(peak, abs(samples[n].toDouble()))
            for (phase in 1..3) {
                val fraction = phase / 4.0
                var value = 0.0
                var weightSum = 0.0
                for (tap in -half + 1..half) {
                    val index = n + tap
                    if (index !in samples.indices) continue
                    val x = tap - fraction
                    val sinc = if (abs(x) < 1e-12) 1.0 else sin(PI * x) / (PI * x)
                    val windowPosition = (x / half).coerceIn(-1.0, 1.0)
                    val window = 0.42 + 0.5 * cos(PI * windowPosition) + 0.08 * cos(2.0 * PI * windowPosition)
                    val weight = sinc * window
                    value += samples[index] * weight
                    weightSum += weight
                }
                if (abs(weightSum) > 1e-12) peak = max(peak, abs(value / weightSum))
            }
        }
        return peak
    }

    private data class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        fun process(x: Double, state: BiquadState): Pair<Double, BiquadState> {
            val y = b0 * x + b1 * state.x1 + b2 * state.x2 - a1 * state.y1 - a2 * state.y2
            return y to BiquadState(x, state.x1, y, state.y1)
        }
    }
    private data class BiquadState(val x1: Double = 0.0, val x2: Double = 0.0, val y1: Double = 0.0, val y2: Double = 0.0)

    private fun highPass(rate: Int, frequency: Double, q: Double): Biquad {
        val k = kotlin.math.tan(PI * frequency / rate)
        val a0 = 1.0 + k / q + k * k
        return Biquad(
            1.0 / a0,
            -2.0 / a0,
            1.0 / a0,
            2.0 * (k * k - 1.0) / a0,
            (1.0 - k / q + k * k) / a0
        )
    }

    private fun highShelf(rate: Int, frequency: Double, gainDb: Double, q: Double): Biquad {
        val k = kotlin.math.tan(PI * frequency / rate)
        val vh = pow10(gainDb / 20.0)
        val vb = vh.pow(0.499666774155)
        val a0 = 1.0 + k / q + k * k
        return Biquad(
            (vh + vb * k / q + k * k) / a0,
            2.0 * (k * k - vh) / a0,
            (vh - vb * k / q + k * k) / a0,
            2.0 * (k * k - 1.0) / a0,
            (1.0 - k / q + k * k) / a0
        )
    }

    private fun Double.pow(exponent: Double) = exp(exponent * ln(this))
    private fun pow10(exponent: Double) = exp(exponent * ln(10.0))
}
