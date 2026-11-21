package cn.myrecipe.app.feature

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.myrecipe.app.core.database.*
import kotlinx.coroutines.launch

@Composable fun EditorScreen(vm: AppViewModel, snapshot: AppSnapshot, draft: EditorDraft, onBack: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var focusIngredient by remember { mutableStateOf<String?>(null) }
    var timingStep by remember { mutableStateOf<String?>(null) }
    var exiting by remember { mutableStateOf(false) }; var categories by remember { mutableStateOf(false) }; var tags by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf<Category?>(null) }
    var newTag by remember { mutableStateOf(false) }; var tagName by remember { mutableStateOf("") }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.image(uri) }
    fun back() { if (vm.busy) return; if (draft.dirty) exiting = true else onBack() }
    BackHandler { back() }
    Column {
        PageTitle(if (draft.original.recipe.name.isEmpty()) "新建菜谱" else "编辑菜谱", { back() })
        LazyColumn(modifier = Modifier.weight(1f), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Field(draft.name, "菜名（必填）") { draft.name = it } }
            if (draft.coverPath != null) item { Cover(draft.coverPath, vm.container.images, Modifier.fillMaxWidth().height(160.dp)) }
            item { Row { OutlinedButton(onClick = { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("选择封面") }; if (draft.coverPath != null) TextButton(onClick = { draft.coverPath = null }) { Text("移除封面") } } }
            item { Field(draft.description, "简介", singleLine = false) { draft.description = it } }
            item { OutlinedButton(onClick = { categories = true }, modifier = Modifier.fillMaxWidth()) { Text("分类：${categoryPath(draft.categoryId, snapshot.categories)}") } }
            item { Row { OutlinedButton(onClick = { tags = true }, modifier = Modifier.weight(1f)) { Text("标签：${draft.tags.joinToString("、") { it.name }.ifEmpty { "未选择" }}") }; TextButton(onClick = { newTag = true }) { Text("新增标签") } } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Field(draft.prepare, "准备（分钟）", Modifier.weight(1f), numeric = true) { draft.prepare = it }; Field(draft.cook, "烹饪（分钟）", Modifier.weight(1f), numeric = true) { draft.cook = it } } }
            item { Section("食材"); Text("数量可不填；“适量”等填入用量说明。购物清单只取名称。", style = MaterialTheme.typography.bodySmall) }
            itemsIndexed(draft.ingredients, key = { _, i -> i.id }) { index, ingredient ->
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row { Text("食材 ${index + 1}", Modifier.weight(1f)); ReorderButtons(index, draft.ingredients.size, { swap(draft.ingredients, index, index - 1) }, { swap(draft.ingredients, index, index + 1) }, { draft.ingredients.removeAt(index) }) }
                    IngredientNameField(ingredient.name, autofocus = ingredient.id == focusIngredient) { draft.ingredients[index] = ingredient.copy(name = it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(ingredient.amount, "数量", Modifier.weight(1f), numeric = true) { draft.ingredients[index] = ingredient.copy(amount = it) }
                        Field(ingredient.unit, "单位（可自定义）", Modifier.weight(1f)) { draft.ingredients[index] = ingredient.copy(unit = it) }
                    }
                    Row { listOf("克", "毫升", "个", "勺").forEach { unit -> TextButton(onClick = { draft.ingredients[index] = ingredient.copy(unit = unit) }) { Text(unit) } } }
                    Field(ingredient.amountNote, "用量说明，例如适量、少许") { draft.ingredients[index] = ingredient.copy(amountNote = it) }
                } }
            }
            item { OutlinedButton(onClick = {
                val ingredient = RecipeIngredient(recipeId = draft.original.recipe.id)
                draft.ingredients.add(ingredient)
                scope.launch {
                    listState.scrollToItem(7 + (if (draft.coverPath != null) 1 else 0) + draft.ingredients.lastIndex)
                    focusIngredient = ingredient.id
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("＋ 添加食材") } }
            item { Section("调料备注"); Text("记录这道菜用到的特殊调料及参考用量。", style = MaterialTheme.typography.bodySmall) }
            itemsIndexed(draft.seasonings, key = { _, i -> i.id }) { index, ingredient ->
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row { Text("调料 ${index + 1}", Modifier.weight(1f)); ReorderButtons(index, draft.seasonings.size, { swap(draft.seasonings, index, index - 1) }, { swap(draft.seasonings, index, index + 1) }, { draft.seasonings.removeAt(index) }) }
                    IngredientNameField(ingredient.name, autofocus = ingredient.id == focusIngredient, label = "调料名称") { draft.seasonings[index] = ingredient.copy(name = it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(ingredient.amount, "数量", Modifier.weight(1f), numeric = true) { draft.seasonings[index] = ingredient.copy(amount = it) }
                        Field(ingredient.unit, "单位（可自定义）", Modifier.weight(1f)) { draft.seasonings[index] = ingredient.copy(unit = it) }
                    }
                    Row { listOf("克", "毫升", "个", "勺").forEach { unit -> TextButton(onClick = { draft.seasonings[index] = ingredient.copy(unit = unit) }) { Text(unit) } } }
                    Field(ingredient.amountNote, "用量说明，例如适量、少许") { draft.seasonings[index] = ingredient.copy(amountNote = it) }
                } }
            }
            item { OutlinedButton(onClick = {
                val ingredient = RecipeIngredient(recipeId = draft.original.recipe.id, isSeasoning = true)
                draft.seasonings.add(ingredient)
                scope.launch {
                    listState.scrollToItem(9 + (if (draft.coverPath != null) 1 else 0) + draft.ingredients.size + draft.seasonings.lastIndex)
                    focusIngredient = ingredient.id
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("＋ 添加调料") } }
            item { Section("备菜"); Field(draft.preparation, "备菜说明", singleLine = false) { draft.preparation = it } }
            item { Section("做法步骤") }
            itemsIndexed(draft.steps, key = { _, s -> s.id }) { index, step ->
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row { Text("步骤 ${index + 1}", Modifier.weight(1f)); ReorderButtons(index, draft.steps.size, { swap(draft.steps, index, index - 1) }, { swap(draft.steps, index, index + 1) }, { draft.steps.removeAt(index) }) }
                    Field(step.content, "步骤说明", singleLine = false) { draft.steps[index] = step.copy(content = it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("未指定", "大火", "中火", "小火").forEach { heat -> FilterChip(step.heat == heat, { draft.steps[index] = step.copy(heat = heat) }, { Text(heat) }) } }
                    Row {
                        OutlinedButton(onClick = { timingStep = step.id }) { Text(step.timerSeconds?.let { "参考计时 " + formatTime(it * 1000) } ?: "设置步骤计时") }
                        if (step.timerSeconds != null) TextButton(onClick = { draft.steps[index] = step.copy(timerSeconds = null) }) { Text("清除时长") }
                    }
                } }
            }
            item { OutlinedButton(onClick = { draft.steps.add(RecipeStep(recipeId = draft.original.recipe.id)) }, modifier = Modifier.fillMaxWidth()) { Text("＋ 添加步骤") } }
        }
        Button(onClick = { vm.saveEditor(onBack) }, enabled = !vm.busy, modifier = Modifier.align(androidx.compose.ui.Alignment.End).padding(end = 16.dp, bottom = 8.dp)) { Text("保存菜谱") }
    }
    draft.steps.find { it.id == timingStep }?.let { step ->
        TimeSheet(step.timerSeconds ?: 60L, { timingStep = null }) { seconds ->
            val index = draft.steps.indexOfFirst { it.id == step.id }
            if (index >= 0) draft.steps[index] = draft.steps[index].copy(timerSeconds = seconds)
            timingStep = null
        }
    }
    if (exiting) AlertDialog(onDismissRequest = { exiting = false }, title = { Text("保存修改？") }, text = { Text("还有未保存的内容。") }, confirmButton = { TextButton(onClick = { exiting = false; vm.saveEditor(onBack) }) { Text("保存并返回") } }, dismissButton = { Row { TextButton(onClick = { exiting = false }) { Text("继续编辑") }; TextButton(onClick = onBack) { Text("放弃修改") } } })
    if (categories) CategorySheet(snapshot.categories, draft.categoryId, { categories = false }, onNew = { categories = false; newCategory = Category(name = "") }) { draft.categoryId = it; categories = false }
    newCategory?.let { category -> CategoryEditorDialog(vm, snapshot.categories, category, { newCategory = null }) { draft.categoryId = it.id; newCategory = null } }
    if (tags) ChoiceSheet("选择标签", snapshot.tags.map { it.id to it.name }, draft.tags.map { it.id }.toSet(), { tags = false }) { ids -> draft.tags.clear(); draft.tags.addAll(snapshot.tags.filter { it.id in ids }); tags = false }
    if (newTag) AlertDialog(onDismissRequest = { newTag = false }, title = { Text("新增标签") }, text = { Field(tagName, "标签名称") { tagName = it } }, confirmButton = { TextButton(onClick = { val tag = Tag(name = tagName.trim()); vm.run(success = { draft.tags.add(tag); newTag = false; tagName = "" }) { vm.container.repository.saveTag(tag) } }) { Text("创建并选中") } }, dismissButton = { TextButton(onClick = { newTag = false }) { Text("取消") } })
}
private fun <T> swap(list: MutableList<T>, first: Int, second: Int) { if (first in list.indices && second in list.indices) { val temp = list[first]; list[first] = list[second]; list[second] = temp } }
@Composable private fun ReorderButtons(index: Int, size: Int, up: () -> Unit, down: () -> Unit, delete: () -> Unit) {
    TextButton(onClick = up, enabled = index > 0) { Text("上移") }; TextButton(onClick = down, enabled = index < size - 1) { Text("下移") }; TextButton(onClick = delete) { Text("删除") }
}
