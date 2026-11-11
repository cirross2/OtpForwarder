package com.boss.otpforwarder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** 주기적으로(기본 15분) 보류 큐를 전송하는 안전망 워커. */
class FlushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            // 15분마다 무선 디버깅이 꺼져 있으면 다시 켠다(화면 OFF 누락 대비 안전망)
            WirelessInfo.ensureWirelessDebug(applicationContext)
            Forwarder.flushNow(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
