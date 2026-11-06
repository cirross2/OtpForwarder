package com.boss.otpforwarder

/**
 * 같은 메시지가 두 경로(SMS 브로드캐스트 + 메시지앱 알림)로 중복 전송되는 것을 막는다.
 * 본문을 정규화해 짧은 시간창 안의 동일 메시지를 걸러낸다.
 */
object Dedup {
    private val recent = ArrayDeque<Pair<String, Long>>()
    private const val WINDOW_MS = 15000L

    @Synchronized
    fun isDuplicate(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val key = normalize(text)
        val now = System.currentTimeMillis()
        while (recent.isNotEmpty() && now - recent.first().second > WINDOW_MS) {
            recent.removeFirst()
        }
        if (recent.any { it.first == key }) return true
        recent.addLast(key to now)
        return false
    }

    private fun normalize(t: String): String =
        t.replace(Regex("""\s"""), "").take(80)
}
