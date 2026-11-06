package com.boss.otpforwarder

import android.content.Context
import com.boss.otpforwarder.Prefs.botToken
import com.boss.otpforwarder.Prefs.chatId
import com.boss.otpforwarder.Prefs.enabled
import com.boss.otpforwarder.Prefs.isConfigured
import com.boss.otpforwarder.Prefs.otpOnly
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 메시지를 텔레그램으로 전달하는 핵심 로직.
 * 전송 실패분은 파일 큐에 쌓아 두었다가 네트워크 복구 시 재전송한다.
 */
object Forwarder {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val queueMutex = Mutex()

    data class Incoming(val source: String, val sender: String?, val body: String)

    /** 수신 즉시 호출. 전송 대상이면 큐에 넣고 바로 플러시를 시도한다. */
    fun handle(ctx: Context, msg: Incoming) {
        val app = ctx.applicationContext
        if (!app.enabled) return
        if (!app.isConfigured()) {
            Logbook.add(app, "⚠ 봇 토큰/챗ID 미설정 — ${msg.source} 수신분 보류")
            return
        }

        val otp = OtpExtractor.analyze(msg.body)
        if (app.otpOnly && !otp.isOtp) {
            // OTP 전용 모드인데 인증번호가 아니면 건너뜀
            return
        }

        val text = buildString {
            append("🔐 [${msg.source}]")
            if (otp.isOtp && otp.code != null) append(" 인증번호 ${otp.code}")
            append("\n")
            if (!msg.sender.isNullOrBlank()) append("보낸이: ${msg.sender}\n")
            append(msg.body.trim())
        }

        scope.launch {
            enqueue(app, text)
            flush(app)
        }
    }

    /** 네트워크 복구/부팅/주기 호출 시 보류분을 재전송한다. */
    fun flushAsync(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch { flush(app) }
    }

    /** 설정 화면 테스트 버튼: 즉시 전송 결과를 콜백으로 돌려준다. */
    fun sendTest(ctx: Context, onResult: (Boolean, String) -> Unit) {
        val app = ctx.applicationContext
        scope.launch {
            val ok = post(app.botToken, app.chatId, "✅ OtpForwarder 연결 테스트 메시지입니다.")
            Logbook.add(app, if (ok) "테스트 전송 성공" else "테스트 전송 실패")
            onResult(ok, if (ok) "전송 성공" else "전송 실패 — 토큰/챗ID/네트워크를 확인하세요")
        }
    }

    // ---- 파일 큐 ----

    private fun queueFile(ctx: Context) = File(ctx.filesDir, "pending.json")

    private suspend fun enqueue(ctx: Context, text: String) = queueMutex.withLock {
        val arr = readQueue(ctx)
        arr.put(JSONObject().put("t", System.currentTimeMillis()).put("text", text))
        queueFile(ctx).writeText(arr.toString())
    }

    private fun readQueue(ctx: Context): JSONArray {
        val f = queueFile(ctx)
        if (!f.exists()) return JSONArray()
        return runCatching { JSONArray(f.readText()) }.getOrDefault(JSONArray())
    }

    private suspend fun flush(ctx: Context) = queueMutex.withLock {
        val token = ctx.botToken
        val chat = ctx.chatId
        if (token.isEmpty() || chat.isEmpty()) return@withLock

        val arr = readQueue(ctx)
        if (arr.length() == 0) return@withLock

        val remaining = JSONArray()
        var sent = 0
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val text = item.getString("text")
            // 24시간 넘은 보류분은 폐기(오래된 인증번호는 의미 없음)
            val age = System.currentTimeMillis() - item.optLong("t", 0)
            if (age > 24 * 60 * 60 * 1000) continue
            val ok = post(token, chat, text)
            if (ok) sent++ else remaining.put(item)
        }
        queueFile(ctx).writeText(remaining.toString())
        if (sent > 0) Logbook.add(ctx, "텔레그램 전송 $sent 건 완료")
        if (remaining.length() > 0) Logbook.add(ctx, "보류 ${remaining.length()} 건 — 다음 기회에 재전송")
    }

    fun pendingCount(ctx: Context): Int = readQueue(ctx.applicationContext).length()

    // ---- 네트워크 ----

    private fun post(token: String, chatId: String, text: String): Boolean {
        return try {
            val url = URL("https://api.telegram.org/bot$token/sendMessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8"
            )
            val body = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") +
                "&disable_web_page_preview=true" +
                "&text=" + URLEncoder.encode(text, "UTF-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }
}
