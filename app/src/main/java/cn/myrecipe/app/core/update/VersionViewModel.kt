package cn.myrecipe.app.core.update

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal data class VersionState(
    val release: Release? = null, val busy: Boolean = false, val status: String = "",
    val progress: TransferProgress? = null, val ready: File? = null
)

internal class VersionViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("update_sources", 0)
    private val manager = app.packageManager
    private val applicationId = app.packageName
    @Suppress("DEPRECATION")
    private val signatureFlags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION")
    private val installed = manager.getPackageInfo(applicationId, signatureFlags)
    val currentVersion = installed.versionName.orEmpty()
    val mirror: String get() = prefs.getString("mirror", "").orEmpty()
    private val mutable = MutableStateFlow(VersionState())
    val state = mutable.asStateFlow()
    private var task: Job? = null

    fun setMirror(value: String) {
        try {
            val base = mirrorBase(value)
            prefs.edit().putString("mirror", base).apply()
            mutable.update { it.copy(status = "下载源已保存") }
        } catch (_: Exception) {
            mutable.update { it.copy(status = "请输入有效的 HTTPS 下载目录") }
        }
    }
    fun check() = work {
        val source = mirror
        val release = withContext(Dispatchers.IO) { latestRelease(source) { ensureActive() } }
        val newer = compareVersions(release.version, currentVersion.substringBefore('-')) > 0
        mutable.update { VersionState(release = release, busy = true, status = if (newer) "发现新版本" else "当前已是最新版本") }
    }
    fun newer(release: Release) = compareVersions(release.version, currentVersion.substringBefore('-')) > 0
    fun download() {
        val release = mutable.value.release ?: return
        if (!newer(release)) return
        work {
            val sources = release.sources(mirror)
            val file = withContext(Dispatchers.IO) {
                val dir = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
                val part = File.createTempFile("download-", ".part", dir)
                try {
                    UpdateTransfer().download(release, sources, part, { ensureActive() }) { progress ->
                        mutable.update { it.copy(progress = progress, status = progress.note) }
                    }
                    ensureActive()
                    val dest = File(dir, release.file)
                    check(part.renameTo(dest)) { "无法保存安装包" }
                    try {
                        verifyPackage(dest, release)
                    } catch (e: Exception) {
                        dest.delete()
                        throw e
                    }
                    dest
                } finally { part.delete() }
            }
            mutable.update { it.copy(ready = file, progress = null, status = "安装包和签名已校验") }
        }
    }
    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File, release: Release) {
        val apk = manager.getPackageArchiveInfo(file.absolutePath, signatureFlags) ?: error("无法读取安装包")
        require(apk.packageName == applicationId && apk.versionName == release.version &&
            PackageInfoCompat.getLongVersionCode(apk) > PackageInfoCompat.getLongVersionCode(installed)) { "安装包与本机应用或版本不匹配" }
        fun certificates(info: PackageInfo): Set<String> {
            val values = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return values.orEmpty().map { it.toCharsString() }.toSet()
        }
        val expected = certificates(installed)
        require(expected.isNotEmpty() && certificates(apk) == expected) { "安装包签名与当前安装版本不同" }
    }
    private fun work(block: suspend () -> Unit) {
        if (task?.isActive == true) return
        mutable.update { it.copy(busy = true, status = "正在连接…", progress = null, ready = null) }
        task = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { mutable.update { it.copy(status = "已取消", progress = null) }; throw e }
            catch (e: Exception) { mutable.update { it.copy(status = e.localizedMessage ?: "更新失败", progress = null) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun cancel() { task?.cancel() }
}
