package cn.myrecipe.app.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.*
import cn.myrecipe.app.core.timer.remainingMillis
import kotlinx.coroutines.delay

@Composable fun RecipeApp(vm: AppViewModel, timerTarget: Pair<String, String>?, consumeTarget: () -> Unit) {
    val updates: cn.myrecipe.app.core.update.VersionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val nav = rememberNavController()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "recipes"
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(snapshot.timer) { if (snapshot.timer?.state == "RUNNING") while (true) { now = android.os.SystemClock.elapsedRealtime(); delay(1000) } }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.run { vm.container.timer.reconcile() } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        vm.events.collect { notice ->
            val result = snackbar.showSnackbar(notice.text, actionLabel = if (notice.undo != null) "撤销" else null, duration = if (notice.undo != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.run { notice.undo?.invoke() }
        }
    }
    LaunchedEffect(timerTarget, snapshot.recipes) {
        timerTarget?.let { target ->
            if (snapshot.recipes.any { it.recipe.id == target.first }) { nav.navigate("kitchen/${target.first}?step=${target.second}") { launchSingleTop = true }; consumeTarget() }
            else if (snapshot.timer?.recipeId == target.first) { vm.notify("原菜谱已删除，可在计时条上管理计时器"); consumeTarget() }
        }
    }
    JournalBook {
        Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            snapshot.timer?.let { timer ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("${timer.recipeName} · ${when(timer.state) { "RUNNING" -> formatTime(remainingMillis(timer, now)); "PAUSED" -> "已暂停 ${formatTime(timer.remainingMillis)}"; "FINISHED" -> "计时结束"; else -> "计时已中断" }}", Modifier.weight(1f).clickable {
                            if (snapshot.recipes.any { it.recipe.id == timer.recipeId }) nav.navigate("kitchen/${timer.recipeId}?step=${timer.stepId}")
                            else vm.notify("原菜谱已删除，计时仍可取消")
                        }.padding(vertical = 12.dp))
                        TextButton(onClick = { vm.run { if (timer.state == "FINISHED") vm.container.timer.acknowledge(timer.token) else vm.container.timer.cancel() } }) { Text(if (timer.state == "FINISHED") "确认" else "取消") }
                    }
                }
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            NavHost(nav, startDestination = "recipes", modifier = Modifier.weight(1f)) {
                composable("recipes") {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().testTag("mainPager"), beyondViewportPageCount = 1) { page ->
                        when (page) {
                            0 -> RecipesScreen(vm, snapshot, onOpen = { nav.navigate("detail/$it") }, onNew = { vm.openEditor(null); nav.navigate("editor/new") })
                            1 -> ShoppingScreen(vm, snapshot)
                            2 -> SettingsScreen(vm, onTaxonomy = { nav.navigate("taxonomy") }, onRestored = { scope.launch { pager.scrollToPage(0) } },
                                onVersion = { nav.navigate("version") }, onTrash = { nav.navigate("trash") })
                        }
                    }
                }
                composable("version") { VersionScreen(updates) { nav.popBackStack() } }
                composable("trash") { TrashScreen(vm, snapshot.trash) { nav.popBackStack() } }
                composable("taxonomy") { TaxonomyScreen(vm, snapshot) { nav.popBackStack() } }
                composable("detail/{id}") { back ->
                    val full = snapshot.recipes.find { it.recipe.id == back.arguments?.getString("id") }
                    if (full == null) { PageTitle("菜谱", { nav.popBackStack() }); EmptyState("菜谱不存在", "请返回菜谱列表。") }
                    else DetailScreen(vm, snapshot, full, onBack = { nav.popBackStack() }, onEdit = { vm.openEditor(full); nav.navigate("editor/${full.recipe.id}") }, onKitchen = { vm.run { vm.container.repository.markUsed(full.recipe.id) }; nav.navigate("kitchen/${full.recipe.id}?step=") })
                }
                composable("editor/{id}") { back ->
                    val id = back.arguments?.getString("id")
                    LaunchedEffect(id, snapshot.recipes) {
                        if (vm.editor == null) {
                            if (id == "new") vm.openEditor(null)
                            else snapshot.recipes.find { it.recipe.id == id }?.let { vm.openEditor(it) }
                        }
                    }
                    vm.editor?.let { EditorScreen(vm, snapshot, it) { vm.clearEditor(); nav.popBackStack() } }
                }
                composable("kitchen/{id}?step={step}") { back ->
                    val full = snapshot.recipes.find { it.recipe.id == back.arguments?.getString("id") }
                    if (full == null) { PageTitle("厨房模式", { nav.popBackStack() }); EmptyState("菜谱不存在", "请返回菜谱列表。") }
                    else KitchenScreen(vm, full, back.arguments?.getString("step"), snapshot.timer) { nav.popBackStack() }
                }
            }
            if (route == "recipes") {
                JournalTabs(pager.currentPage, (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 2f)) {
                    scope.launch { pager.animateScrollToPage(it) }
                }
            }
        }
        }
    }
}
fun formatTime(millis: Long): String { val seconds = (millis.coerceAtLeast(0) + 999) / 1000; return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60) }
