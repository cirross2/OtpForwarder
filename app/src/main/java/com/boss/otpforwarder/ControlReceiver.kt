package com.boss.otpforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.boss.otpforwarder.Prefs.mirroringPaused

/**
 * PC(미러링 런처 스크립트)에서 보내는 제어 신호를 받는다.
 * 미러링 시작 시 포워딩을 일시중지하고, 종료 시 재개한다.
 *
 *   adb shell am broadcast -n com.boss.otpforwarder/.ControlReceiver --es cmd pause
 *   adb shell am broadcast -n com.boss.otpforwarder/.ControlReceiver --es cmd resume
 */
class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val cmd = intent.getStringExtra("cmd")
        android.util.Log.i("OtpFwd", "ControlReceiver cmd=$cmd")
        when (cmd) {
            "pause" -> {
                app.mirroringPaused = true
                Logbook.add(app, "미러링 모드 ON — 텔레그램 포워딩 일시중지")
            }
            "resume" -> {
                app.mirroringPaused = false
                Logbook.add(app, "미러링 모드 OFF — 텔레그램 포워딩 재개")
                Forwarder.flushAsync(app)
            }
        }
    }
}
