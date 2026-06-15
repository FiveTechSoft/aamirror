package com.aamirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.graphics.ImageFormat
import com.aamirror.comm.CarBridge

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "aamirror_capture"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        @Volatile
        var isRunning = false
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var renderer: OpenGLRenderer? = null
    private var phoneWidth = 0
    private var phoneHeight = 0
    private var phoneDpi = 160
    private var downscale = 1.0f

    private val appMessenger = Messenger(
        CarBridge.AppIncomingHandler(Looper.getMainLooper())
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Return binder for Messenger IPC from car process
        return appMessenger.binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            return START_NOT_STICKY
        }

        if (intent.action == "STOP") {
            Log.d(TAG, "Stop action received")
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)

        if (resultCode == 0 || data == null) {
            Log.e(TAG, "Missing MediaProjection result")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        val captureStarted = startCapture(resultCode, data)

        if (!captureStarted) {
            Log.e(TAG, "Failed to start capture — stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        CarBridge.setSurfaceListener(object : CarBridge.SurfaceListener {
            override fun onSurfaceReady(surface: android.view.Surface, width: Int, height: Int, dpi: Int) {
                Log.d(TAG, "Surface ready: ${width}x${height}")
                renderer?.start(surface, width, height)
            }

            override fun onSurfaceDestroyed() {
                Log.d(TAG, "Surface destroyed — pausing renderer")
                renderer?.stop()
            }

            override fun onTouchEvent(x: Float, y: Float, action: Int) {
                val r = renderer
                TouchInjectService.instance?.injectTouch(
                    x, y,
                    r?.displayWidth ?: 1920,
                    r?.displayHeight ?: 1080,
                    action
                )
            }

            override fun onCarConnected() {
                Log.d(TAG, "Car connected")
            }

            override fun onCarDisconnected() {
                Log.d(TAG, "Car disconnected")
                renderer?.stop()
            }
        })

        isRunning = true
        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent): Boolean {
        val metrics = getPhoneMetrics()
        phoneWidth = metrics.first
        phoneHeight = metrics.second
        phoneDpi = metrics.third

        if (phoneWidth <= 0 || phoneHeight <= 0) {
            Log.e(TAG, "Invalid phone dimensions: ${phoneWidth}x${phoneHeight}")
            return false
        }

        val captureWidth = (phoneWidth * downscale).toInt()
        val captureHeight = (phoneHeight * downscale).toInt()

        Log.d(TAG, "Phone: ${phoneWidth}x${phoneHeight} @${phoneDpi}dpi -> Capture: ${captureWidth}x${captureHeight}")

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        if (mediaProjection == null) {
            Log.e(TAG, "getMediaProjection returned null")
            return false
        }

        captureThread = HandlerThread("Capture").apply { start() }
        captureHandler = Handler(captureThread!!.looper)

        // Android 14+ requires registering a Callback BEFORE createVirtualDisplay
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped by system")
            }
        }, captureHandler)

        imageReader = ImageReader.newInstance(
            captureWidth, captureHeight,
            ImageFormat.FLEX_RGBA_8888, 2
        )

        renderer = OpenGLRenderer()

        imageReader!!.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val planes = image.planes
            if (planes.isNotEmpty()) {
                val buffer = planes[0].buffer
                val rgba = ByteArray(buffer.remaining())
                buffer.get(rgba)
                val q = renderer?.frameQueue
                q?.clear()
                q?.offer(rgba)
            }
            image.close()
        }, captureHandler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AAMirror",
            captureWidth, captureHeight, phoneDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, null
        )

        if (virtualDisplay == null) {
            Log.e(TAG, "createVirtualDisplay returned null")
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
            captureThread?.quitSafely()
            captureThread = null
            captureHandler = null
            return false
        }

        TouchInjectService.instance?.updatePhoneDimensions(phoneWidth, phoneHeight)
        return true
    }

    private fun getPhoneMetrics(): Triple<Int, Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = wm.currentWindowMetrics
            val bounds = windowMetrics.bounds
            val density = resources.displayMetrics.densityDpi
            return Triple(bounds.width(), bounds.height(), density)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            return Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        }
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, ScreenCaptureService::class.java).apply {
                action = "STOP"
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        CarBridge.setSurfaceListener(null)
        renderer?.stop()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        captureThread?.quitSafely()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }
}
