package com.aamirror.comm

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.view.Surface

/**
 * Cross-process communication between app process (:main) and car process.
 * Uses Messenger + Handler. Surface is Parcelable so passes across process boundary.
 */
object CarBridge {

    private const val TAG = "CarBridge"

    const val MSG_SURFACE_READY = 1
    const val MSG_SURFACE_DESTROYED = 2
    const val MSG_TOUCH_EVENT = 3        // arg1=x, arg2=y, data bundle has "action"
    const val MSG_CAR_CONNECTED = 4
    const val MSG_CAR_DISCONNECTED = 5

    // Messenger from car process → main process
    @Volatile
    var carToAppMessenger: Messenger? = null
        internal set

    // Messenger from main process → car process
    @Volatile
    var appToCarMessenger: Messenger? = null
        internal set

    // Callbacks for main process
    interface SurfaceListener {
        fun onSurfaceReady(surface: Surface, width: Int, height: Int, dpi: Int)
        fun onSurfaceDestroyed()
        fun onTouchEvent(x: Float, y: Float, action: Int)
        fun onCarConnected()
        fun onCarDisconnected()
    }

    @Volatile
    private var surfaceListener: SurfaceListener? = null

    fun setSurfaceListener(listener: SurfaceListener?) {
        surfaceListener = listener
    }

    /** Handler in main process: receives messages from car process */
    class AppIncomingHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_SURFACE_READY -> {
                    val surface = msg.obj as? Surface
                    val width = msg.arg1
                    val height = msg.arg2
                    val dpi = msg.data?.getInt("dpi", 160) ?: 160
                    if (surface != null) {
                        Log.d(TAG, "Surface received: ${width}x${height} @${dpi}dpi")
                        surfaceListener?.onSurfaceReady(surface, width, height, dpi)
                    }
                }
                MSG_SURFACE_DESTROYED -> {
                    Log.d(TAG, "Surface destroyed")
                    surfaceListener?.onSurfaceDestroyed()
                }
                MSG_TOUCH_EVENT -> {
                    val data = msg.data
                    val x = data?.getFloat("x", 0f) ?: msg.arg1.toFloat()
                    val y = data?.getFloat("y", 0f) ?: msg.arg2.toFloat()
                    val action = data?.getInt("action", 0) ?: 0
                    surfaceListener?.onTouchEvent(x, y, action)
                }
                MSG_CAR_CONNECTED -> {
                    Log.d(TAG, "Car connected")
                    surfaceListener?.onCarConnected()
                }
                MSG_CAR_DISCONNECTED -> {
                    Log.d(TAG, "Car disconnected")
                    surfaceListener?.onCarDisconnected()
                }
            }
        }
    }

    /** Handler in car process: receives messages from main process */
    class CarIncomingHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            // Reserved for future use (e.g., main→car status updates)
            Log.d(TAG, "Car process received msg: ${msg.what}")
        }
    }
}
