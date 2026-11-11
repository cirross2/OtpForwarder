package com.boss.otpforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.boss.otpforwarder.Prefs.enabled

/** 심장박동 알람 수신: 서비스를 되살리고 무선 디버깅을 재활성한 뒤 다음 알람을 재예약한다. */
class HeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        WirelessInfo.ensureWirelessDebug(app)
        // 알람 발화 시 OS가 잠깐 FGS 시작을 허용한다(배터리 제외 상태라 더 안전).
        if (app.enabled) ForwardService.start(app)
        Heartbeat.schedule(app)
    }
}
