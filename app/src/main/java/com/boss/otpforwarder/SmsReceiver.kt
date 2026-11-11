package com.boss.otpforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.boss.otpforwarder.Prefs.forwardSms

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext
        if (!app.forwardSms) return

        // 멀티파트 SMS는 발신자별로 본문을 합친다.
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].displayOriginatingAddress
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        if (body.isBlank()) return

        // 메시지 앱 알림 경로와 중복되면 건너뜀(번호가 있으면 번호 기준으로 비교)
        val code = OtpExtractor.extractCode(body)
        if (Dedup.isDuplicate(code ?: body)) return

        Logbook.add(app, "SMS 수신 ($sender)")
        Forwarder.handle(app, Forwarder.Incoming("문자", sender, body))
    }
}
