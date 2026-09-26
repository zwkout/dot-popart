package com.dotpopart.app.glyph

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.atomic.AtomicInteger

/**
 * 通知が来たという事実だけを数える（AQUARIUM のエサ）。
 * 通知の本文・送信者・アプリ名は読まないし、保存もしない。
 */
class NotificationCounter : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 常駐の通知（音楽再生中など）と、このアプリ自身の通知は数えない
        if (sbn.isOngoing || sbn.packageName == packageName) return
        count.incrementAndGet()
    }

    companion object {
        /** プロセスが動いている間の累計件数。 */
        val count = AtomicInteger(0)

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, NotificationCounter::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
