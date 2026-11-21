package cn.myrecipe.app.feature

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.myrecipe.app.core.update.VersionViewModel
import java.io.File

internal fun openInstaller(context: Context, apk: File): Boolean {
    if (!context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)))
        return false
    }
    require(apk.isFile) { "安装包已被清理，请重新下载" }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", apk)
    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    return true
}

@Composable internal fun VersionScreen(vm: VersionViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var mirror by rememberSaveable { mutableStateOf(vm.mirror) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    var installMessage by remember { mutableStateOf("") }
    Column {
        PageTitle("关于版本", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section("Cove Recipe")
            Text("当前版本 v" + vm.currentVersion, style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = { installMessage = ""; vm.check() }, enabled = !state.busy) { Text("检查更新") }
            state.release?.let { release ->
                Text("发布版本 v" + release.version + if (release.preview) " · 预发行版" else " · 正式版")
                if (vm.newer(release) && state.ready == null) {
                    Button(onClick = { installMessage = ""; vm.download() }, enabled = !state.busy) { Text("下载更新") }
                }
                state.progress?.let { p ->
                    LinearProgressIndicator(progress = { (p.received.toFloat() / release.size).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${p.source} · ${p.received / 1024} / ${release.size / 1024} KiB · ${p.bytesPerSecond / 1024} KiB/s")
                }
            }
            if (state.status.isNotBlank()) Text(state.status)
            if (state.busy) TextButton(onClick = vm::cancel) { Text("取消") }
            state.ready?.let { file ->
                Button(onClick = {
                    try { installMessage = if (openInstaller(context, file)) "" else "允许此来源后，返回这里点击安装更新。" }
                    catch (e: Exception) { installMessage = e.localizedMessage ?: "无法打开系统安装器" }
                }) { Text("安装更新") }
                if (installMessage.isNotBlank()) Text(installMessage)
            }
            HorizontalDivider()
            TextButton(onClick = { settings = !settings }) { Text("下载源设置") }
            if (settings) {
                Text("未配置自建源时先查 GitHub，连接失败会改用备用版本清单。下载超时或连续 8 秒均速低于 128 KiB/s 时会换源，支持断点的来源会续传。")
                OutlinedTextField(value = mirror, onValueChange = { mirror = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy,
                    label = { Text("国内自建下载目录（可选）") }, placeholder = { Text("https://你的域名/apk/") })
                Text("配置后优先从此目录下载同名 APK；也可在目录提供 GitHub 格式的 releases.json 用于备用版本检查。", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { vm.setMirror(mirror) }, enabled = !state.busy) { Text("保存下载源") }
            }
            TextButton(onClick = { history = !history }) { Text("版本更新目录") }
            if (history) {
                Text("1.5.1　恢复皮革封面、纸纹和固定页签，修复备用线路检查更新。")
                Text("1.5.0　菜谱排序与回收站、步骤滚轮、购物勾选和应用内更新。")
                Text("1.4.0　菜谱拖动排序、调料和购物来源。")
                Text("1.3.0　计时滚轮和到点震动确认。")
                Text("1.2.0　厨房手账界面与系统倒计时通知。")
                Text("1.1.0　备菜说明、滑动切页和分类快速创建。")
            }
        }
    }
}
