package cn.myrecipe.app.feature

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.myrecipe.app.core.database.*
import cn.myrecipe.app.core.timer.remainingMillis
import kotlinx.coroutines.delay

@Composable fun KitchenScreen(vm: AppViewModel, full: RecipeFull, initialStep: String?, timer: ActiveTimer?, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    DisposableEffect(activity) { activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    val steps = full.steps.sortedBy { it.sortOrder }
    var index by rememberSaveable(full.recipe.id, initialStep) { mutableIntStateOf(steps.indexOfFirst { it.id == initialStep }.coerceAtLeast(0)) }
    index = index.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(1000) } }
    var ingredients by remember { mutableStateOf(false) }; var editingSeconds by remember { mutableStateOf<Long?>(null) }; var editingActive by remember { mutableStateOf(false) }
    var overrideSeconds by rememberSaveable(full.recipe.id, index) { mutableStateOf<Long?>(null) }
    var replaceSeconds by remember { mutableStateOf<Long?>(null) }; var permissions by remember { mutableStateOf(false) }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.notify(if (granted) "通知已允许，请再次开始计时" else "通知未允许，后台提醒不可用") }
    fun start(step: RecipeStep, seconds: Long, replace: Boolean = false) {
        if (vm.container.timer.permissionMessage() != null) { permissions = true; return }
        if (!replace && timer?.state in listOf("RUNNING", "PAUSED")) { replaceSeconds = seconds; return }
        vm.run { vm.container.timer.start(full.recipe, step, seconds, replace) }
    }
    Column(Modifier.fillMaxSize()) {
        PageTitle("厨房模式", onBack) { TextButton(onClick = { ingredients = true }) { Text("查看食材") } }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(full.recipe.name, style = MaterialTheme.typography.headlineMedium)
            if (steps.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.forEachIndexed { i, _ -> Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)) }
            }
            if (steps.isEmpty()) EmptyState("还没有做法步骤", "退出厨房，在编辑中添加步骤后即可使用步骤计时。")
            else {
                val step = steps[index]; val sameTimer = timer?.takeIf { it.stepId == step.id && it.recipeId == full.recipe.id }
                val duration = overrideSeconds ?: step.timerSeconds ?: 60
                Text("第 ${index + 1} / ${steps.size} 步", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                if (step.heat != "未指定") SuggestionChip(onClick = {}, label = { Text("火候：${step.heat}", fontSize = 20.sp) })
                Text(step.content, fontSize = 28.sp, lineHeight = 42.sp, fontFamily = FontFamily.Serif)
                Row { Text("参考计时 ${formatTime(duration * 1000)}", Modifier.weight(1f).padding(top = 12.dp)); TextButton(onClick = { editingActive = false; editingSeconds = duration }) { Text("修改") } }
                if (sameTimer != null && sameTimer.state in listOf("RUNNING", "PAUSED")) {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (sameTimer.state == "PAUSED") "计时已暂停" else "计时中", color = MaterialTheme.colorScheme.primary)
                            Text(formatTime(remainingMillis(sameTimer, now)), fontSize = 52.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(if (sameTimer.state == "PAUSED") "计时已暂停" else "计时中；翻步骤或退出厨房不会停止")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { vm.run { if (sameTimer.state == "RUNNING") vm.container.timer.pause() else vm.container.timer.resume() } }, modifier = Modifier.weight(1f)) { Text(if (sameTimer.state == "RUNNING") "暂停" else "继续") }
                        OutlinedButton(onClick = { editingActive = true; editingSeconds = ((remainingMillis(sameTimer, now) + 999) / 1000).coerceAtLeast(1) }, modifier = Modifier.weight(1f)) { Text("改剩余时间") }
                    }
                    OutlinedButton(onClick = { vm.run { vm.container.timer.cancel() } }, modifier = Modifier.fillMaxWidth()) { Text("取消计时") }
                } else {
                    if (sameTimer?.state == "FINISHED") {
                        Text("计时已结束", color = MaterialTheme.colorScheme.primary)
                        Button(onClick = { vm.run { vm.container.timer.acknowledge(sameTimer.token) } }, modifier = Modifier.fillMaxWidth()) { Text("确认") }
                    }
                    if (sameTimer?.state == "INTERRUPTED") Text("计时已中断，请重新开始")
                    Button(onClick = { start(step, duration) }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text("开始计时", fontSize = 22.sp) }
                }
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = { index-- }, enabled = index > 0, modifier = Modifier.weight(1f).heightIn(min = 60.dp)) { Text("上一步", fontSize = 20.sp) }
            Button(onClick = { index++ }, enabled = index < steps.lastIndex, modifier = Modifier.weight(1f).heightIn(min = 60.dp)) { Text("下一步", fontSize = 20.sp) }
        }
    }
    if (ingredients) AlertDialog(onDismissRequest = { ingredients = false }, title = { Text("食材") }, text = { Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text("食材", style = MaterialTheme.typography.titleLarge); if (full.foodIngredients.isEmpty()) Text("暂无食材") else IngredientFlow(full.foodIngredients, large = true); if (full.seasonings.isNotEmpty()) { Text("调料备注", style = MaterialTheme.typography.titleLarge); IngredientFlow(full.seasonings, large = true) }; if (full.recipe.preparation.isNotBlank()) { Text("备菜", style = MaterialTheme.typography.titleLarge); Text(full.recipe.preparation, fontSize = 22.sp); HorizontalDivider() } } }, confirmButton = { TextButton(onClick = { ingredients = false }) { Text("返回步骤") } })
    editingSeconds?.let { seconds -> TimeSheet(seconds, { editingSeconds = null }) { changed -> if (editingActive) vm.run { vm.container.timer.change(changed) } else overrideSeconds = changed; editingSeconds = null } }
    replaceSeconds?.let { seconds -> Confirm("替换当前计时器？", "“${timer?.recipeName}”的计时尚未结束，替换后原计时会取消。", { replaceSeconds = null }) { replaceSeconds = null; steps.getOrNull(index)?.let { start(it, seconds, true) } } }
    if (permissions) AlertDialog(onDismissRequest = { permissions = false }, title = { Text("开启后台计时提醒") }, text = { Text("计时到点提醒需要精确闹钟和通知权限。授权后返回，再次点击开始计时。当前：${vm.container.timer.permissionMessage() ?: "权限已具备"}") }, confirmButton = { Column {
        if (Build.VERSION.SDK_INT >= 31) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())) }) { Text("允许精确闹钟") }
        TextButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text("开启通知") }
        TextButton(onClick = { permissions = false }) { Text("返回") }
    } })
}
