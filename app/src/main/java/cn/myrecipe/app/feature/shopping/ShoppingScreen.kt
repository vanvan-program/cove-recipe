package cn.myrecipe.app.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextDecoration
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
import cn.myrecipe.app.core.database.*

@Composable fun ShoppingScreen(vm: AppViewModel, snapshot: AppSnapshot) {
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }; var name by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    var addingRecipes by remember { mutableStateOf(false) }; var pickingIngredients by remember { mutableStateOf<List<RecipeIngredient>?>(null) }
    var clearing by remember { mutableStateOf(false) }; var showBought by rememberSaveable { mutableStateOf(true) }
    fun edit(item: ShoppingItem) { name = item.name; note = item.note; editing = item }
    Column(Modifier.fillMaxSize()) {
        PageTitle("购物清单") {
            TextButton(onClick = { edit(ShoppingItem(name = "", sortOrder = (snapshot.shopping.maxOfOrNull { it.sortOrder } ?: -1) + 1)) },
                modifier = Modifier.testTag("addShopping")) { Text("＋ 添加食材") }
        }
        Text("待买 ${snapshot.shopping.count { !it.bought }} 项 · 已买 ${snapshot.shopping.count { it.bought }} 项", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { addingRecipes = true }, modifier = Modifier.weight(1f)) { Text("从菜谱添加") }
            TextButton(onClick = { clearing = true }, enabled = snapshot.shopping.isNotEmpty()) { Text("清空") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (snapshot.shopping.isEmpty()) item { EmptyState("今天要买什么？", "从菜谱添加食材，或手动记下要买的东西。") }
            item { Section("待买 ${snapshot.shopping.count { !it.bought }} 项") }
            items(snapshot.shopping.filter { !it.bought }, key = { it.id }) { item -> ShoppingRow(item, { vm.run { vm.container.repository.saveItem(item.copy(bought = true)) } }, { edit(item) }, { vm.deleteShopping(item) }) }
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showBought = !showBought }, modifier = Modifier.weight(1f)) { Text("已买 ${snapshot.shopping.count { it.bought }} 项 ${if (showBought) "▴" else "▾"}") }
                TextButton(onClick = { vm.run { vm.container.repository.clearShopping(true) } }, enabled = snapshot.shopping.any { it.bought }) { Text("清理已买") }
            } }
            if (showBought) items(snapshot.shopping.filter { it.bought }, key = { it.id }) { item -> ShoppingRow(item, { vm.run { vm.container.repository.saveItem(item.copy(bought = false)) } }, { edit(item) }, { vm.deleteShopping(item) }) }
        }
    }
    editing?.let { item -> AlertDialog(onDismissRequest = { editing = null }, title = { Text(if (item.name.isBlank()) "添加食材" else "编辑食材") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { IngredientNameField(name, autofocus = item.name.isBlank()) { name = it }; Field(note, "采购备注（可选）", singleLine = false) { note = it } } }, confirmButton = { TextButton(onClick = { vm.run(success = { editing = null }) { vm.container.repository.saveItem(item.copy(name = name, note = note)) } }) { Text("保存") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } }) }
    if (clearing) Confirm("清空购物清单", "删除全部待买和已买食材及采购备注？", { clearing = false }) { clearing = false; vm.run { vm.container.repository.clearShopping(false) } }
    if (addingRecipes) ChoiceSheet("选择菜谱", snapshot.recipes.sortedByDescending { it.recipe.favorite }.map { it.recipe.id to it.recipe.name }, emptySet(), { addingRecipes = false }) { ids -> pickingIngredients = snapshot.recipes.filter { it.recipe.id in ids }.flatMap { it.ingredients }; addingRecipes = false }
    pickingIngredients?.let { ingredients -> RecipeShoppingSheet(vm, ingredients, { pickingIngredients = null }) { pickingIngredients = null } }
}
@Composable private fun ShoppingRow(item: ShoppingItem, toggle: () -> Unit, edit: () -> Unit, delete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(item.bought, { toggle() })
            Column(Modifier.weight(1f).clickable(onClick = edit).padding(vertical = 10.dp)) { Text(item.name, style = MaterialTheme.typography.titleMedium, textDecoration = if (item.bought) TextDecoration.LineThrough else null, color = if (item.bought) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface); if (item.sourceRecipeNames.isNotEmpty()) Text("来自：${item.sourceRecipeNames.joinToString("、")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (item.bought) Text("已买", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
            IconButton(onClick = delete) { Icon(Icons.Outlined.Delete, "删除${item.name}", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
