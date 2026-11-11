package com.boss.otpforwarder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import java.util.concurrent.TimeUnit

/**
 * 상주 포그라운드 서비스.
 * 삼성 One UI의 절전 정리로 앱이 죽지 않도록 상태 알림을 띄우고,
 * 주기적으로 보류 큐를 재전송한다.
 */
class ForwardService : Service() {

    companion object {
        private const val CHANNEL_ID = "otp_forwarder_status"
        private const val NOTI_ID = 1001

        fun start(ctx: Context) {
            val i = Intent(ctx, ForwardService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(i)
                } else {
                    ctx.startService(i)
                }
            } catch (e: Exception) {
                // Android 12+ 백그라운드 FGS 시작 제한 등으로 실패할 수 있음.
                // 핵심 전달 기능은 알림 리스너가 담당하므로 무시한다.
            }
        }
    }

    private var netCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTI_ID, buildNotification())
        registerNetworkCallback()
        schedulePeriodicFlush()
    }

    /** 네트워크가 복구되는 즉시 보류 큐를 전송한다(밤새 쌓인 메시지 대응). */
    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Forwarder.flushAsync(applicationContext)
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (_: Exception) {}
    }

    /** 15분마다 보류 큐를 전송하는 안전망(앱을 안 열어도 동작). */
    private fun schedulePeriodicFlush() {
        Scheduler.ensurePeriodicFlush(applicationContext)
    }

    override fun onDestroy() {
        netCallback?.let {
            try { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 시작될 때마다 보류분 재전송 시도
        Forwarder.flushAsync(applicationContext)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                "인증번호 전달 상태",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "앱이 백그라운드에서 동작 중임을 표시합니다." }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("인증번호 전달 대기 중")
            .setContentText("문자·카톡 수신을 감시하고 있습니다.")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .build()
    }
}
