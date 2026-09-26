package com.dotpopart.app

import android.app.Application
import com.dotpopart.app.data.SettingsRepository
import com.dotpopart.app.device.DeviceInfo

class DotPopArtApp : Application() {
    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        DeviceInfo.init(this)
        settings = SettingsRepository(this)
    }
}
