package com.boss.otpforwarder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.boss.otpforwarder.databinding.ActivityPermissionsBinding

/** 권한: 문자 수신, 알림 접근(카톡), 배터리 최적화 제외. */
class PermissionsActivity : AppCompatActivity() {

    private lateinit var b: ActivityPermissionsBinding

    private val smsPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.applySystemBarInsets()

        b.toolbar.setNavigationOnClickListener { finish() }
        b.btnPermSms.setOnClickListener { requestSmsPermission() }
        b.btnPermNoti.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        b.btnPermBattery.setOnClickListener { requestIgnoreBattery() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
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

    private fun hasSms(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNotiAccess(): Boolean {
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        return listeners.contains(packageName)
    }

    private fun ignoringBattery(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun refresh() {
        b.statusSms.text = if (hasSms()) "✅ 허용됨" else "❌ 필요"
        b.statusNoti.text = if (hasNotiAccess()) "✅ 허용됨" else "❌ 필요"
        b.statusBattery.text = if (ignoringBattery()) "✅ 제외됨" else "권장"
    }
}
