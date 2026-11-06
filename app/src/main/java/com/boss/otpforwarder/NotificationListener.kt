package com.boss.otpforwarder

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.boss.otpforwarder.Prefs.forwardKakao
import com.boss.otpforwarder.Prefs.forwardSms

/**
 * 카카오톡·메시지 앱의 알림 본문을 읽어 전달한다.
 * - 카톡: 공식 API가 없어 알림 접근이 유일한 방법.
 * - 문자: RCS("채팅") 메시지는 SMS 브로드캐스트가 안 오므로, 메시지 앱 알림으로 받는다.
 */
class NotificationListener : NotificationListenerService() {

    companion object {
        // 패키지 -> 전달 시 표기할 출처
        private val TARGETS = mapOf(
            "com.kakao.talk" to "카카오톡",
            "com.samsung.android.messaging" to "문자",       // 삼성 메시지 (SMS/RCS)
            "com.google.android.apps.messaging" to "문자"    // 구글 메시지 (RCS)
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val app = applicationContext
        val source = TARGETS[sbn.packageName] ?: return

        // 출처별 on/off 스위치 적용
        val isSms = source == "문자"
        if (isSms && !app.forwardSms) return
        if (!isSms && !app.forwardKakao) return

        val n = sbn.notification ?: return
        val extras = n.extras ?: return

        // 그룹 요약 알림(개별 메시지 아님)은 건너뜀
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        // 진행 중(ongoing) 알림(예: "메시지 받는 중")도 제외
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        if (text.isNullOrBlank()) return

        // "메시지 N개" 같은 요약 문구는 제외
        if (text.matches(Regex(""".*\d+개의? (새 )?(메시지|대화).*"""))) return

        // SMS 브로드캐스트와 중복될 수 있으므로 본문 기준 중복 제거
        if (Dedup.isDuplicate(text)) return

        Logbook.add(app, "$source 알림 수신 (${title ?: "알 수 없음"})")
        Forwarder.handle(app, Forwarder.Incoming(source, title, text))
    }

    override fun onListenerConnected() {
        Logbook.add(applicationContext, "알림 리스너 연결됨")
    }
}
