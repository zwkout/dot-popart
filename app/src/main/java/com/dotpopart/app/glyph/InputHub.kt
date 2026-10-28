package com.dotpopart.app.glyph

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper

/**
 * センサーと電池の入力をまとめる。サービスとアプリ内のプレビューの両方で使う。
 *
 * 加速度センサーの軸（画面側から見て x が右、y が上、z が手前）で、重力と逆向きの値が出る。
 * - 背面のマトリクス（背面から見て x が右、y が下）では、重力は (ax, ay)。背面から見ると、画面の右は左に来るため。
 *   マトリクスの配列は背面から見た向きで左上が (0, 0)（システムの時計のコマで確かめた）。
 * - 画面上のプレビュー（画面側から見て x が右、y が下）では、重力は (−ax, ay)。
 */
class InputHub(context: Context) : SensorEventListener {
    private val app = context.applicationContext
    private val sensors = app.getSystemService(SensorManager::class.java)
    private val accel: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /**
     * Nothing の向きのセンサー（android.sensor.screen_upward、type 65538）。権限なしで使える。
     * システムはこの値で Glyph の LED を切り替えている：0 = 手に持っている・立てている、1 = 画面を上にして置いている
     * （LED を消す）、2 = 伏せている（AOD のおもちゃを出す）。値の意味は (4a) Pro のログで確かめた。
     */
    private val faceSensor: Sensor? = sensors?.getSensorList(Sensor.TYPE_ALL)?.firstOrNull { it.type == SCREEN_UPWARD_TYPE }

    /** [faceSensor] の最新の値（-1：まだ届いていない、またはセンサーがない）。 */
    @Volatile var systemFace = -1
        private set

    /** ローパスをかけた加速度（≒重力の逆向き）。 */
    @Volatile var ax = 0f
        private set
    @Volatile var ay = 9.8f
        private set
    @Volatile var az = 0f
        private set

    @Volatile var batteryLevel = 50
        private set
    @Volatile var charging = false
        private set

    private var hasSample = false
    private var running = false
    private var fast = false
    private var withAccel = true

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = readBattery(intent)
    }

    /** [useAccel] が false なら、加速度センサーは止めて、向きのセンサーだけを読む。 */
    fun start(fastRate: Boolean, useAccel: Boolean = true) {
        if (!running) {
            running = true
            app.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { readBattery(it) }
            registerSensor(fastRate, useAccel)
        } else if (fastRate != fast || useAccel != withAccel) {
            sensors?.unregisterListener(this)
            registerSensor(fastRate, useAccel)
        }
    }

    /**
     * 値は、呼んだスレッド（サービスならループのスレッド、画面ならメイン）に届ける。
     * サービスの値をメインに届けると、画面を出していてメインが忙しいときに傾きが遅れる。
     */
    private fun registerSensor(fastRate: Boolean, useAccel: Boolean) {
        fast = fastRate
        withAccel = useAccel
        val handler = Looper.myLooper()?.let { Handler(it) }
        if (useAccel) {
            accel?.let {
                sensors?.registerListener(this, it, if (fastRate) SensorManager.SENSOR_DELAY_GAME else SensorManager.SENSOR_DELAY_NORMAL, handler)
            }
        } else {
            // 止めている間の値は古くなる。次に読み始めたら、ローパスをかけずに最初の値から始める
            hasSample = false
        }
        faceSensor?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, handler) }
    }

    fun stop() {
        if (!running) return
        running = false
        sensors?.unregisterListener(this)
        runCatching { app.unregisterReceiver(batteryReceiver) }
    }

    private fun readBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0 && scale > 0) batteryLevel = level * 100 / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == SCREEN_UPWARD_TYPE) {
            systemFace = e.values[0].toInt()
            return
        }
        val x = e.values[0]
        val y = e.values[1]
        val z = e.values[2]
        if (!hasSample) {
            ax = x; ay = y; az = z
            hasSample = true
        } else {
            val k = if (fast) 0.2f else 0.5f
            ax += (x - ax) * k
            ay += (y - ay) * k
            az += (z - az) * k
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** 画面が下を向いているか（背面の LED が上を向いている）。 */
    fun screenDown(): Boolean = az < -7f

    fun screenUp(): Boolean = az > -4f

    /** 画面を上にして、ほぼ水平に置いている（システムが LED を消す状態）。 */
    fun screenFlatUp(): Boolean = az > 8.5f

    /** [screenFlatUp] から抜けたかの判定用（行ったり来たりしないよう、少し緩める）。 */
    fun screenFlatUpLoose(): Boolean = az > 7f

    private companion object {
        const val SCREEN_UPWARD_TYPE = 65538
    }
}
