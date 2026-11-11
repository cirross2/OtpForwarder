package com.boss.otpforwarder

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import com.boss.otpforwarder.Prefs.pcAddresses
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 재부팅 후 바뀌는 "무선 디버깅" 연결 포트를 mDNS로 찾아
 * 테일스케일 IP와 묶어 텔레그램으로 안내한다.
 *
 * 폰이 스스로 광고하는 `_adb-tls-connect._tcp` 서비스를
 * 같은 기기의 NsdManager로 조회한다. (멀티캐스트 락으로 신뢰성 보강)
 */
object WirelessInfo {

    private const val SERVICE_TYPE = "_adb-tls-connect._tcp."

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

    /** mDNS로 무선 디버깅 연결 포트를 찾아 콜백한다. 못 찾으면 null. */
    fun discoverPort(ctx: Context, timeoutMs: Long = 8000, onResult: (Int?) -> Unit) {
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

    private suspend fun discoverPortSuspend(ctx: Context, timeoutMs: Long = 8000): Int? =
        suspendCancellableCoroutine { cont ->
            discoverPort(ctx, timeoutMs) { if (cont.isActive) cont.resume(it) }
        }

    private fun buildMessage(port: Int?, ip: String?): String = buildString {
        append("📡 무선 디버깅 포트 안내\n")
        when {
            port != null && ip != null -> {
                append("adb connect $ip:$port\n")
                append("(IP $ip · 포트 $port)")
            }
            port != null -> {
                append("포트: $port\n")
                append("⚠ 테일스케일 IP를 못 읽음 — 폰에서 테일스케일이 켜져 있는지 확인")
            }
            else -> {
                append("⚠ 무선 디버깅 포트를 찾지 못했습니다.\n")
                append("폰에서 '무선 디버깅'이 켜져 있는지 확인해 주세요.")
                if (ip != null) append("\n(테일스케일 IP: $ip)")
            }
        }
    }

    /** 등록된 PC들에 바뀐 포트를 직접 전송한다(원격도 복붙 없이 자동 연결). */
    private fun pushToPcs(ctx: Context, ip: String?, port: Int?) {
        if (ip == null || port == null) return
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
                    conn.outputStream.use { it.write("""{"ip":"$ip","port":$port}""".toByteArray()) }
                    val ok = conn.responseCode in 200..299
                    conn.disconnect()
                    Logbook.add(ctx, if (ok) "PC 전송 성공: $target" else "PC 전송 실패($target)")
                } catch (e: Exception) {
                    Logbook.add(ctx, "PC 전송 실패($target): ${e.javaClass.simpleName}")
                }
            }
        }.start()
    }

    /** PC 주소 저장 직후 호출: 텔레그램 없이 등록된 PC로만 포트를 전송한다. */
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

    /** UI 버튼용: 즉시 조회 후 안내 전송 + PC 전송. */
    fun announce(ctx: Context) {
        val app = ctx.applicationContext
        discoverPort(app) { port ->
            val ip = tailscaleIp()
            Forwarder.notify(app, buildMessage(port, ip))
            pushToPcs(app, ip, port)
            Logbook.add(app, "무선 포트 안내 전송(port=${port ?: "-"}, ip=${ip ?: "-"})")
        }
    }

    /** 부팅 워커용: 완료까지 기다리는 suspend형. */
    suspend fun announceSuspend(ctx: Context) {
        val app = ctx.applicationContext
        val port = discoverPortSuspend(app)
        val ip = tailscaleIp()
        Forwarder.notify(app, buildMessage(port, ip))
        pushToPcs(app, ip, port)
        Logbook.add(app, "부팅 후 무선 포트 안내 전송(port=${port ?: "-"}, ip=${ip ?: "-"})")
    }
}
