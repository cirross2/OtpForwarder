package com.boss.otpforwarder

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 화면에 보여줄 최근 활동 로그(민감정보 최소화). */
object Logbook {
    private const val FILE = "otp_forwarder_log"
    private const val KEY = "lines"
    private const val MAX = 60

    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA)
    private var listener: (() -> Unit)? = null

    fun setListener(l: (() -> Unit)?) { listener = l }

    fun add(ctx: Context, line: String) {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val cur = sp.getString(KEY, "") ?: ""
        val stamped = "[${fmt.format(Date())}] $line"
        val merged = (listOf(stamped) + cur.split("\n").filter { it.isNotBlank() })
            .take(MAX)
            .joinToString("\n")
        sp.edit().putString(KEY, merged).apply()
        listener?.invoke()
    }

    fun get(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        listener?.invoke()
    }
}
