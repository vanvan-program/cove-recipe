package cn.myrecipe.app.feature

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate

@Composable fun SettingsScreen(vm: AppViewModel, onTaxonomy: () -> Unit, onRestored: () -> Unit, onVersion: () -> Unit, onTrash: () -> Unit) {
    val context = LocalContext.current; val lifecycle = LocalLifecycleOwner.current
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }; var reminderStatus by remember { mutableStateOf(vm.container.timer.permissionMessage()) }
    DisposableEffect(lifecycle) { val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reminderStatus = vm.container.timer.permissionMessage() }; lifecycle.lifecycle.addObserver(observer); onDispose { lifecycle.lifecycle.removeObserver(observer) } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) vm.export(uri) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        PageTitle("设置")
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            StickyNote("整理菜谱", NotePaper.Wheat) {
                NoteAction("分类与标签管理", onClick = onTaxonomy)
                NoteAction("回收站", onClick = onTrash)
            }
            StickyNote("导出与备份", NotePaper.Apricot) {
                Text("菜谱、图片和购物清单只保存在本机。导出 ZIP 后可自行保管或迁移；卸载应用会删除本机数据，请先备份。", style = MaterialTheme.typography.bodyMedium)
                NoteAction("导出完整备份", enabled = !vm.busy) { export.launch("CoveRecipe-${LocalDate.now()}.zip") }
                NoteAction("从备份恢复", enabled = !vm.busy) {
                    restore.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
                }
                Text("恢复会替换全部菜谱、分类、标签和购物清单。计时器不备份。", style = MaterialTheme.typography.bodySmall)
            }
            StickyNote("厨房计时提醒", NotePaper.Sage) {
                Text(reminderStatus ?: "精确闹钟与通知已允许", style = MaterialTheme.typography.bodyMedium)
                if (Build.VERSION.SDK_INT >= 31) NoteAction("精确闹钟设置") {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                }
                NoteAction("通知设置") {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
                Text("锁屏和切后台继续计时；重启手机后旧计时中断。系统强行停止 App 后不保证提醒。", style = MaterialTheme.typography.bodySmall)
            }
            StickyNote("关于", NotePaper.Rose) {
                Text("自己的家庭菜谱本\n离线 · 无账号 · 无广告", style = MaterialTheme.typography.bodyMedium)
                NoteAction("关于版本", onClick = onVersion)
            }
        }
    }
    pendingRestore?.let { uri -> Confirm("恢复并覆盖当前数据？", "建议先导出当前备份。只有文件完整校验通过才会替换；成功恢复将取消当前计时。", { pendingRestore = null }) { pendingRestore = null; vm.restore(uri, onRestored) } }
}
