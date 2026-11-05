package com.sdtech.studio.performance

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MemoryPressureLevel(
    val previewScale: Float,
    val galleryThumbnailWidthPx: Int,
    val quickPreviewWidthPx: Int,
    val editorMaxEdgePx: Int
) {
    NORMAL(
        previewScale = 1.0f,
        galleryThumbnailWidthPx = 420,
        quickPreviewWidthPx = 540,
        editorMaxEdgePx = 1080
    ),
    MODERATE(
        previewScale = 0.75f,
        galleryThumbnailWidthPx = 300,
        quickPreviewWidthPx = 400,
        editorMaxEdgePx = 720
    ),
    CRITICAL(
        previewScale = 0.5f,
        galleryThumbnailWidthPx = 220,
        quickPreviewWidthPx = 280,
        editorMaxEdgePx = 540
    )
}

/**
 * Monitors system memory pressure via [ComponentCallbacks2.onTrimMemory] and
 * [ActivityManager.isLowRamDevice]. On pressure, trims or clears [CentralBitmapPool]
 * and lowers preview resolution automatically.
 */
object MemoryGuard : ComponentCallbacks2 {
    private val _pressureLevel = MutableStateFlow(MemoryPressureLevel.NORMAL)
    val pressureLevel: StateFlow<MemoryPressureLevel> = _pressureLevel.asStateFlow()

    private val _isLowRamDevice = MutableStateFlow(false)
    val isLowRamDevice: StateFlow<Boolean> = _isLowRamDevice.asStateFlow()

    fun install(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = am?.isLowRamDevice == true
        applyLowRamFlag(lowRam)
        appContext.registerComponentCallbacks(this)
    }

    fun applyLowRamFlag(lowRam: Boolean) {
        _isLowRamDevice.value = lowRam
        if (lowRam && _pressureLevel.value == MemoryPressureLevel.NORMAL) {
            _pressureLevel.value = MemoryPressureLevel.MODERATE
            CentralBitmapPool.trimToFraction(0.5f)
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                _pressureLevel.value = MemoryPressureLevel.CRITICAL
                CentralBitmapPool.evictAll()
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                _pressureLevel.value = MemoryPressureLevel.MODERATE
                CentralBitmapPool.trimToFraction(0.5f)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                if (_isLowRamDevice.value) {
                    _pressureLevel.value = MemoryPressureLevel.MODERATE
                }
                CentralBitmapPool.trimToFraction(0.5f)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        _pressureLevel.value = MemoryPressureLevel.CRITICAL
        CentralBitmapPool.evictAll()
    }

    fun resetForNormalConditions() {
        _pressureLevel.value = if (_isLowRamDevice.value) {
            MemoryPressureLevel.MODERATE
        } else {
            MemoryPressureLevel.NORMAL
        }
    }

    fun resetForTesting() {
        _isLowRamDevice.value = false
        _pressureLevel.value = MemoryPressureLevel.NORMAL
    }
}
