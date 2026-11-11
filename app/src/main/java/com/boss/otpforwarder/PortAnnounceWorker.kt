package com.boss.otpforwarder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.boss.otpforwarder.Prefs.announcePort

/** 재부팅 후 일정 시간 뒤 무선 디버깅 포트를 안내하는 1회성 작업. */
class PortAnnounceWorker(ctx: Context, params: WorkerParameters) :
    CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!app.announcePort) return Result.success()
        return try {
            WirelessInfo.announceSuspend(app)
            Result.success()
        } catch (e: Exception) {
            Result.success()
        }
    }
}
