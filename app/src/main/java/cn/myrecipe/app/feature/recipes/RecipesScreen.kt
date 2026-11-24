package cn.myrecipe.app.feature

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.myrecipe.app.core.database.*

@Composable fun RecipesScreen(vm: AppViewModel, snapshot: AppSnapshot, onOpen: (String) -> Unit, onNew: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val mode = prefs.first; val category = prefs.second?.takeIf { id -> snapshot.categories.any { it.id == id } }
    var categorySheet by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var ingredientsSheet by remember { mutableStateOf<List<RecipeIngredient>?>(null) }
    val sort by vm.container.preferences.sort.collectAsStateWithLifecycle(initialValue = RecipeSort.MANUAL to false)
    var sortMenu by remember { mutableStateOf(false) }
    val recipes = filterRecipes(snapshot, query, mode, category, sort.first, sort.second)
    val draggable = sort.first == RecipeSort.MANUAL && !selecting
    val saveOrder: (List<String>) -> Unit = { ids -> vm.run(onError = { vm.notify(it) }) { vm.container.repository.reorderRecipes(ids) } }
    val (drag, dragModifier) = rememberRecipeDragState(recipes, draggable, saveOrder)
    val orderedRecipes = if (!draggable) recipes else drag.ids.mapNotNull { id -> recipes.find { it.recipe.id == id } }.let { ordered -> ordered + recipes.filter { full -> ordered.none { it.recipe.id == full.recipe.id } } }
    Column(Modifier.fillMaxSize()) {
        PageTitle("我的菜谱") {
            Box {
                TextButton(onClick = { sortMenu = true }) { Text("排序") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    RecipeSort.entries.forEach { option ->
                        DropdownMenuItem(text = { Text((if (sort.first == option) "✓ " else "") + option.title) }, onClick = {
                            sortMenu = false
                            vm.run { vm.container.preferences.saveSort(option, sort.second) }
                        })
                    }
                    if (sort.first != RecipeSort.MANUAL) {
                        HorizontalDivider()
                        listOf(false to "正序", true to "倒序").forEach { (desc, label) ->
                            DropdownMenuItem(text = { Text((if (sort.second == desc) "✓ " else "") + label) }, onClick = {
                                sortMenu = false
                                vm.run { vm.container.preferences.saveSort(sort.first, desc) }
                            })
                        }
                    }
                }
            }
            IconButton(onClick = { selecting = !selecting; selected = emptySet() }, modifier = Modifier.testTag("multiSelect")) {
                Icon(if (selecting) Icons.Outlined.Close else Icons.Outlined.CheckCircle, if (selecting) "取消多选" else "多选", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            placeholder = { Text("搜索菜名、食材、标签、分类", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)) },
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            singleLine = true, shape = RoundedCornerShape(5.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("全部", "收藏", "最近使用").forEach { item ->
                FilterChip(selected = mode == item, onClick = { vm.run { vm.container.preferences.saveHome(item, category) } },
                    label = { Text(item) }, shape = RoundedCornerShape(4.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = journalLeaf,
                        selectedLabelColor = Color(0xfffff6de)))
            }
            TextButton(onClick = { categorySheet = true }) { Text(if (category == null) "全部分类 ▾" else "${categoryPath(category, snapshot.categories)} ▾") }
        }
        if (!selecting) Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${recipes.size} 道菜谱", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            StickerAction("新建菜谱", onNew, Modifier.testTag("newRecipe"))
        }
        if (selecting) Button(onClick = { ingredientsSheet = snapshot.recipes.filter { it.recipe.id in selected }.flatMap { it.ingredients } }, enabled = selected.isNotEmpty(), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { Text("将选中的 ${selected.size} 道菜加入购物清单") }
        LazyColumn(Modifier.weight(1f).then(dragModifier).testTag("recipeList"), state = drag.listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (recipes.isEmpty()) item { EmptyState(if (snapshot.recipes.isEmpty()) "自己的菜谱，从第一道开始" else "没有找到菜谱", if (snapshot.recipes.isEmpty()) "点「新建菜谱」，记下家里的拿手菜。" else "试试其他关键词或分类。") }
            items(orderedRecipes, key = { it.recipe.id }) { full ->
                val recipe = full.recipe
                val isSelected = selecting && recipe.id in selected
                val row = Modifier.fillMaxWidth().testTag("recipe-${recipe.id}").then(
                    if (draggable) Modifier.recipeDragItem(drag, recipe.id, recipes, saveOrder) else Modifier
                ).background(if (isSelected) Color(0x33576745) else Color.Transparent).clickable {
                    if (selecting) selected = if (isSelected) selected - recipe.id else selected + recipe.id
                    else onOpen(recipe.id)
                }
                Column(row) {
                    recipe.coverPath?.let { path ->
                        Cover(path, vm.container.images, Modifier.fillMaxWidth().height(148.dp), photoPrint = true)
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) Checkbox(isSelected, null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(categoryPath(recipe.categoryId, snapshot.categories), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(recipe.name + if (recipe.pinned) " · 置顶" else "", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                            val times = listOfNotNull(recipe.prepareMinutes?.let { "准备 ${it}分" }, recipe.cookMinutes?.let { "烹饪 ${it}分" })
                            if (times.isNotEmpty()) Text(times.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (full.tags.isNotEmpty()) Text(full.tags.joinToString("  ") { "#${it.name}" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        if (recipe.favorite) Text("★", Modifier.padding(horizontal = 6.dp), color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider(color = Color(0xffc4b393))
                }
            }
        }
    }
    if (categorySheet) CategorySheet(snapshot.categories, category, { categorySheet = false }) { id -> categorySheet = false; vm.run { vm.container.preferences.saveHome(mode, id) } }
    ingredientsSheet?.let { ingredients -> RecipeShoppingSheet(vm, ingredients, { ingredientsSheet = null }) { ingredientsSheet = null; selected = emptySet(); selecting = false } }
}

@Composable fun DetailScreen(vm: AppViewModel, snapshot: AppSnapshot, full: RecipeFull, onBack: () -> Unit, onEdit: () -> Unit, onKitchen: () -> Unit) {
    var deleting by remember { mutableStateOf(false) }; var shopping by remember { mutableStateOf(false) }
    val r = full.recipe
    Column {
        PageTitle("菜谱", onBack) { TextButton(onClick = onEdit) { Text("编辑") }; TextButton(onClick = { deleting = true }) { Text("删除") } }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (r.coverPath != null) item { Cover(r.coverPath, vm.container.images, Modifier.fillMaxWidth().height(210.dp)) }
            item { Text(r.name, style = MaterialTheme.typography.headlineLarge) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(r.favorite, { vm.run { vm.container.repository.toggle(r.id, favorite = !r.favorite) } }, { Text(if (r.favorite) "★ 已收藏" else "☆ 收藏") })
                FilterChip(r.pinned, { vm.run { vm.container.repository.toggle(r.id, pinned = !r.pinned) } }, { Text(if (r.pinned) "已置顶" else "置顶") })
            } }
            item { Text(categoryPath(r.categoryId, snapshot.categories)); if (full.tags.isNotEmpty()) Text(full.tags.joinToString("  ") { "#${it.name}" }, color = MaterialTheme.colorScheme.secondary) }
            if (r.prepareMinutes != null || r.cookMinutes != null) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOfNotNull(r.prepareMinutes?.let { "准备" to it }, r.cookMinutes?.let { "烹饪" to it }).forEach { (label, minutes) ->
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.padding(16.dp)) {
                                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${minutes}分钟", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            if (r.description.isNotBlank()) item { Text(r.description) }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("食材", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); TextButton(onClick = { shopping = true }, enabled = full.ingredients.isNotEmpty()) { Text("加入购物清单") } } }
            if (full.foodIngredients.isNotEmpty()) item { IngredientFlow(full.foodIngredients) }
            if (full.foodIngredients.isEmpty()) item { Text("尚未添加食材，可在编辑中补充。") }
            if (full.seasonings.isNotEmpty()) item { Section("调料备注"); IngredientFlow(full.seasonings) }
            if (r.preparation.isNotBlank()) item { Section("备菜"); Text(r.preparation) }
            item { Section("做法") }
            items(full.steps.sortedBy { it.sortOrder }, key = { it.id }) { step ->
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("步骤 ${step.sortOrder + 1}${if (step.heat == "未指定") "" else " · ${step.heat}"}", style = MaterialTheme.typography.titleMedium)
                    Text(step.content)
                    step.timerSeconds?.let { Text("参考计时 ${formatTime(it * 1000)}", color = MaterialTheme.colorScheme.primary) }
                } }
            }
            if (full.steps.isEmpty()) item { Text("尚未添加步骤，可在编辑中补充。") }
        }
        Button(onClick = onKitchen, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 52.dp)) { Text("开始做饭") }
    }
    if (deleting) Confirm("删除菜谱", "将“${r.name}”移入回收站？可以在设置中恢复。", { deleting = false }) { deleting = false; vm.run(onBack) { vm.container.repository.delete(r.id) } }
    if (shopping) RecipeShoppingSheet(vm, full.ingredients, { shopping = false }) { shopping = false }
}
