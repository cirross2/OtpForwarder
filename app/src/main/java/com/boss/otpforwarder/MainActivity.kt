package com.boss.otpforwarder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.boss.otpforwarder.Prefs.announcePort
import com.boss.otpforwarder.Prefs.botToken
import com.boss.otpforwarder.Prefs.chatId
import com.boss.otpforwarder.Prefs.enabled
import com.boss.otpforwarder.Prefs.forwardKakao
import com.boss.otpforwarder.Prefs.forwardSms
import com.boss.otpforwarder.Prefs.isForwardingActive
import com.boss.otpforwarder.Prefs.mirroringPaused
import com.boss.otpforwarder.Prefs.otpOnly
import com.boss.otpforwarder.Prefs.pcAddresses
import com.boss.otpforwarder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val smsPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // 저장된 값 로드
        b.editToken.setText(botToken)
        b.editChatId.setText(chatId)
        b.switchEnabled.isChecked = enabled
        b.switchSms.isChecked = forwardSms
        b.switchKakao.isChecked = forwardKakao
        b.switchOtpOnly.isChecked = otpOnly
        b.switchMirror.isChecked = mirroringPaused
        b.switchMirror.setOnCheckedChangeListener { _, checked ->
            mirroringPaused = checked
            refreshStatus()
        }
        b.switchAnnouncePort.isChecked = announcePort
        b.switchAnnouncePort.setOnCheckedChangeListener { _, checked ->
            announcePort = checked
        }
        b.editPcAddr.setText(pcAddresses)

        b.btnSave.setOnClickListener { save() }
        b.btnTest.setOnClickListener { test() }
        b.btnAnnouncePort.setOnClickListener { announcePort() }
        b.btnPermSms.setOnClickListener { requestSmsPermission() }
        b.btnPermNoti.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        b.btnPermBattery.setOnClickListener { requestIgnoreBattery() }
        b.btnClearLog.setOnClickListener {
            Logbook.clear(this); refreshLog()
        }
        b.textVersion.text = "v${BuildConfig.VERSION_NAME}"
        b.btnUpdate.setOnClickListener { checkUpdate() }

        // 앱을 열 때도 주기 플러시 안전망을 보장
        Scheduler.ensurePeriodicFlush(this)

        Logbook.setListener {
            runOnUiThread {
                if (b.switchMirror.isChecked != mirroringPaused) {
                    b.switchMirror.isChecked = mirroringPaused
                }
                refreshStatus()
                refreshLog()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (b.switchMirror.isChecked != mirroringPaused) {
            b.switchMirror.isChecked = mirroringPaused
        }
        refreshStatus()
        refreshLog()
    }

    override fun onDestroy() {
        Logbook.setListener(null)
        super.onDestroy()
    }

    private fun save() {
        botToken = b.editToken.text.toString()
        chatId = b.editChatId.text.toString()
        enabled = b.switchEnabled.isChecked
        forwardSms = b.switchSms.isChecked
        forwardKakao = b.switchKakao.isChecked
        otpOnly = b.switchOtpOnly.isChecked
        pcAddresses = b.editPcAddr.text.toString()

        if (enabled) ForwardService.start(this)
        Toast.makeText(this, "저장했습니다", Toast.LENGTH_SHORT).show()
        // PC 주소가 등록돼 있으면 저장 즉시 현재 포트를 PC로 전송
        if (pcAddresses.isNotBlank()) {
            Toast.makeText(this, "등록된 PC로 포트 전송 중…", Toast.LENGTH_SHORT).show()
            WirelessInfo.pushOnly(this)
        }
        refreshStatus()
    }

    private fun test() {
        if (botToken.isBlank() || chatId.isBlank()) {
            // 입력 중인 값으로도 테스트할 수 있게 먼저 저장
            save()
        }
        Toast.makeText(this, "테스트 전송 중…", Toast.LENGTH_SHORT).show()
        Forwarder.sendTest(this) { _, msg ->
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }
    }

    private fun announcePort() {
        if (botToken.isBlank() || chatId.isBlank()) {
            Toast.makeText(this, "먼저 봇 토큰/챗ID를 저장하세요", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "무선 포트 찾는 중… (최대 8초)", Toast.LENGTH_SHORT).show()
        WirelessInfo.announce(this)
    }

    private fun requestSmsPermission() {
        val perms = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        smsPermLauncher.launch(perms.toTypedArray())
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBattery() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun checkUpdate() {
        Toast.makeText(this, "업데이트 확인 중…", Toast.LENGTH_SHORT).show()
        UpdateChecker.check(this) { release, msg ->
            if (release == null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            } else {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("새 버전 ${release.version}")
                    .setMessage(if (release.notes.isBlank()) "지금 업데이트할까요?" else release.notes)
                    .setPositiveButton("설치") { _, _ ->
                        if (ensureInstallPermission()) {
                            UpdateChecker.downloadAndInstall(this, release) { s ->
                                runOnUiThread { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
                            }
                        }
                    }
                    .setNegativeButton("나중에", null)
                    .show()
            }
        }
    }

    /** 알 수 없는 출처 설치 권한 확인. 없으면 설정으로 보내고 false 반환. */
    private fun ensureInstallPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, "이 앱의 '알 수 없는 앱 설치'를 허용해 주세요", Toast.LENGTH_LONG).show()
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {}
            return false
        }
        return true
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNotificationAccess(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(packageName)
    }

    private fun refreshStatus() {
        val sms = if (hasSmsPermission()) "✅" else "❌"
        val noti = if (hasNotificationAccess()) "✅" else "❌"
        val configured = if (botToken.isNotBlank() && chatId.isNotBlank()) "✅" else "❌"
        val pending = Forwarder.pendingCount(this)
        val fwd = when {
            isForwardingActive() -> "✅ 활성"
            mirroringPaused -> "⏸ 미러링 중 (포워딩 중지)"
            else -> "⏹ 꺼짐"
        }
        b.textStatus.text = buildString {
            append("전달 상태: $fwd\n")
            append("SMS 권한: $sms\n")
            append("알림 접근(카톡): $noti\n")
            append("봇 설정: $configured\n")
            append("재전송 대기: ${pending}건")
        }
    }

    private fun refreshLog() {
        b.textLog.text = Logbook.get(this).ifBlank { "아직 기록 없음" }
    }
}
