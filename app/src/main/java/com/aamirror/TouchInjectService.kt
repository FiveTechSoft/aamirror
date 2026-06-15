package com.aamirror

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/**
 * Injects touch events from car display back to phone screen.
 * Uses AccessibilityService.dispatchGesture() — the only non-root way
 * to inject touches on Android.
 */
class TouchInjectService : AccessibilityService() {

    companion object {
        private const val TAG = "TouchInjectService"

        private var instanceRef: WeakReference<TouchInjectService>? = null

        var instance: TouchInjectService?
            get() = instanceRef?.get()
            private set(value) { instanceRef = if (value != null) WeakReference(value) else null }

        fun isEnabled(): Boolean = instance != null
    }

    private var phoneWidth = 1440
    private var phoneHeight = 3088

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't consume accessibility events — gesture dispatch only
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        instance = null
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        Log.d(TAG, "Service unbound")
        return super.onUnbind(intent)
    }

    /**
     * Map car surface coordinates to phone screen coordinates and inject.
     *
     * @param carX X on car surface (pixels)
     * @param carY Y on car surface (pixels)
     * @param carWidth Car surface width (pixels)
     * @param carHeight Car surface height (pixels)
     * @param action MotionEvent action (ACTION_DOWN=0, ACTION_UP=1, ACTION_MOVE=2)
     */
    fun injectTouch(
        carX: Float, carY: Float,
        carWidth: Int, carHeight: Int,
        action: Int
    ) {
        if (carWidth <= 0 || carHeight <= 0) return
        val phoneX = (carX / carWidth) * phoneWidth
        val phoneY = (carY / carHeight) * phoneHeight

        val path = Path().apply {
            moveTo(phoneX, phoneY)
        }

        val duration = when (action) {
            0 /* ACTION_DOWN */ -> 0L
            1 /* ACTION_UP */   -> 30L
            else                -> 50L // move/drag
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val dispatched = dispatchGesture(gesture, null, null)
        if (!dispatched) {
            Log.w(TAG, "dispatchGesture returned false")
        }
    }

    fun updatePhoneDimensions(width: Int, height: Int) {
        phoneWidth = width
        phoneHeight = height
    }
}
