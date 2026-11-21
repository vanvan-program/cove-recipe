package cn.myrecipe.app.feature

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import cn.myrecipe.app.core.database.RecipeIngredient
import cn.myrecipe.app.core.database.Category
import cn.myrecipe.app.core.database.categoryPath
import cn.myrecipe.app.core.files.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun PageTitle(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) TextButton(onClick = back) { Text("返回") }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(4.dp))
        actions()
    }
    HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.46f))
    Spacer(Modifier.height(6.dp))
}
@Composable fun Section(title: String) { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)) }
@Composable fun Field(value: String, label: String, modifier: Modifier = Modifier, numeric: Boolean = false, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable fun EmptyState(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("🍲", style = MaterialTheme.typography.displayMedium)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun IngredientNameField(value: String, autofocus: Boolean, modifier: Modifier = Modifier, label: String = "食材名称", onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Field(value, label, modifier.focusRequester(focus), onChange = onChange)
    LaunchedEffect(autofocus) { if (autofocus) { focus.requestFocus(); keyboard?.show() } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun IngredientFlow(ingredients: List<RecipeIngredient>, large: Boolean = false) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ingredients.sortedBy { it.sortOrder }.forEach { ingredient ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(ingredient.name, style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
                    val amount = listOf(ingredient.amount + ingredient.unit, ingredient.amountNote).filter { it.isNotBlank() }.joinToString(" ")
                    if (amount.isNotBlank()) Text(amount, style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
@Composable fun Cover(path: String?, images: ImageStore, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            path?.let { runCatching {
                val file = images.file(it)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, options); options.inJustDecodeBounds = false; options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 800) options.inSampleSize *= 2
                BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
            }.getOrNull() }
        }
    }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!, "菜谱封面", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("🍲", style = MaterialTheme.typography.headlineLarge)
    }
}
@Composable fun Confirm(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) }, confirmButton = { TextButton(onClick = onConfirm) { Text("确认") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CategorySheet(categories: List<Category>, selected: String?, onDismiss: () -> Unit, onNew: (() -> Unit)? = null, onSelect: (String?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 500.dp).padding(bottom = 24.dp)) {
            item { Section("选择分类"); ListItem(headlineContent = { Text("未分类 / 全部") }, leadingContent = { RadioButton(selected == null, null) }, modifier = Modifier.clickable { onSelect(null) }) }
            if (onNew != null) item { TextButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("＋ 新建分类") } }
            items(categories.sortedBy { categoryPath(it.id, categories) }, key = { it.id }) { c -> ListItem(headlineContent = { Text(categoryPath(c.id, categories)) }, leadingContent = { RadioButton(selected == c.id, null) }, modifier = Modifier.clickable { onSelect(c.id) }) }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ChoiceSheet(title: String, choices: List<Pair<String, String>>, initial: Set<String>, onDismiss: () -> Unit, onDone: (Set<String>) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Section(title)
            if (choices.isEmpty()) Text("暂无可选项目，请先添加。")
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 430.dp)) {
                items(choices, key = { it.first }) { (id, title) ->
                    Row(Modifier.fillMaxWidth().clickable { selected = if (id in selected) selected - id else selected + id }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(id in selected, { checked -> selected = if (checked) selected + id else selected - id }); Text(title, Modifier.weight(1f))
                    }
                }
            }
            Button(onClick = { onDone(selected) }, modifier = Modifier.fillMaxWidth()) { Text("确定") }
        }
    }
}

@Composable fun RecipeShoppingSheet(vm: AppViewModel, ingredients: List<RecipeIngredient>, dismiss: () -> Unit, added: () -> Unit) {
    val choices = ingredients.distinctBy { cn.myrecipe.app.core.database.normalizeName(it.name) }
    ChoiceSheet("选择要买的食材", choices.map { cn.myrecipe.app.core.database.normalizeName(it.name) to it.name }, ingredients.filterNot { it.isSeasoning }.map { cn.myrecipe.app.core.database.normalizeName(it.name) }.toSet(), dismiss) { names ->
        vm.addIngredients(ingredients.filter { cn.myrecipe.app.core.database.normalizeName(it.name) in names }, added)
    }
}
