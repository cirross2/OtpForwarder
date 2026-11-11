package com.boss.otpforwarder

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.boss.otpforwarder.Prefs.botToken
import com.boss.otpforwarder.Prefs.chatId
import com.boss.otpforwarder.Prefs.enabled
import com.boss.otpforwarder.Prefs.forwardKakao
import com.boss.otpforwarder.Prefs.forwardSms
import com.boss.otpforwarder.Prefs.otpOnly
import com.boss.otpforwarder.databinding.ActivitySettingsBinding

/** 전달 설정: 봇 토큰/챗ID, 전달 옵션, 테스트, 업데이트. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBack.setOnClickListener { finish() }

        b.editToken.setText(botToken)
        b.editChatId.setText(chatId)
        b.switchSms.isChecked = forwardSms
        b.switchKakao.isChecked = forwardKakao
        b.switchOtpOnly.isChecked = otpOnly

        b.btnSave.setOnClickListener { save() }
        b.btnTest.setOnClickListener { test() }
        b.btnUpdate.setOnClickListener { checkUpdate() }
    }

    private fun save() {
        botToken = b.editToken.text.toString()
        chatId = b.editChatId.text.toString()
        forwardSms = b.switchSms.isChecked
        forwardKakao = b.switchKakao.isChecked
        otpOnly = b.switchOtpOnly.isChecked
        if (enabled) ForwardService.start(this)
        Toast.makeText(this, "저장했습니다", Toast.LENGTH_SHORT).show()
    }

    private fun test() {
        if (botToken.isBlank() || chatId.isBlank()) save()
        Toast.makeText(this, "테스트 전송 중…", Toast.LENGTH_SHORT).show()
        Forwarder.sendTest(this) { _, msg ->
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }
    }

    private fun checkUpdate() {
        Toast.makeText(this, "업데이트 확인 중…", Toast.LENGTH_SHORT).show()
        UpdateChecker.check(this) { release, msg ->
            if (release == null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            } else {
                AlertDialog.Builder(this)
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

    private fun ensureInstallPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(this, "이 앱의 '알 수 없는 앱 설치'를 허용해 주세요", Toast.LENGTH_LONG).show()
            try {
                startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {}
            return false
        }
        return true
    }
}
