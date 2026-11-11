package com.boss.otpforwarder

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import com.boss.otpforwarder.Prefs.fcmToken
import com.boss.otpforwarder.Prefs.pcAddresses
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL

/**
 * 무선 디버깅을 유지하고, PC의 요청에 현재 포트를 돌려주기 위한 도우미.
 *
 * 설계(pull): 폰은 무선 디버깅을 항상 켜두고, PC가 [PortRequestServer](47801)로
 * 물으면 그때 현재 포트를 mDNS로 찾아 응답한다. 폰이 PC로 먼저 쏘지 않는다.
 */
object WirelessInfo {

    private const val SERVICE_TYPE = "_adb-tls-connect._tcp."

    /**
     * 무선 디버깅을 켠다. WRITE_SECURE_SETTINGS 권한이 있어야 하며
     * (adb로 pm grant), 삼성이 재부팅/화면꺼짐 때 꺼버리는 걸 자동 복구한다.
     */
    fun ensureWirelessDebug(ctx: Context): Boolean {
        return try {
            android.provider.Settings.Global.putInt(
                ctx.applicationContext.contentResolver, "adb_wifi_enabled", 1
            )
            true
        } catch (e: Exception) {
            Logbook.add(ctx, "무선 디버깅 자동 켜기 실패 — WRITE_SECURE_SETTINGS 권한 필요")
            false
        }
    }

    /** 테일스케일 IP(100.64.0.0/10, 즉 100.64~127 대역)를 찾는다. 없으면 null. */
    fun tailscaleIp(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .mapNotNull { it.hostAddress }
                .firstOrNull { ip ->
                    val p = ip.split(".")
                    p.size == 4 &&
                        p[0].toIntOrNull() == 100 &&
                        (p[1].toIntOrNull() ?: -1) in 64..127
                }
        } catch (e: Exception) {
            null
        }
    }

    /** 화면이 꺼지면 삼성이 무선 디버깅을 꺼버린다. 잠시 뒤 다시 켠다(전송은 없음). */
    fun onScreenOff(ctx: Context) {
        val app = ctx.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({ ensureWirelessDebug(app) }, 2500)
    }

    /** mDNS로 무선 디버깅 연결 포트를 찾아 콜백한다. 못 찾으면 null. */
    private fun discoverPort(ctx: Context, timeoutMs: Long = 8000, onResult: (Int?) -> Unit) {
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsd == null) {
            onResult(null)
            return
        }

        // 일부 기기는 멀티캐스트 락이 없으면 mDNS 응답을 못 받는다.
        val lock = try {
            val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifi?.createMulticastLock("otp-adb-mdns")?.apply { setReferenceCounted(false); acquire() }
        } catch (e: Exception) {
            null
        }

        val handler = Handler(Looper.getMainLooper())
        var done = false
        var resolving = false
        var listener: NsdManager.DiscoveryListener? = null

        fun finish(port: Int?) {
            if (done) return
            done = true
            try { listener?.let { nsd.stopServiceDiscovery(it) } } catch (_: Exception) {}
            try { lock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
            onResult(port)
        }

        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) { finish(null) }
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onServiceLost(s: NsdServiceInfo) {}
            override fun onServiceFound(s: NsdServiceInfo) {
                if (resolving || done) return
                resolving = true
                try {
                    nsd.resolveService(s, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, e: Int) { resolving = false }
                        override fun onServiceResolved(si: NsdServiceInfo) {
                            val p = si.port
                            handler.post { finish(if (p > 0) p else null) }
                        }
                    })
                } catch (e: Exception) {
                    resolving = false
                }
            }
        }

        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            finish(null)
            return
        }
        handler.postDelayed({ finish(null) }, timeoutMs)
    }

    private fun jsonStr(s: String?): String =
        if (s.isNullOrEmpty()) "null"
        else "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** 등록된 PC들에 JSON 본문을 POST한다(포트/토큰 전달용). */
    private fun postToPcs(ctx: Context, body: String) {
        val list = ctx.pcAddresses.split('\n', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (list.isEmpty()) return
        Thread {
            for (pc in list) {
                val target = if (pc.contains(":")) pc else "$pc:47800"
                try {
                    val conn = URL("http://$target/port").openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                    val ok = conn.responseCode in 200..299
                    conn.disconnect()
                    Logbook.add(ctx, if (ok) "PC 전송 성공: $target" else "PC 전송 실패($target)")
                } catch (e: Exception) {
                    Logbook.add(ctx, "PC 전송 실패($target): ${e.javaClass.simpleName}")
                }
            }
        }.start()
    }

    /** 현재 포트 + FCM 토큰을 등록된 PC로 전송(PC 주소 저장 시 즉시 연결용). */
    private fun pushToPcs(ctx: Context, ip: String?, port: Int?) {
        if (ip == null || port == null) return
        postToPcs(ctx, """{"ip":"$ip","port":$port,"token":${jsonStr(ctx.fcmToken)}}""")
    }

    /** FCM 토큰만 등록된 PC로 전송(토큰 갱신 시). */
    fun pushTokenToPcs(ctx: Context, token: String) {
        if (token.isBlank()) return
        postToPcs(ctx.applicationContext, """{"token":${jsonStr(token)}}""")
    }

    /** PC 주소 저장 직후 호출: 포트를 찾아 등록된 PC로 전송한다. */
    fun pushOnly(ctx: Context) {
        val app = ctx.applicationContext
        if (app.pcAddresses.isBlank()) return
        discoverPort(app) { port ->
            val ip = tailscaleIp()
            if (port == null) {
                Logbook.add(app, "PC 전송 보류 — 무선 포트 못 찾음(무선 디버깅 확인)")
            } else {
                pushToPcs(app, ip, port)
            }
        }
    }

    /** 스레드에서 현재 무선 포트를 동기적으로 구한다(PortRequestServer용). */
    fun currentPortBlocking(ctx: Context, timeoutMs: Long = 10000): Int? {
        ensureWirelessDebug(ctx)
        val latch = java.util.concurrent.CountDownLatch(1)
        val result = arrayOfNulls<Int>(1)
        discoverPort(ctx, 8000) { result[0] = it; latch.countDown() }
        try {
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Exception) {}
        return result[0]
    }
}
