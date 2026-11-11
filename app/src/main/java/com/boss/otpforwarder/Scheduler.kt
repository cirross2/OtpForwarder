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

    /** 미러링 pause 후 3분간 하트비트(재-pause)가 없으면 포워딩을 자동 재개한다. */
    fun scheduleAutoResume(ctx: Context) {
        try {
            val req = OneTimeWorkRequestBuilder<AutoResumeWorker>()
                .setInitialDelay(3, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniqueWork("otp_auto_resume", ExistingWorkPolicy.REPLACE, req)
        } catch (_: Exception) {}
    }

    /** 미러링이 정상 종료(resume)되면 자동 재개 안전장치를 취소한다. */
    fun cancelAutoResume(ctx: Context) {
        try {
            WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork("otp_auto_resume")
        } catch (_: Exception) {}
    }

}
