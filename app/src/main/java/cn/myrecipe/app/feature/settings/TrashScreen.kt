package cn.myrecipe.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.myrecipe.app.core.database.RecipeFull

@Composable internal fun TrashScreen(vm: AppViewModel, trash: List<RecipeFull>, onBack: () -> Unit) {
    var purge by remember { mutableStateOf<RecipeFull?>(null) }
    Column {
        PageTitle("回收站", onBack)
        if (trash.isEmpty()) EmptyState("回收站是空的", "删除的菜谱可以在这里恢复。")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(trash, key = { it.recipe.id }) { full ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(full.recipe.name, style = MaterialTheme.typography.titleMedium)
                        Row {
                            TextButton(onClick = { vm.run { vm.container.repository.restoreRecipe(full.recipe.id) } }) { Text("恢复") }
                            TextButton(onClick = { purge = full }) { Text("永久删除") }
                        }
                    }
                }
            }
        }
    }
    purge?.let { full ->
        Confirm("永久删除菜谱", "永久删除“${full.recipe.name}”及其食材和步骤？此操作无法撤销。", { purge = null }) {
            purge = null
            vm.run { vm.container.repository.purgeRecipe(full.recipe.id) }
        }
    }
}
