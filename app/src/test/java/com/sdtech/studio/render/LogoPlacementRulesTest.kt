package com.sdtech.studio.render

import org.junit.Assert.assertEquals
import org.junit.Test

class LogoPlacementRulesTest {
    private fun assertPlacement(ratio: String, mode: LogoPlacementMode,
                                widthPercent: Float, rightPercent: Float, topPercent: Float) {
        val profile = RatioProfile.forRatio(ratio)
        val result = LogoPlacementRules.forRatio(ratio, mode)
        assertEquals(profile.width * widthPercent / 100f, result.width, 0.01f)
        assertEquals(profile.width * rightPercent / 100f,
            profile.width - result.x - result.width, 0.01f)
        assertEquals(profile.height * topPercent / 100f, result.y, 0.01f)
    }

    @Test fun portraitSafeAndTightPositions() {
        assertPlacement("9:16", LogoPlacementMode.SAFE, 14f, 6f, 13f)
        assertPlacement("9:16", LogoPlacementMode.TIGHT, 14f, 6f, 4f)
    }

    @Test fun portraitFourByFivePosition() {
        assertPlacement("4:5", LogoPlacementMode.SAFE, 16f, 4f, 4f)
        assertPlacement("4:5", LogoPlacementMode.TIGHT, 16f, 4f, 4f)
    }

    @Test fun squarePosition() {
        assertPlacement("1:1", LogoPlacementMode.SAFE, 15f, 4f, 4f)
        assertPlacement("1:1", LogoPlacementMode.TIGHT, 15f, 4f, 4f)
    }

    @Test fun landscapeSafeAndTightPositions() {
        assertPlacement("16:9", LogoPlacementMode.SAFE, 11f, 3f, 4f)
        assertPlacement("16:9", LogoPlacementMode.TIGHT, 11f, 3f, 3f)
    }

    @Test fun logoAspectRatioControlsHeight() {
        val wide = LogoPlacementRules.forRatio("1:1", LogoPlacementMode.SAFE, logoAspectRatio = 2f)
        assertEquals(wide.width / 2f, wide.height, 0.01f)
    }
}
