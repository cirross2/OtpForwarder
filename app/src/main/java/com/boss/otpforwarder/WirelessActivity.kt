package com.boss.otpforwarder

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.boss.otpforwarder.Prefs.mirroringPaused
import com.boss.otpforwarder.Prefs.pcAddresses
import com.boss.otpforwarder.databinding.ActivityWirelessBinding

/** 무선·PC 연결: 이 폰 주소 안내, PC 주소 등록(저장 시 전송), 수동 옵션. */
class WirelessActivity : AppCompatActivity() {

    private lateinit var b: ActivityWirelessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityWirelessBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.applySystemBarInsets()

        b.toolbar.setNavigationOnClickListener { finish() }

        b.textPhoneIp.text = WirelessInfo.tailscaleIp() ?: "테일스케일 미연결"
        b.editPcAddr.setText(pcAddresses)
        b.switchMirror.isChecked = mirroringPaused
        b.switchMirror.setOnCheckedChangeListener { _, checked -> mirroringPaused = checked }

        b.btnSaveWireless.setOnClickListener { saveAndSend() }
        b.btnWirelessOn.setOnClickListener {
            val ok = WirelessInfo.ensureWirelessDebug(this)
            Toast.makeText(this, if (ok) "무선 디버깅을 켰습니다" else "권한이 없어 켜지 못했습니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        pcAddresses = b.editPcAddr.text.toString()
    }

    private fun saveAndSend() {
        pcAddresses = b.editPcAddr.text.toString()
        if (pcAddresses.isBlank()) {
            Toast.makeText(this, "저장했습니다 (등록된 PC 없음)", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "저장 후 등록된 PC로 전송 중…", Toast.LENGTH_SHORT).show()
        WirelessInfo.pushOnly(this)
    }
}
