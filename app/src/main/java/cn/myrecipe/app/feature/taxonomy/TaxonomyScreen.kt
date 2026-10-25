package cn.myrecipe.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.myrecipe.app.core.database.*

@Composable fun TaxonomyScreen(vm: AppViewModel, snapshot: AppSnapshot, onBack: () -> Unit) {
    var category by remember { mutableStateOf<Category?>(null) }; var tag by remember { mutableStateOf<Tag?>(null) }
    var name by remember { mutableStateOf("") }
    var removingCategory by remember { mutableStateOf<Category?>(null) }; var removingTag by remember { mutableStateOf<Tag?>(null) }
    Column {
        PageTitle("分类与标签", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Section("分类（最多三级）"); OutlinedButton(onClick = { category = Category(name = "") }) { Text("＋ 新建分类") } }
            items(snapshot.categories.sortedBy { categoryPath(it.id, snapshot.categories) }, key = { it.id }) { c ->
                ListItem(headlineContent = { Text(categoryPath(c.id, snapshot.categories)) }, supportingContent = { Text("${snapshot.recipes.count { it.recipe.categoryId == c.id }} 道菜谱") }, trailingContent = { Row { TextButton(onClick = { category = c }) { Text("编辑") }; TextButton(onClick = { removingCategory = c }) { Text("删除") } } })
            }
            if (snapshot.categories.isEmpty()) item { Text("尚无分类。未分类的菜谱也可以正常保存。") }
            item { Section("标签"); OutlinedButton(onClick = { tag = Tag(name = ""); name = "" }) { Text("＋ 新建标签") } }
            items(snapshot.tags, key = { it.id }) { t -> ListItem(headlineContent = { Text("#${t.name}") }, trailingContent = { Row { TextButton(onClick = { tag = t; name = t.name }) { Text("编辑") }; TextButton(onClick = { removingTag = t }) { Text("删除") } } }) }
        }
    }
    category?.let { c -> CategoryEditorDialog(vm, snapshot.categories, c, { category = null }) { category = null } }
    tag?.let { t -> AlertDialog(onDismissRequest = { tag = null }, title = { Text("标签") }, text = { Field(name, "标签名称") { name = it } }, confirmButton = { TextButton(onClick = { vm.run(success = { tag = null }) { vm.container.repository.saveTag(t.copy(name = name)) } }) { Text("保存") } }, dismissButton = { TextButton(onClick = { tag = null }) { Text("取消") } }) }
    removingCategory?.let { c -> Confirm("删除分类", "“${c.name}”中的菜谱将改为未分类，直属子分类提升一级；菜谱不会删除。", { removingCategory = null }) { removingCategory = null; vm.run { vm.container.repository.removeCategory(c.id) } } }
    removingTag?.let { t -> Confirm("删除标签", "删除“${t.name}”及菜谱关联，菜谱不会删除。", { removingTag = null }) { removingTag = null; vm.run { vm.container.repository.removeTag(t.id) } } }
}

@Composable fun CategoryEditorDialog(vm: AppViewModel, categories: List<Category>, category: Category, onDismiss: () -> Unit, onSaved: (Category) -> Unit) {
    var name by remember(category.id) { mutableStateOf(category.name) }
    var parentId by remember(category.id) { mutableStateOf(category.parentId) }
    var choosingParent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    if (!choosingParent) AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() }, title = { Text(if (category.name.isEmpty()) "新建分类" else "编辑分类") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(name, "分类名称") { name = it; error = null }
            OutlinedButton(onClick = { choosingParent = true }, enabled = !saving) { Text("父分类：${if (parentId == null) "无（顶级分类）" else categoryPath(parentId, categories)}") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            val value = category.copy(name = name.trim(), parentId = parentId)
            saving = true
            vm.run(success = { saving = false; onSaved(value) }, onError = { error = it; saving = false }) { vm.container.repository.saveCategory(value) }
        }) { Text("保存") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("取消") } }
    )
    if (choosingParent) CategorySheet(categories.filterNot { it.id in categoryDescendants(category.id, categories) }, parentId, { choosingParent = false }) { parentId = it; error = null; choosingParent = false }
}
