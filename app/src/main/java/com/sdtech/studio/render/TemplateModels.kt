package com.sdtech.studio.render

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Template(
    val id: String,
    val name: String,
    val category: String,
    val supportedRatios: List<String>,
    val themes: List<TemplateTheme> = emptyList(),
    val layers: List<TemplateLayer>
)

@Serializable
data class TemplateTheme(
    val id: String,
    val name: String,
    val colors: Map<String, String>
)

@Serializable
sealed class TemplateLayer {
    abstract val id: String
    abstract val x: Float
    abstract val y: Float
    abstract val width: Float
    abstract val height: Float
    abstract val ratioOverrides: Map<String, LayerBounds>

    @Serializable @SerialName("background")
    data class Background(
        override val id: String = "background",
        override val x: Float = 0f, override val y: Float = 0f,
        override val width: Float = 100f, override val height: Float = 100f,
        val kind: BackgroundKind = BackgroundKind.SOLID,
        val color: String = "#F3F5F8",
        val endColor: String = "#101C2C",
        val imageAsset: String? = null,
        override val ratioOverrides: Map<String, LayerBounds> = emptyMap()
    ) : TemplateLayer()

    @Serializable @SerialName("shape")
    data class Shape(
        override val id: String,
        override val x: Float, override val y: Float,
        override val width: Float, override val height: Float,
        val shape: ShapeKind,
        val color: String = "#16A6A0",
        val strokeColor: String? = null,
        val strokeWidth: Float = 0f,
        val cornerRadius: Float = 0f,
        override val ratioOverrides: Map<String, LayerBounds> = emptyMap()
    ) : TemplateLayer()

    @Serializable @SerialName("text")
    data class Text(
        override val id: String,
        override val x: Float, override val y: Float,
        override val width: Float, override val height: Float,
        val role: TextRole,
        val text: String,
        val maxLines: Int = 2,
        val autoFit: Boolean = true,
        val alignment: TextAlignment = TextAlignment.START,
        val color: String = "#101C2C",
        val sizeSp: Float = 48f,
        val font: FontFamily = FontFamily.HEADING,
        val bold: Boolean = false,
        override val ratioOverrides: Map<String, LayerBounds> = emptyMap()
    ) : TemplateLayer()

    @Serializable @SerialName("image_slot")
    data class ImageSlot(
        override val id: String,
        override val x: Float, override val y: Float,
        override val width: Float, override val height: Float,
        val slot: String,
        val fit: ImageFit = ImageFit.CROP,
        val darkOverlay: Float = 0f,
        override val ratioOverrides: Map<String, LayerBounds> = emptyMap()
    ) : TemplateLayer()

    @Serializable @SerialName("logo_slot")
    data class LogoSlot(
        override val id: String = "logo",
        override val x: Float = 0f, override val y: Float = 0f,
        override val width: Float = 14f, override val height: Float = 14f,
        val placement: LogoPlacementMode = LogoPlacementMode.SAFE,
        val logoType: String = "FULL",
        val placementOverrides: Map<String, LogoPlacementOverride> = emptyMap(),
        override val ratioOverrides: Map<String, LayerBounds> = emptyMap()
    ) : TemplateLayer()

    @Serializable @SerialName("contact_line")
    data class ContactLine(
        override val id: String,
        override val x: Float, override val y: Float,
        override val width: Float, override val height: Float,
        val includeWebsite: Boolean = true,
        val includeWhatsapp: Boolean = true,
        val includeHandle: Boolean = true,
        val color: String = "#FFFFFF",
        val sizeSp: Float = 22f,
        override val ratioOverrides: Map<String, LayerBounds> = emptyMap()
    ) : TemplateLayer()
}

@Serializable enum class BackgroundKind { SOLID, GRADIENT, IMAGE }
@Serializable enum class ShapeKind { RECTANGLE, ROUNDED_RECTANGLE, LINE, CIRCLE }
@Serializable enum class TextRole { HEADING, BODY, LABEL, CTA }
@Serializable enum class TextAlignment { START, CENTER, END }
@Serializable enum class FontFamily { HEADING, BODY, MONO }
@Serializable enum class ImageFit { FIT, CROP }
@Serializable enum class LogoPlacementMode { SAFE, TIGHT }

@Serializable
data class LayerBounds(val x: Float, val y: Float, val width: Float, val height: Float)

@Serializable
data class LogoPlacementOverride(val width: Float, val rightMargin: Float, val topMargin: Float)

data class RatioProfile(
    val ratio: String,
    val width: Int,
    val height: Int,
    val safeLeft: Float,
    val safeTop: Float,
    val safeRight: Float,
    val safeBottom: Float
) {
    companion object {
        val ALL = listOf(
            RatioProfile("1:1", 1080, 1080, .06f, .06f, .06f, .06f),
            RatioProfile("4:5", 1080, 1350, .06f, .06f, .06f, .06f),
            RatioProfile("9:16", 1080, 1920, .06f, .08f, .06f, .08f),
            RatioProfile("16:9", 1920, 1080, .05f, .06f, .05f, .06f)
        )
        fun forRatio(ratio: String): RatioProfile = ALL.firstOrNull { it.ratio == ratio }
            ?: throw IllegalArgumentException("Unsupported canvas ratio: $ratio")
    }
}

data class LogoPlacement(val x: Float, val y: Float, val width: Float, val height: Float)

object LogoPlacementRules {
    fun forRatio(ratio: String, mode: LogoPlacementMode, logoAspectRatio: Float = 1f): LogoPlacement {
        val (widthPct, rightPct, topPct) = when (ratio) {
            "9:16" -> Triple(14f, 6f, if (mode == LogoPlacementMode.SAFE) 13f else 4f)
            "4:5" -> Triple(16f, 4f, 4f)
            "1:1" -> Triple(15f, 4f, 4f)
            "16:9" -> Triple(11f, 3f, if (mode == LogoPlacementMode.SAFE) 4f else 3f)
            else -> throw IllegalArgumentException("Unsupported logo ratio: $ratio")
        }
        val profile = RatioProfile.forRatio(ratio)
        val w = profile.width * widthPct / 100f
        val h = if (logoAspectRatio > 0f) w / logoAspectRatio else w
        return LogoPlacement(profile.width * (100f - rightPct) / 100f - w,
            profile.height * topPct / 100f, w, h)
    }
}

data class TemplateContent(
    val values: Map<String, String> = emptyMap(),
    val images: Map<String, android.graphics.Bitmap> = emptyMap(),
    val imageFits: Map<String, ImageFit> = emptyMap(),
    val imageOverlays: Map<String, Float> = emptyMap(),
    val textPresets: Map<String, String> = emptyMap(),
    val brandLock: Boolean = true
)

data class RenderOptions(
    val showSafeZones: Boolean = false,
    val logoPlacementMode: LogoPlacementMode? = null,
    val backgroundColor: Int? = null,
    val logoTypeOverride: String? = null,
    val brandColors: Map<String, String> = emptyMap()
)
