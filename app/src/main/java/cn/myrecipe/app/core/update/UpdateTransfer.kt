package cn.myrecipe.app.core.update

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.concurrent.CancellationException

internal data class TransferProgress(val source: String, val received: Long, val bytesPerSecond: Long, val note: String)

internal class UpdateTransfer(
    private val connect: (String, Long) -> HttpURLConnection = ::openHttps,
    private val millis: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    fun download(release: Release, sources: List<DownloadSource>, part: File, checkActive: () -> Unit, report: (TransferProgress) -> Unit) {
        var reason = ""
        var failure: Exception? = null
        for ((index, source) in sources.withIndex()) {
            checkActive()
            try {
                transferFile(release, source, part, index < sources.lastIndex, reason, checkActive, report)
                val digest = MessageDigest.getInstance("SHA-256")
                part.inputStream().buffered().use { stream ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        checkActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                if (!digest.digest().joinToString("") { "%02x".format(it) }.equals(release.sha256, true)) {
                    part.delete()
                    throw IOException("文件校验失败")
                }
                return
            } catch (e: CancellationException) { throw e }
            catch (e: IOException) {
                failure = e
                reason = "${source.title}：${e.localizedMessage ?: "连接失败"}"
                if (part.length() >= release.size) part.delete()
            }
        }
        throw IOException("下载未完成：$reason", failure)
    }

    private fun transferFile(release: Release, source: DownloadSource, part: File, hasNext: Boolean, reason: String,
                             checkActive: () -> Unit, report: (TransferProgress) -> Unit) {
        var pos = part.length()
        report(TransferProgress(source.title, pos, 0, reason))
        val conn = connect(source.url, pos)
        try {
            when (conn.responseCode) {
                200 -> pos = 0
                206 -> if (conn.getHeaderField("Content-Range") != "bytes $pos-${release.size - 1}/${release.size}") throw IOException("续传位置不匹配")
                else -> throw IOException("HTTP ${conn.responseCode}")
            }
            RandomAccessFile(part, "rw").use { file ->
                file.setLength(pos)
                file.seek(pos)
                var start = millis()
                var windowBytes = pos
                var lastReport = start
                conn.inputStream.use { stream ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        checkActive()
                        val n = stream.read(buffer)
                        if (n < 0) break
                        pos += n
                        if (pos > release.size) throw IOException("文件大小不符")
                        file.write(buffer, 0, n)
                        val now = millis()
                        val elapsed = (now - start).coerceAtLeast(1L)
                        val speed = (pos - windowBytes) * 1000 / elapsed
                        if (now - lastReport >= 250 || pos == release.size) {
                            report(TransferProgress(source.title, pos, speed, reason))
                            lastReport = now
                        }
                        if (elapsed >= 8000) {
                            if (hasNext && pos < release.size && speed < 128 * 1024) throw IOException("持续慢速，正在换源")
                            start = now
                            windowBytes = pos
                        }
                    }
                }
            }
            if (pos != release.size) throw IOException("传输中断")
        } finally { conn.disconnect() }
    }
}
