package cn.myrecipe.app

import cn.myrecipe.app.core.update.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateTest {
    @get:Rule val temp = TemporaryFolder()
    private val bytes = ByteArray(100000) { (it % 251).toByte() }
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val release = Release("1.5.0", "v1.5.0", "CoveRecipe-1.5.0.apk", bytes.size.toLong(), hash, false)
    private val sources = listOf(DownloadSource("A", "one"), DownloadSource("B", "two"))

    private fun response(data: ByteArray, code: Int = 200, range: String? = null): HttpURLConnection =
        object : HttpURLConnection(URL("https://example.invalid")) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy() = false
            override fun getResponseCode() = code
            override fun getInputStream() = data.inputStream()
            override fun getHeaderField(name: String?) = if (name == "Content-Range") range else null
        }

    @Test fun checkUsesAnActualReleaseFileWhenGithubApiIsUnavailable() {
        val attempted = mutableListOf<String>()
        val result = latestRelease("", read = { address, _ ->
            attempted += address
            if (address.startsWith(RELEASE_API)) throw IOException("HTTP 403")
            release
        }) {}
        assertEquals(release, result)
        assertEquals(listOf("$RELEASE_API?per_page=100",
            "${ACCELERATOR}https://github.com/$REPOSITORY/releases/latest/download/releases.json"), attempted)
    }

    @Test fun slowConnectionSwitchesToMirrorAndResumesAtLastByteWritten() {
        val part = temp.newFile()
        val offsets = mutableListOf<Long>()
        var time = 0L
        val updater = UpdateTransfer({ address, offset ->
            offsets += offset
            if (address == "one") response(bytes)
            else response(bytes.copyOfRange(offset.toInt(), bytes.size), 206, "bytes $offset-99999/100000")
        }, { time.also { time += 9000 } })
        val progress = mutableListOf<TransferProgress>()
        updater.download(release, sources, part, {}, { progress += it })
        assertEquals(listOf(0L, 65536L), offsets)
        assertTrue(progress.any { it.source == "B" && it.note.contains("慢速") })
        assertArrayEquals(bytes, part.readBytes())
    }

    @Test fun nonRangeServerRestartsFullFileAfterShortResponse() {
        val part = temp.newFile()
        val offsets = mutableListOf<Long>()
        UpdateTransfer({ address, offset ->
            offsets += offset
            response(if (address == "one") bytes.copyOf(200) else bytes)
        }).download(release, sources, part, {}, {})
        assertEquals(listOf(0L, 200L), offsets)
        assertArrayEquals(bytes, part.readBytes())
    }

    @Test fun wrongDigestCannotProduceInstallableFile() {
        val part = temp.newFile()
        val offsets = mutableListOf<Long>()
        assertThrows(IOException::class.java) {
            UpdateTransfer({ _, offset ->
                offsets += offset
                response(ByteArray(bytes.size))
            }).download(release, sources, part, {}, {})
        }
        assertEquals(listOf(0L, 0L), offsets)
        assertFalse(part.exists())
    }

    @Test fun releaseFeedRequiresAnUploadedVersionedApkAndChoosesNumericallyNewest() {
        fun entry(version: String, digest: String = hash, draft: Boolean = false) =
            """{"tag_name":"v$version","draft":$draft,"prerelease":true,"assets":[{"name":"CoveRecipe-$version.apk","size":100000,"digest":"sha256:$digest","state":"uploaded"}]}"""
        val latest = parseReleases(listOf(entry("1.2.0"), entry("1.10.0"), entry("2.0.0", "bad"), entry("3.0.0", draft = true)).joinToString(",", "[", "]"))
        assertEquals("1.10.0", latest.version)
        assertTrue(latest.preview)
        val mirror = mirrorBase(" https://mirror.example/apk ")
        assertEquals("https://mirror.example/apk/CoveRecipe-1.10.0.apk", latest.sources(mirror).first().url)
        assertThrows(IllegalArgumentException::class.java) { mirrorBase("http://mirror.example") }
    }
}

