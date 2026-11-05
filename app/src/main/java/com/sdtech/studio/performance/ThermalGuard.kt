package com.sdtech.studio.performance

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

enum class ThermalState(
    val previewScale: Float,
    val shouldWarnBeforeExport: Boolean,
    val label: String
) {
    NOMINAL(previewScale = 1.0f, shouldWarnBeforeExport = false, label = "Nominal"),
    WARM(previewScale = 0.85f, shouldWarnBeforeExport = false, label = "Warm"),
    HOT(previewScale = 0.65f, shouldWarnBeforeExport = true, label = "Hot"),
    CRITICAL(previewScale = 0.5f, shouldWarnBeforeExport = true, label = "Critical")
}

/**
 * Monitors device thermal status via [PowerManager]. When the phone is hot,
 * lowers preview quality automatically and warns before starting an export.
 */
object ThermalGuard {
    private val _thermalState = MutableStateFlow(ThermalState.NOMINAL)
    val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()
    val status: StateFlow<ThermalState> get() = thermalState

    fun install(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            onPlatformThermalStatusChanged(pm.currentThermalStatus)
            runCatching {
                pm.addThermalStatusListener(context.mainExecutor) { status ->
                    onPlatformThermalStatusChanged(status)
                }
            }
        }
    }

    fun onPlatformThermalStatusChanged(platformStatus: Int) {
        val next = when {
            platformStatus >= PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.CRITICAL
            platformStatus >= PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.HOT
            platformStatus >= PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.WARM
            else -> ThermalState.NOMINAL
        }
        _thermalState.value = next
        if (next == ThermalState.HOT || next == ThermalState.CRITICAL) {
            CentralBitmapPool.trimToFraction(0.5f)
        }
    }

    fun updateFromPowerThermalStatus(platformStatus: Int) = onPlatformThermalStatusChanged(platformStatus)

    val isDeviceHot: Boolean
        get() = _thermalState.value.shouldWarnBeforeExport

    val shouldWarnBeforeExport: Boolean
        get() = _thermalState.value.shouldWarnBeforeExport

    fun exportThermalWarningMessage(): String =
        "Your phone is currently warm (${_thermalState.value.label}). Preview resolution has been lowered automatically. Exporting now may run slower or warm your phone further."

    fun resetForTesting() {
        _thermalState.value = ThermalState.NOMINAL
    }

    /**
     * Combined preview scale factor factoring in both [MemoryGuard] and [ThermalGuard].
     */
    val effectivePreviewScale: Float
        get() = min(MemoryGuard.pressureLevel.value.previewScale, _thermalState.value.previewScale)

    val effectiveGalleryThumbnailWidthPx: Int
        get() = (420 * effectivePreviewScale).toInt().coerceAtLeast(180)

    val effectiveQuickPreviewWidthPx: Int
        get() = (540 * effectivePreviewScale).toInt().coerceAtLeast(240)

    val effectiveEditorMaxEdgePx: Int
        get() = (1080 * effectivePreviewScale).toInt().coerceAtLeast(480)
}
