package cn.myrecipe.app.core.update

import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal const val REPOSITORY = "vanvan-program/cove-recipe"
internal const val RELEASE_API = "https://api.github.com/repos/$REPOSITORY/releases"
internal const val ACCELERATOR = "https://ghfast.top/"
internal const val MAX_APK = 200L * 1024 * 1024

internal data class Release(val version: String, val tag: String, val file: String, val size: Long, val sha256: String, val preview: Boolean) {
    val url get() = "https://github.com/$REPOSITORY/releases/download/$tag/$file"
}

internal fun compareVersions(a: String, b: String): Int {
    val left = a.split('.').map { it.toInt() }
    val right = b.split('.').map { it.toInt() }
    return left.zip(right).firstOrNull { it.first != it.second }?.let { it.first.compareTo(it.second) } ?: 0
}

internal fun parseReleases(json: String): Release {
    val releases = JSONArray(json)
    val versions = mutableListOf<Release>()
    for (i in 0 until releases.length()) {
        val item = releases.getJSONObject(i)
        if (item.optBoolean("draft")) continue
        val tag = item.optString("tag_name")
        val version = tag.removePrefix("v")
        if (!Regex("""\d{1,7}\.\d{1,7}\.\d{1,7}""").matches(version)) continue
        val assets = item.optJSONArray("assets") ?: continue
        for (j in 0 until assets.length()) {
            val asset = assets.getJSONObject(j)
            val file = asset.optString("name")
            val size = asset.optLong("size")
            val hash = asset.optString("digest").removePrefix("sha256:").lowercase()
            if (file == "CoveRecipe-$version.apk" && size in 1..MAX_APK && hash.matches(Regex("[0-9a-f]{64}")) && asset.optString("state") == "uploaded") {
                versions += Release(version, tag, file, size, hash, item.optBoolean("prerelease"))
            }
        }
    }
    return versions.maxWithOrNull { a, b -> compareVersions(a.version, b.version) } ?: throw IOException("没有找到附带校验值的安装包")
}

internal fun mirrorBase(input: String): String {
    if (input.isBlank()) return ""
    val uri = java.net.URI(input.trim())
    require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "请输入 HTTPS 下载目录" }
    return uri.toASCIIString().trimEnd('/') + "/"
}

internal data class DownloadSource(val title: String, val url: String)
internal fun Release.sources(mirror: String) = buildList {
    if (mirror.isNotEmpty()) add(DownloadSource("国内自建源", mirror + file))
    add(DownloadSource("GitHub 官方源", url))
    add(DownloadSource("GHFast 国内加速", ACCELERATOR + url))
}

/** Follow HTTPS redirects explicitly so a mirror cannot silently downgrade to cleartext. */
internal fun openHttps(address: String, offset: Long = 0L): HttpURLConnection {
    var next = URL(address)
    repeat(6) {
        if (next.protocol != "https" || next.userInfo != null) throw IOException("来源地址无效")
        val c = (next.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "CoveRecipe-Android")
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            if (c.responseCode !in listOf(301, 302, 303, 307, 308)) return c
            next = URL(next, c.getHeaderField("Location") ?: throw IOException("无效的重定向"))
        } catch (e: Exception) {
            c.disconnect()
            throw e
        }
        c.disconnect()
    }
    throw IOException("来源重定向次数过多")
}

internal fun latestRelease(mirror: String, checkActive: () -> Unit): Release {
    val feeds = buildList {
        add("$RELEASE_API?per_page=100")
        if (mirror.isNotEmpty()) add(mirror + "releases.json")
        add(ACCELERATOR + RELEASE_API + "?per_page=100")
    }
    var failure: Exception? = null
    for (feed in feeds) {
        checkActive()
        try {
            val conn = openHttps(feed)
            try {
                if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                val data = java.io.ByteArrayOutputStream()
                conn.inputStream.use { stream ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        checkActive()
                        val n = stream.read(buffer)
                        if (n < 0) break
                        if (data.size() + n > 4 * 1024 * 1024) throw IOException("发布列表过大")
                        data.write(buffer, 0, n)
                    }
                }
                return parseReleases(data.toString("UTF-8"))
            } finally { conn.disconnect() }
        } catch (e: java.util.concurrent.CancellationException) { throw e }
        catch (e: Exception) { failure = e }
    }
    throw IOException("无法检查更新，请稍后重试。", failure)
}
