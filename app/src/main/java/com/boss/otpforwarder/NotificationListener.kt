package com.boss.otpforwarder

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.boss.otpforwarder.Prefs.forwardKakao
import com.boss.otpforwarder.Prefs.forwardSms

/**
 * 카카오톡·메시지 앱의 알림 본문을 읽어 전달한다.
 * - 카톡: 공식 API가 없어 알림 접근이 유일한 방법.
 * - 문자: RCS("채팅")는 SMS 브로드캐스트가 안 와서 메시지 앱 알림으로 받는다.
 * - OTP 알림이 OS에 가려져도(EXTRA_TEXT=플레이스홀더) 알림 버튼("복사 (123456)")에서
 *   번호를 복구하고, 번호도 내용도 없는 껍데기 알림은 전송하지 않는다.
 */
class NotificationListener : NotificationListenerService() {

    companion object {
        private val TARGETS = mapOf(
            "com.kakao.talk" to "카카오톡",
            "com.samsung.android.messaging" to "문자",
            "com.google.android.apps.messaging" to "문자"
        )

        // OS/앱이 내용을 가릴 때 넣는 껍데기 문구들 — 이것만 있으면 전송하지 않는다.
        private val PLACEHOLDERS = listOf(
            "민감한 알림 콘텐츠 숨김",
            "메시지가 도착했습니다",
            "메시지가 도착했어요",
            "새 메시지",
            "새로운 메시지",
            "콘텐츠 숨김",
            "내용 숨김"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val app = applicationContext
        val source = TARGETS[sbn.packageName] ?: return

        val isSms = source == "문자"
        if (isSms && !app.forwardSms) return
        if (!isSms && !app.forwardKakao) return

        val n = sbn.notification ?: return
        val extras = n.extras ?: return

        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val ticker = n.tickerText?.toString()?.trim()

        // 1) 가림이 아닌 진짜 본문 후보
        val realText = listOf(bigText, text, ticker).firstOrNull {
            !it.isNullOrBlank() && !isPlaceholder(it)
        }
        // 2) 알림 버튼("복사 (422205)")에서 코드 복구
        val actionCode = codeFromActions(n)

        when {
            // 본문도 없고 버튼 코드도 없음 → 껍데기 알림이므로 전송 안 함
            realText == null && actionCode == null -> {
                Logbook.add(app, "$source: 가려진 알림 — 번호 없어 건너뜀 (${title ?: "?"})")
                return
            }
            // 본문은 가려졌지만 버튼에서 코드 복구 성공
            realText == null && actionCode != null -> {
                if (Dedup.isDuplicate(actionCode)) return
                Logbook.add(app, "$source: 가려진 알림에서 인증번호 복구")
                Forwarder.handle(app, Forwarder.Incoming(source, title, "인증번호 $actionCode"))
            }
            // 정상 본문
            else -> {
                if (isPlaceholder(realText)) return
                val code = actionCode ?: OtpExtractor.extractCode(realText)
                if (Dedup.isDuplicate(code ?: realText!!)) return
                Logbook.add(app, "$source 알림 수신 (${title ?: "알 수 없음"})")
                Forwarder.handle(app, Forwarder.Incoming(source, title, realText!!))
            }
        }
    }

    private fun isPlaceholder(t: String?): Boolean {
        if (t.isNullOrBlank()) return true
        if (PLACEHOLDERS.any { t.contains(it) }) return true
        return t.matches(Regex(""".*\d+개의? (새 )?(메시지|대화).*"""))
    }

    /** 알림 액션 라벨에서 OTP를 뽑는다. 예: "복사 (422205)", "Copy (123456)". */
    private fun codeFromActions(n: Notification): String? {
        val actions = n.actions ?: return null
        val paren = Regex("""\((\d{4,8})\)""")
        for (a in actions) {
            val t = a.title?.toString() ?: continue
            paren.find(t)?.let { return it.groupValues[1] }
            if (t.contains("복사") || t.contains("Copy", true) ||
                t.contains("인증") || t.contains("OTP", true)
            ) {
                Regex("""\b(\d{4,8})\b""").find(t)?.let { return it.groupValues[1] }
            }
        }
        return null
    }

    override fun onListenerConnected() {
        Logbook.add(applicationContext, "알림 리스너 연결됨")
        // 재연결 시 밤새 쌓인 보류분을 즉시 전송
        Forwarder.flushAsync(applicationContext)
    }

    override fun onListenerDisconnected() {
        // 리스너가 끊기면 즉시 재연결을 요청해 카톡/문자 누락을 줄인다.
        Logbook.add(applicationContext, "알림 리스너 끊김 — 재연결 요청")
        try {
            requestRebind(android.content.ComponentName(this, NotificationListener::class.java))
        } catch (_: Exception) {}
    }
}
