package com.boss.otpforwarder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.boss.otpforwarder.Prefs.mirroringPaused

/**
 * 미러링 pause 후 PC의 하트비트(주기적 pause)가 끊기면 호출되어
 * 포워딩을 자동으로 재개한다. resume 신호가 유실돼도 영원히 멈추지 않게 하는 안전장치.
 */
class AutoResumeWorker(ctx: Context, params: WorkerParameters) :
    CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        if (app.mirroringPaused) {
            app.mirroringPaused = false
            Logbook.add(app, "⏱ 미러링 신호 끊김 — 포워딩 자동 재개(안전장치)")
            Forwarder.flushAsync(app)
        }
        return Result.success()
    }
}
