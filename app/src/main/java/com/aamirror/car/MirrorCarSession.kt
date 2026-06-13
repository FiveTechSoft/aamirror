package com.aamirror.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import android.view.MotionEvent
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aamirror.comm.CarBridge

class MirrorCarSession : Session() {

    companion object {
        private const val TAG = "MirrorCarSession"
    }

    private var carMessenger: Messenger? = null
    private var appBound = false
    private var currentSurfaceContainer: SurfaceContainer? = null

    private val carHandler = Messenger(
        CarBridge.CarIncomingHandler(Looper.getMainLooper())
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Bound to ScreenCaptureService")
            carMessenger = Messenger(service)
            CarBridge.carToAppMessenger = carMessenger
            appBound = true

            // If surface already available, send it now
            currentSurfaceContainer?.let { sendSurfaceReady(it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "ScreenCaptureService disconnected")
            appBound = false
            carMessenger = null
            CarBridge.carToAppMessenger = null
        }
    }

    override fun onCreateScreen(intent: Intent): Screen {
        Log.d(TAG, "Creating screen")

        // Bind to ScreenCaptureService in main process (same APK)
        val bindIntent = Intent(carContext, com.aamirror.ScreenCaptureService::class.java)
        carContext.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Observe lifecycle to unbind when session is destroyed
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY && appBound) {
                carContext.unbindService(serviceConnection)
                appBound = false
                Log.d(TAG, "Unbound from ScreenCaptureService")
            }
        })

        return MirrorScreen(carContext)
    }

    private fun sendSurfaceReady(container: SurfaceContainer) {
        val messenger = carMessenger ?: return
        try {
            val msg = Message.obtain(null, CarBridge.MSG_SURFACE_READY).apply {
                obj = container.surface
                arg1 = container.width
                arg2 = container.height
            }
            messenger.send(msg)
            Log.d(TAG, "Sent surface to main process: ${container.width}x${container.height}")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to send surface", e)
        }
    }

    private fun sendTouchEvent(x: Float, y: Float, action: Int) {
        val messenger = carMessenger ?: return
        try {
            val msg = Message.obtain(null, CarBridge.MSG_TOUCH_EVENT).apply {
                arg1 = x.toInt()
                arg2 = y.toInt()
                data = Bundle().apply { putInt("action", action) }
            }
            messenger.send(msg)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to send touch", e)
        }
    }

    inner class MirrorScreen(carContext: androidx.car.app.CarContext) : Screen(carContext) {

        private val surfaceCallback = object : SurfaceCallback {
            override fun onSurfaceAvailable(container: SurfaceContainer) {
                Log.d(TAG, "AA Surface available: ${container.width}x${container.height}")
                currentSurfaceContainer = container
                if (appBound) {
                    sendSurfaceReady(container)
                }
            }

            override fun onSurfaceDestroyed(container: SurfaceContainer) {
                Log.d(TAG, "AA Surface destroyed")
                currentSurfaceContainer = null
                carMessenger?.let {
                    try {
                        it.send(Message.obtain(null, CarBridge.MSG_SURFACE_DESTROYED))
                    } catch (e: RemoteException) {
                        Log.e(TAG, "Failed to send destroy", e)
                    }
                }
            }

            override fun onVisibleAreaChanged(rect: Rect) {
                // Not needed for mirroring
            }

            override fun onStableAreaChanged(rect: Rect) {
                // Not needed for mirroring
            }

            override fun onClick(x: Float, y: Float) {
                Log.d(TAG, "onClick: $x, $y")
                sendTouchEvent(x, y, MotionEvent.ACTION_DOWN)
                Handler(Looper.getMainLooper()).postDelayed({
                    sendTouchEvent(x, y, MotionEvent.ACTION_UP)
                }, 50)
            }

            override fun onFling(velocityX: Float, velocityY: Float) {
                Log.d(TAG, "onFling: $velocityX, $velocityY")
                val carW = currentSurfaceContainer?.width ?: 1920
                val carH = currentSurfaceContainer?.height ?: 1080
                val startX = carW / 2f - velocityX / 10f
                val startY = carH / 2f - velocityY / 10f
                val endX = carW / 2f + velocityX / 10f
                val endY = carH / 2f + velocityY / 10f
                sendTouchEvent(startX, startY, MotionEvent.ACTION_DOWN)
                sendTouchEvent(endX, endY, MotionEvent.ACTION_MOVE)
                sendTouchEvent(endX, endY, MotionEvent.ACTION_UP)
            }

            override fun onScroll(distanceX: Float, distanceY: Float) {
                Log.d(TAG, "onScroll: $distanceX, $distanceY")
                sendTouchEvent(
                    (currentSurfaceContainer?.width ?: 1920) / 2f,
                    (currentSurfaceContainer?.height ?: 1080) / 2f,
                    MotionEvent.ACTION_MOVE
                )
            }

            override fun onScale(x: Float, y: Float, scale: Float) {
                // Not needed for mirroring
            }
        }

        init {
            // Register SurfaceCallback through AppManager (the way surfaces work in v1.4.0)
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
        }

        override fun onGetTemplate(): Template {
            return PaneTemplate.Builder(
                Pane.Builder()
                    .addRow(
                        Row.Builder()
                            .setTitle("AA Mirror")
                            .addText("Phone screen mirroring active")
                            .build()
                    )
                    .build()
            ).build()
        }
    }
}
