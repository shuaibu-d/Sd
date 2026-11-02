package com.sdtech.studio

import com.sdtech.studio.render.LogoPlacementMode
import com.sdtech.studio.render.LogoPlacementRules
import com.sdtech.studio.video.Scene
import com.sdtech.studio.video.ScriptSceneSplitter
import com.sdtech.studio.video.VideoDurationCalculator
import com.sdtech.studio.video.effectiveDurationMs
import org.junit.Assert.*
import org.junit.Test

class HardeningUnitTest {
    @Test fun logoPlacementIsInsideCanvasAndSafeModeIsLowerOnPortrait() {
        val safe = LogoPlacementRules.forRatio("9:16", LogoPlacementMode.SAFE, 2f)
        val tight = LogoPlacementRules.forRatio("9:16", LogoPlacementMode.TIGHT, 2f)
        assertTrue(safe.x >= 0f && safe.y >= 0f)
        assertTrue(safe.x + safe.width <= 1080f)
        assertTrue(safe.y > tight.y)
        assertEquals(safe.width, tight.width, 0.001f)
    }

    @Test fun scriptSplitterPreservesWordsAndCapsSceneSize() {
        val script = "First sentence has several words.\n\nSecond paragraph contains more useful words for a short video."
        val scenes = ScriptSceneSplitter.split(script)
        assertTrue(scenes.isNotEmpty())
        assertTrue(scenes.all { ScriptSceneSplitter.wordCount(it) <= ScriptSceneSplitter.MAX_WORDS_PER_SCENE })
        assertEquals(ScriptSceneSplitter.wordCount(script), scenes.sumOf { ScriptSceneSplitter.wordCount(it) })
    }

    @Test fun durationUsesManualOverrideVoiceTailAndEmptySceneFallback() {
        val scenes = listOf(Scene(manualDurationMs = 5_000L, voiceDurationMs = 2_000L), Scene(voiceDurationMs = 3_000L), Scene())
        assertEquals(5_000L, scenes[0].effectiveDurationMs)
        assertEquals(3_400L, scenes[1].effectiveDurationMs)
        assertEquals(8_400L + VideoDurationCalculator.EMPTY_SCENE_FALLBACK_MS, VideoDurationCalculator.totalDurationMs(scenes))
    }
}
