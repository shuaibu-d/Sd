package com.sdtech.studio.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class EbuR128LoudnessTest {
    private fun tone(amplitude: Double, seconds: Double = 2.0): FloatArray {
        val rate = 44_100
        return FloatArray((rate * seconds).toInt()) { index ->
            (amplitude * sin(2.0 * PI * 1_000.0 * index / rate)).toFloat()
        }
    }

    @Test fun integratedLoudnessTracksAmplitudeScaling() {
        val quiet = EbuR128Loudness.measure(tone(0.1), 44_100)
        val loud = EbuR128Loudness.measure(tone(0.2), 44_100)
        assertEquals(6.0206, loud.integratedLufs - quiet.integratedLufs, 0.15)
    }

    @Test fun kWeightingMatchesBs1770ReferenceForOneKilohertzTone() {
        val measured = EbuR128Loudness.measure(tone(0.1), 44_100)
        assertEquals(-23.05, measured.integratedLufs, 0.05)
        assertEquals(-20.0, measured.truePeakDbtp, 0.05)
    }

    @Test fun gainPlanRespectsTruePeakCeiling() {
        val measurement = EbuR128Loudness.Measurement(-30.0, -2.0)
        val plan = EbuR128Loudness.plan(measurement, -16.0, -1.5)
        assertEquals(0.5, plan.gainDb, 0.0001)
        assertTrue(plan.peakLimited)
        assertEquals(-1.5, plan.predictedTruePeakDbtp, 0.0001)
    }

    @Test fun lowLevelBoostIsBounded() {
        val plan = EbuR128Loudness.plan(EbuR128Loudness.Measurement(-55.0, -45.0))
        assertEquals(18.0, plan.gainDb, 0.0001)
        assertTrue(plan.boostLimited)
    }

    @Test fun media3AudioProcessorReachesMinus16LufsTargetAndMatchesLegacyGainPlan() {
        val input = tone(amplitude = 0.08, seconds = 2.0)
        // Reference calculation matching legacy AndroidAacLoudnessNormalizer stereo-compensated target (-16 LUFS, -1.5 dBTP)
        val rawMono = EbuR128Loudness.measure(input, 44_100)
        val stereoCompensated = rawMono.copy(
            integratedLufs = rawMono.integratedLufs + EbuR128NormalizationAudioProcessor.MONO_TO_STEREO_COMPENSATION_DB
        )
        val expectedPlan = EbuR128Loudness.plan(stereoCompensated, targetLufs = -16.0, maxTruePeakDbtp = -1.5)
        val expectedSamples = EbuR128Loudness.applyGain(input, expectedPlan.gainDb)

        // Process through Media3 EbuR128NormalizationAudioProcessor (two-pass auto-analysis mode)
        val (processed, summary) = EbuR128NormalizationAudioProcessor.processPcmFloatArray(
            input = input,
            sampleRate = 44_100,
            channelCount = 1,
            encoding = androidx.media3.common.C.ENCODING_PCM_FLOAT,
            targetLufs = -16.0,
            truePeakCeilingDbtp = -1.5,
            monoToStereoCompensation = true
        )

        assertEquals(expectedPlan.gainDb, summary.appliedGainDb, 0.001)
        assertEquals(-16.0, summary.outputLufsEstimate, 0.15)
        assertTrue("Output true peak must stay at or below -1.5 dBTP ceiling", summary.outputTruePeakEstimateDbtp <= -1.45)
        assertEquals(expectedSamples.size, processed.size)
        for (i in expectedSamples.indices step 64) {
            assertEquals(expectedSamples[i].toDouble(), processed[i].toDouble(), 1e-4)
        }
    }

    @Test fun media3AudioProcessorEnforcesTruePeakCeilingAndStreamingPrecomputedGainParity() {
        // Signal with low average loudness but a sharp transient spike near -2.0 dBTP
        val input = tone(amplitude = 0.04, seconds = 2.0)
        input[1000] = 0.794f // ~ -2.0 dBFS peak
        input[1001] = -0.794f

        val (twoPassOut, twoPassSummary) = EbuR128NormalizationAudioProcessor.processPcmFloatArray(
            input = input,
            sampleRate = 44_100,
            channelCount = 1,
            encoding = androidx.media3.common.C.ENCODING_PCM_16BIT,
            targetLufs = -16.0,
            truePeakCeilingDbtp = -1.5
        )
        assertTrue("Processor must mark take as peak-limited", twoPassSummary.peakLimited)
        assertTrue("Output true peak (${twoPassSummary.outputTruePeakEstimateDbtp}) must respect -1.5 dBTP ceiling",
            twoPassSummary.outputTruePeakEstimateDbtp <= -1.4)

        // Streaming mode with precomputedGainDb must produce identical samples
        val (streamOut, _) = EbuR128NormalizationAudioProcessor.processPcmFloatArray(
            input = input,
            sampleRate = 44_100,
            channelCount = 1,
            encoding = androidx.media3.common.C.ENCODING_PCM_16BIT,
            targetLufs = -16.0,
            truePeakCeilingDbtp = -1.5,
            precomputedGainDb = twoPassSummary.appliedGainDb
        )
        assertEquals(twoPassOut.size, streamOut.size)
        for (i in twoPassOut.indices step 64) {
            assertEquals(twoPassOut[i].toDouble(), streamOut[i].toDouble(), 1e-3)
        }
    }
}
