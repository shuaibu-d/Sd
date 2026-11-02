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
}
