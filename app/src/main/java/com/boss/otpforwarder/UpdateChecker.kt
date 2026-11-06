package com.boss.otpforwarder

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases를 확인해 새 버전이 있으면 APK를 내려받아 설치한다.
 * 대상 저장소는 BuildConfig.UPDATE_REPO (owner/repo).
 */
object UpdateChecker {

    private val scope = CoroutineScope(Dispatchers.IO)

    data class Release(val version: String, val notes: String, val apkUrl: String, val apkName: String)

    /** 최신 릴리즈 확인. 새 버전이면 Release, 아니면 null. message에 상태 설명. */
    fun check(ctx: Context, onResult: (Release?, String) -> Unit) {
        scope.launch {
            try {
                val repo = BuildConfig.UPDATE_REPO
                val json = httpGet("https://api.github.com/repos/$repo/releases/latest")
                val obj = JSONObject(json)
                val tag = obj.optString("tag_name").trimStart('v', 'V')
                val notes = obj.optString("body").take(300)
                val assets = obj.optJSONArray("assets")
                var apkUrl: String? = null
                var apkName = "update.apk"
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk", true)) {
                            apkUrl = a.optString("browser_download_url")
                            apkName = a.optString("name")
                            break
                        }
                    }
                }
                val current = BuildConfig.VERSION_NAME
                withContext(Dispatchers.Main) {
                    when {
                        apkUrl == null -> onResult(null, "릴리즈에 APK가 없습니다")
                        isNewer(tag, current) ->
                            onResult(Release(tag, notes, apkUrl!!, apkName), "새 버전 $tag 있음 (현재 $current)")
                        else -> onResult(null, "이미 최신입니다 (v$current)")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(null, "업데이트 확인 실패: ${e.message}")
                }
            }
        }
    }

    /** APK 다운로드 후 설치 화면을 띄운다. */
    fun downloadAndInstall(ctx: Context, release: Release, onStatus: (String) -> Unit) {
        val app = ctx.applicationContext
        scope.launch {
            try {
                withContext(Dispatchers.Main) { onStatus("다운로드 중…") }
                val dir = File(app.cacheDir, "updates").apply { mkdirs() }
                val outFile = File(dir, release.apkName)
                downloadTo(release.apkUrl, outFile)
                withContext(Dispatchers.Main) {
                    onStatus("설치 화면을 엽니다")
                    installApk(app, outFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onStatus("다운로드 실패: ${e.message}") }
            }
        }
    }

    private fun installApk(ctx: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    // "1.0.3" > "1.0.2" 식의 숫자 비교
    private fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split(".").mapNotNull { it.toIntOrNull() }
        val b = current.split(".").mapNotNull { it.toIntOrNull() }
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "OtpForwarder")
            connectTimeout = 10000
            readTimeout = 15000
        }
        conn.inputStream.use { return it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun downloadTo(urlStr: String, out: File) {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "OtpForwarder")
            connectTimeout = 10000
            readTimeout = 30000
        }
        conn.inputStream.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
