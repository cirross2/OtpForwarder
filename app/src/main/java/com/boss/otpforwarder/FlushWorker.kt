package com.boss.otpforwarder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** 주기적으로(기본 15분) 보류 큐를 전송하는 안전망 워커. */
class FlushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            Forwarder.flushNow(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
