package io.ronesec.android.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.View
import android.view.WindowManager

sealed interface OverlayAttachResult {
    data object Success : OverlayAttachResult
    data class Failed(val error: String) : OverlayAttachResult
}

interface OverlayBackend {
    fun addView(view: View, params: WindowManager.LayoutParams): OverlayAttachResult
    fun removeView(view: View)
}

class ApplicationOverlayBackend(
    private val context: Context
) : OverlayBackend {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    override fun addView(view: View, params: WindowManager.LayoutParams): OverlayAttachResult {
        return try {
            windowManager.addView(view, params)
            OverlayAttachResult.Success
        } catch (e: Exception) {
            OverlayAttachResult.Failed(e.message ?: "Failed to add application overlay view")
        }
    }

    override fun removeView(view: View) {
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {}
    }
}

class AccessibilityOverlayBackend(
    private val service: AccessibilityService
) : OverlayBackend {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    override fun addView(view: View, params: WindowManager.LayoutParams): OverlayAttachResult {
        return try {
            params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            windowManager.addView(view, params)
            OverlayAttachResult.Success
        } catch (e: Exception) {
            OverlayAttachResult.Failed(e.message ?: "Failed to add accessibility overlay view")
        }
    }

    override fun removeView(view: View) {
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {}
    }
}
