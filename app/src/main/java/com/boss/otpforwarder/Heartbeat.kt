package com.boss.otpforwarder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 삼성이 장시간 딥 도즈에서 상주 서비스를 죽여도 스스로 되살아나게 하는 심장박동.
 *
 * setAndAllowWhileIdle은 도즈 중에도 (약 15분 간격으로) 발화하며 별도 권한이 필요 없다.
 * 발화 때마다 서비스를 다시 띄우고 무선 디버깅을 재활성한 뒤 다음 알람을 재예약한다.
 */
object Heartbeat {
    private const val REQ = 4701
    private const val INTERVAL = 15 * 60 * 1000L

    fun schedule(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pi = PendingIntent.getBroadcast(
                ctx.applicationContext,
                REQ,
                Intent(ctx.applicationContext, HeartbeatReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + INTERVAL,
                pi
            )
        } catch (_: Exception) {}
    }
}
