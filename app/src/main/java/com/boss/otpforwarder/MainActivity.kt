package com.boss.otpforwarder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.boss.otpforwarder.Prefs.enabled
import com.boss.otpforwarder.Prefs.fcmToken
import com.boss.otpforwarder.Prefs.isForwardingActive
import com.boss.otpforwarder.Prefs.mirroringPaused
import com.boss.otpforwarder.databinding.ActivityMainBinding

/** 홈: 전달 상태 + 전달 켜기 토글 + 카테고리 메뉴. */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.applySystemBarInsets()

        b.switchEnabled.isChecked = enabled
        b.switchEnabled.setOnCheckedChangeListener { _, checked ->
            enabled = checked
            if (checked) ForwardService.start(this)
            refreshStatus()
        }

        b.rowSettings.setOnClickListener { open(SettingsActivity::class.java) }
        b.rowPerms.setOnClickListener { open(PermissionsActivity::class.java) }
        b.rowWireless.setOnClickListener { open(WirelessActivity::class.java) }
        b.rowLog.setOnClickListener { open(LogActivity::class.java) }

        b.textVersion.text = "v${BuildConfig.VERSION_NAME}"

        if (enabled) ForwardService.start(this)
        Scheduler.ensurePeriodicFlush(this)
        WirelessInfo.ensureWirelessDebug(this)
        Heartbeat.schedule(this)

        // FCM 토큰 확보 후 저장 + 등록된 PC로 전달
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { t ->
                fcmToken = t
                WirelessInfo.pushTokenToPcs(this, t)
            }
    }

    private fun open(cls: Class<*>) = startActivity(Intent(this, cls))

    override fun onResume() {
        super.onResume()
        b.switchEnabled.isChecked = enabled
        refreshStatus()
    }

    private fun refreshStatus() {
        val (txt, color) = when {
            isForwardingActive() -> "활성" to R.color.ok
            mirroringPaused -> "미러링 중 · 중지" to R.color.warn
            else -> "꺼짐" to R.color.sub
        }
        b.textStatusValue.text = txt
        b.textStatusValue.setTextColor(ContextCompat.getColor(this, color))
        b.textPending.text = "재전송 대기 ${Forwarder.pendingCount(this)}건"
    }
}
