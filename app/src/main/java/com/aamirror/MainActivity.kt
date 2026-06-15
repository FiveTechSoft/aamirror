package com.aamirror

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var statusText: TextView
    private lateinit var startStopButton: Button
    private lateinit var permissionStatus: TextView
    private lateinit var carStatusText: TextView

    private var isCapturing = false

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startCaptureService(result.resultCode, result.data!!)
        } else {
            statusText.text = getString(R.string.status_error).format("Screen capture permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        startStopButton = findViewById(R.id.startStopButton)
        permissionStatus = findViewById(R.id.permissionStatus)
        carStatusText = findViewById(R.id.carStatus)

        startStopButton.setOnClickListener {
            if (isCapturing) {
                stopCapture()
            } else {
                checkPermissionsAndStart()
            }
        }

        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        isCapturing = ScreenCaptureService.isRunning
        updateCarStatus()
        updateUI()
    }

    private fun checkPermissionsAndStart() {
        // Check SYSTEM_ALERT_WINDOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            statusText.text = getString(R.string.status_error).format("Grant overlay permission first")
            return
        }

        // Check Accessibility Service
        if (!isAccessibilityServiceEnabled()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            statusText.text = getString(R.string.status_error).format("Enable accessibility service first")
            return
        }

        // Check POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
                statusText.text = getString(R.string.status_error).format("Grant notification permission first")
                return
            }
        }

        // Request MediaProjection
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = projectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(intent)
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_DATA, data)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isCapturing = true
            updateUI()
        } catch (e: Exception) {
            statusText.text = getString(R.string.status_error).format(e.message)
        }
    }

    private fun stopCapture() {
        val intent = Intent(this, ScreenCaptureService::class.java)
        stopService(intent)
        isCapturing = false
        updateUI()
    }

    private fun updateUI() {
        if (isCapturing) {
            statusText.setText(R.string.status_capturing)
            startStopButton.setText(R.string.btn_stop)
        } else {
            statusText.setText(R.string.status_idle)
            startStopButton.setText(R.string.btn_start)
        }
    }

    private fun updatePermissionStatus() {
        val missing = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            missing.add(getString(R.string.perm_overlay_missing))
        }

        if (!isAccessibilityServiceEnabled()) {
            missing.add(getString(R.string.perm_accessibility_missing))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missing.add(getString(R.string.perm_notifications_missing))
            }
        }

        permissionStatus.text = if (missing.isEmpty()) {
            getString(R.string.perm_all_granted)
        } else {
            missing.joinToString("\n")
        }
    }

    private fun updateCarStatus() {
        if (ScreenCaptureService.isRunning) {
            carStatusText.text = getString(R.string.car_status_connected)
        } else {
            carStatusText.text = getString(R.string.car_status_waiting)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any { info ->
            info.resolveInfo.serviceInfo.packageName == packageName &&
            info.resolveInfo.serviceInfo.name == TouchInjectService::class.java.name
        }
    }
}
