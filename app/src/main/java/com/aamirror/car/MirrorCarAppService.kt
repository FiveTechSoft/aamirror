package com.aamirror.car

import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class MirrorCarAppService : CarAppService() {

    companion object {
        private const val TAG = "MirrorCarAppService"
    }

    override fun createHostValidator(): HostValidator {
        Log.d(TAG, "createHostValidator: allowing known Android Auto hosts")
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        Log.d(TAG, "Creating car session")
        return MirrorCarSession()
    }
}
