package com.dotpopart.app.glyph

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2

/**
 * アプリ連動のマップの方位。回転ベクトルセンサー（`TYPE_ROTATION_VECTOR`。許可は要らない）から、
 * 北が背面のマトリクスのどちらにあるか（12 時が 0、時計回りの度）を求める。
 * マトリクスは背面から見た向きなので、画面側から見た向きの左右を反転する。
 * アニメーションを出している間だけ登録する。
 */
class CueCompass(context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val sensor = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotation = FloatArray(9)
    private var on = false

    /** 北の向き（度）。 */
    @Volatile var heading = 0.0
        private set

    fun start() {
        if (on || sensor == null) return
        on = true
        sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!on) return
        on = false
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        // 北（世界の y 軸）を端末の座標で表すと、回転行列の2行目になる
        val nx = rotation[3]
        val ny = rotation[4]
        // 端末を立てていて、北がマトリクスの面にほとんど乗らないときは、前の向きのままにする
        if (nx * nx + ny * ny < 0.01f) return
        // マトリクスの右は端末の −x（背面から見るため）、上は端末の +y
        heading = (Math.toDegrees(atan2(-nx.toDouble(), ny.toDouble())) + 360) % 360
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
