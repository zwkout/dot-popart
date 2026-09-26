package com.dotpopart.engine

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * おもちゃが読む外部の入力。実機ではサービスが、プレビューでは [PreviewSimulator] が書き込む。
 * テストでは自由に差し替えられる。
 */
class Inputs(
    /** 壁時計の時刻（エポックミリ秒）。 */
    var epochMillis: Long = System.currentTimeMillis(),
    var zone: ZoneId = ZoneId.systemDefault(),
    /**
     * 重力（m/s²）。マトリクスを正面（背面側）から見た座標で、x は右、y は下が正。
     * 伏せて水平に置いたときは x, y ともほぼ 0 になる。
     */
    var gravityX: Float = 0f,
    var gravityY: Float = 0f,
    /** 電池残量（0〜100）。 */
    var batteryLevel: Int = 80,
    var charging: Boolean = false,
    /** 受け取った通知の累計件数。増えた分だけエサが落ちる。 */
    var notifications: Int = 0,
) {
    fun zoned(): ZonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)

    fun local(): LocalDateTime = zoned().toLocalDateTime()

    fun copyFrom(o: Inputs) {
        epochMillis = o.epochMillis
        zone = o.zone
        gravityX = o.gravityX
        gravityY = o.gravityY
        batteryLevel = o.batteryLevel
        charging = o.charging
        notifications = o.notifications
    }
}
