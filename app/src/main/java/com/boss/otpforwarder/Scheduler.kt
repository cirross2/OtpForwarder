package com.boss.otpforwarder

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** 보류 큐를 주기적으로 비우는 안전망 작업을 등록한다(앱 실행/부팅/서비스 시작 시). */
object Scheduler {
    fun ensurePeriodicFlush(ctx: Context) {
        try {
            val req = PeriodicWorkRequestBuilder<FlushWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniquePeriodicWork("otp_flush", ExistingPeriodicWorkPolicy.KEEP, req)
        } catch (_: Exception) {}
    }
}
