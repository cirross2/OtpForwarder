package com.boss.otpforwarder

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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

    /** 재부팅 후 무선 디버깅이 올라올 시간을 두고 포트를 안내하는 1회성 작업. */
    fun schedulePortAnnounce(ctx: Context) {
        try {
            val req = OneTimeWorkRequestBuilder<PortAnnounceWorker>()
                .setInitialDelay(45, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniqueWork("otp_port_announce", ExistingWorkPolicy.REPLACE, req)
        } catch (_: Exception) {}
    }
}
