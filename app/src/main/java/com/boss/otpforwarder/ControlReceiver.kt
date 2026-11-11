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
 *   adb shell am broadcast -n com.boss.otpforwarder/.ControlReceiver --es cmd announce
 */
class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val cmd = intent.getStringExtra("cmd")
        android.util.Log.i("OtpFwd", "ControlReceiver cmd=$cmd")
        when (cmd) {
            "pause" -> {
                val wasActive = !app.mirroringPaused
                app.mirroringPaused = true
                // PC가 미러링 중 주기적으로 pause를 보낸다(하트비트). 끊기면 자동 재개.
                Scheduler.scheduleAutoResume(app)
                if (wasActive) Logbook.add(app, "미러링 모드 ON — 텔레그램 포워딩 일시중지")
            }
            "resume" -> {
                app.mirroringPaused = false
                Scheduler.cancelAutoResume(app)
                Logbook.add(app, "미러링 모드 OFF — 텔레그램 포워딩 재개")
                Forwarder.flushAsync(app)
            }
        }
    }
}
