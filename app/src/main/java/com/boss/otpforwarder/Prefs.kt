package com.boss.otpforwarder

import android.content.Context

/** 앱 설정 저장소. 봇 토큰/챗ID는 민감정보이므로 기기 내부에만 보관한다. */
object Prefs {
    private const val FILE = "otp_forwarder_prefs"

    private const val K_TOKEN = "bot_token"
    private const val K_CHAT = "chat_id"
    private const val K_FORWARD_SMS = "forward_sms"
    private const val K_FORWARD_KAKAO = "forward_kakao"
    private const val K_OTP_ONLY = "otp_only"
    private const val K_ENABLED = "enabled"
    private const val K_MIRROR_PAUSED = "mirroring_paused"

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var Context.botToken: String
        get() = sp(this).getString(K_TOKEN, "") ?: ""
        set(v) { sp(this).edit().putString(K_TOKEN, v.trim()).apply() }

    var Context.chatId: String
        get() = sp(this).getString(K_CHAT, "") ?: ""
        set(v) { sp(this).edit().putString(K_CHAT, v.trim()).apply() }

    var Context.forwardSms: Boolean
        get() = sp(this).getBoolean(K_FORWARD_SMS, true)
        set(v) { sp(this).edit().putBoolean(K_FORWARD_SMS, v).apply() }

    var Context.forwardKakao: Boolean
        get() = sp(this).getBoolean(K_FORWARD_KAKAO, true)
        set(v) { sp(this).edit().putBoolean(K_FORWARD_KAKAO, v).apply() }

    /** true면 인증번호가 감지된 메시지만 전송한다. 기본값은 false(전부 전송). */
    var Context.otpOnly: Boolean
        get() = sp(this).getBoolean(K_OTP_ONLY, false)
        set(v) { sp(this).edit().putBoolean(K_OTP_ONLY, v).apply() }

    var Context.enabled: Boolean
        get() = sp(this).getBoolean(K_ENABLED, true)
        set(v) { sp(this).edit().putBoolean(K_ENABLED, v).apply() }

    /** 미러링 중에는 true가 되어 포워딩을 일시중지한다. 재부팅 시 false로 초기화. */
    var Context.mirroringPaused: Boolean
        get() = sp(this).getBoolean(K_MIRROR_PAUSED, false)
        set(v) { sp(this).edit().putBoolean(K_MIRROR_PAUSED, v).apply() }

    fun Context.isConfigured(): Boolean =
        botToken.isNotEmpty() && chatId.isNotEmpty()

    /** 실제로 전달이 동작하는 상태인지(켜짐 + 미러링 일시중지 아님). */
    fun Context.isForwardingActive(): Boolean = enabled && !mirroringPaused
}
