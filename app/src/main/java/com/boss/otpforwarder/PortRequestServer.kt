package com.boss.otpforwarder

import android.content.Context
import java.net.ServerSocket

/**
 * PC가 현재 무선 디버깅 포트를 "요청(pull)"할 수 있게 하는 고정 포트(47801) 서버.
 *
 * PC가 꺼져 있는 동안 포트가 바뀌면 push가 유실되는데, PC를 켠 뒤
 * `GET http://<폰 Tailscale IP>:47801/port` 로 물으면 폰이 무선 디버깅을 켜고
 * 현재 포트를 찾아 `{"port":N,"ip":"..."}` 로 응답한다. ForwardService가 상주시킨다.
 */
object PortRequestServer {

    const val PORT = 47801

    @Volatile private var server: ServerSocket? = null

    fun start(ctx: Context) {
        if (server != null) return
        val app = ctx.applicationContext
        Thread {
            try {
                val ss = ServerSocket(PORT)
                server = ss
                while (!ss.isClosed) {
                    val sock = try { ss.accept() } catch (e: Exception) { break }
                    handle(app, sock)
                }
            } catch (_: Exception) {
                server = null
            }
        }.start()
    }

    fun stop() {
        try { server?.close() } catch (_: Exception) {}
        server = null
    }

    private fun handle(ctx: Context, sock: java.net.Socket) {
        Thread {
            try {
                sock.use { s ->
                    s.soTimeout = 12000
                    val input = s.getInputStream().bufferedReader()
                    val requestLine = input.readLine() ?: return@use
                    // 헤더는 읽어서 버린다(요청 완결).
                    while (true) {
                        val l = input.readLine() ?: break
                        if (l.isEmpty()) break
                    }

                    val body = if (requestLine.contains("/port")) {
                        val port = WirelessInfo.currentPortBlocking(ctx)
                        val ip = WirelessInfo.tailscaleIp()
                        if (port != null) {
                            """{"port":$port,"ip":${if (ip != null) "\"$ip\"" else "null"}}"""
                        } else {
                            """{"port":null}"""
                        }
                    } else {
                        """{"ok":true}"""
                    }

                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val resp = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    val out = s.getOutputStream()
                    out.write(resp.toByteArray(Charsets.UTF_8))
                    out.write(bytes)
                    out.flush()
                    if (requestLine.contains("/port")) {
                        Logbook.add(ctx, "PC 포트 요청 응답: $body")
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }
}
