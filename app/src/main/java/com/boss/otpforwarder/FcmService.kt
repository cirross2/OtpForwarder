package com.boss.otpforwarder

import com.boss.otpforwarder.Prefs.enabled
import com.boss.otpforwarder.Prefs.fcmToken
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * PC가 보낸 FCM "깨우기" 메시지를 받는다. 구글 플레이 서비스가 앱을 깨우므로
 * (딥 도즈에서 서비스가 죽어 있어도) 여기서 무선 디버깅을 켜고 상주 서비스를 되살린다.
 */
class FcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val app = applicationContext
        Logbook.add(app, "FCM 깨우기 수신 — 무선 디버깅/서비스 켜기")
        WirelessInfo.ensureWirelessDebug(app)
        if (app.enabled) ForwardService.start(app)
    }

    override fun onNewToken(token: String) {
        val app = applicationContext
        app.fcmToken = token
        WirelessInfo.pushTokenToPcs(app, token)
    }
}
