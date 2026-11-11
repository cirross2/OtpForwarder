package com.boss.otpforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.boss.otpforwarder.Prefs.enabled
import com.boss.otpforwarder.Prefs.mirroringPaused

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        // 안전장치: 재부팅하면 미러링 일시중지 상태를 해제해 포워딩이 반드시 재개되게 한다.
        app.mirroringPaused = false
        if (!app.enabled) return
        // 재부팅 직후 포그라운드 서비스 시작이 OS 정책에 막혀 예외가 나도
        // 앱이 죽지 않도록 감싼다. 전달 자체는 시스템이 자동 재연결하는
        // 알림 리스너로 계속 동작한다.
        try {
            ForwardService.start(app)
        } catch (e: Exception) {
            Logbook.add(app, "부팅 서비스 시작 보류(${e.javaClass.simpleName}) — 리스너로 계속 동작")
        }
        Forwarder.flushAsync(app)
        Scheduler.ensurePeriodicFlush(app)
        // 삼성이 재부팅 때 꺼버리는 무선 디버깅을 자동으로 다시 켠다.
        if (WirelessInfo.ensureWirelessDebug(app)) Logbook.add(app, "부팅 후 무선 디버깅 자동 켬")
        Logbook.add(app, "부팅 후 재시작 처리됨")
    }
}
