package com.sdtech.studio.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PaletteColor(
    val name: String,
    val hex: String
)

enum class LogoType { FULL, ICON, LIGHT, DARK }
enum class ProjectType { POST, VIDEO }
enum class ProjectStatus { DRAFT, READY, EXPORTED }
enum class StudioDivision { SOFTWARE, AI, ACADEMY, DIGITAL, GENERAL }
enum class PlannerStatus { IDEA, DRAFT, READY, POSTED }

data class AppSettings(
    val themeMode: String = THEME_SYSTEM,
    val onboardingCompleted: Boolean = false,
    val lastUsedRatio: String = "1:1",
    val defaultLogoMode: String = "SAFE"
) {
    companion object {
        const val THEME_SYSTEM = "SYSTEM"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_DARK = "DARK"
    }
}
