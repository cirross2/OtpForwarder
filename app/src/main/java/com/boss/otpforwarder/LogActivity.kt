package com.boss.otpforwarder

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.boss.otpforwarder.databinding.ActivityLogBinding

/** 활동 로그: 최근 전송·상태 기록. */
class LogActivity : AppCompatActivity() {

    private lateinit var b: ActivityLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLogBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.applySystemBarInsets()

        b.toolbar.setNavigationOnClickListener { finish() }
        b.btnClearLog.setOnClickListener {
            Logbook.clear(this)
            refreshLog()
        }
        refreshLog()

        Logbook.setListener { runOnUiThread { refreshLog() } }
    }

    override fun onDestroy() {
        Logbook.setListener(null)
        super.onDestroy()
    }

    private fun refreshLog() {
        b.textLog.text = Logbook.get(this).ifBlank { "아직 기록 없음" }
    }
}
