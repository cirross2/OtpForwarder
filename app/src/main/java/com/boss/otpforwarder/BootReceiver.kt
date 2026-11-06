package com.boss.otpforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.boss.otpforwarder.Prefs.enabled

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
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
        Logbook.add(app, "부팅 후 재시작 처리됨")
    }
}
