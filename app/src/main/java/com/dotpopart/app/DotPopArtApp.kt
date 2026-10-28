package com.dotpopart.app

import android.app.Application
import android.util.Log
import com.dotpopart.app.data.SettingsRepository
import com.dotpopart.app.device.DeviceInfo
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DotPopArtApp : Application() {
    lateinit var settings: SettingsRepository
        private set

    /** 画面より長く続けたい書き込み（✓ の保存など）に使う。失敗してもプロセスは落とさない。 */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e("DPA", "app task failed", e) },
    )

    override fun onCreate() {
        super.onCreate()
        DeviceInfo.init(this)
        settings = SettingsRepository(this)
    }
}
