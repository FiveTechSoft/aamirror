package com.aamirror.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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
    private val clickHandler = Handler(Looper.getMainLooper())

    private val carHandler = Messenger(
        CarBridge.CarIncomingHandler(Looper.getMainLooper())
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Bound to ScreenCaptureService")
            carMessenger = Messenger(service)
            CarBridge.carToAppMessenger = carMessenger
            appBound = true

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

        val bindIntent = Intent(carContext, com.aamirror.ScreenCaptureService::class.java)
        val bound = carContext.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            Log.e(TAG, "Failed to bind to ScreenCaptureService")
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY && appBound) {
                try {
                    carContext.unbindService(serviceConnection)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "unbindService failed", e)
                }
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
                data = Bundle().apply { putInt("dpi", container.dpi) }
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
                data = Bundle().apply {
                    putFloat("x", x)
                    putFloat("y", y)
                    putInt("action", action)
                }
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

            override fun onVisibleAreaChanged(rect: android.graphics.Rect) {}
            override fun onStableAreaChanged(rect: android.graphics.Rect) {}
            override fun onClick(x: Float, y: Float) {
                Log.d(TAG, "onClick: $x, $y")
                sendTouchEvent(x, y, MotionEvent.ACTION_DOWN)
                clickHandler.postDelayed({
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
                clickHandler.postDelayed({
                    sendTouchEvent(endX, endY, MotionEvent.ACTION_MOVE)
                    clickHandler.postDelayed({
                        sendTouchEvent(endX, endY, MotionEvent.ACTION_UP)
                    }, 16)
                }, 16)
            }

            override fun onScroll(distanceX: Float, distanceY: Float) {
                Log.d(TAG, "onScroll: $distanceX, $distanceY")
                val carW = currentSurfaceContainer?.width ?: 1920
                val carH = currentSurfaceContainer?.height ?: 1080
                sendTouchEvent(carW / 2f - distanceX, carH / 2f - distanceY, MotionEvent.ACTION_MOVE)
            }

            override fun onScale(x: Float, y: Float, scale: Float) {}
        }

        init {
            // SurfaceCallback DISABLED for testing — Google may block apps using this API
            // from appearing in the AA launcher on production hosts (v16.9+).
            // If the app appears without this line, SurfaceCallback is the blocker.
            // carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
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
