package cn.myrecipe.app.feature

import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.myrecipe.app.AppContainer
import cn.myrecipe.app.core.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class Notice(val text: String, val undo: (suspend () -> Unit)? = null)
class EditorDraft(val original: RecipeFull) {
    var name by mutableStateOf(original.recipe.name)
    var description by mutableStateOf(original.recipe.description)
    var preparation by mutableStateOf(original.recipe.preparation)
    var coverPath by mutableStateOf(original.recipe.coverPath)
    var categoryId by mutableStateOf(original.recipe.categoryId)
    var prepare by mutableStateOf(original.recipe.prepareMinutes?.toString().orEmpty())
    var cook by mutableStateOf(original.recipe.cookMinutes?.toString().orEmpty())
    val ingredients = mutableStateListOf<RecipeIngredient>().apply { addAll(original.foodIngredients) }
    val seasonings = mutableStateListOf<RecipeIngredient>().apply { addAll(original.seasonings) }
    val steps = mutableStateListOf<RecipeStep>().apply { addAll(original.steps.sortedBy { it.sortOrder }) }
    val tags = mutableStateListOf<Tag>().apply { addAll(original.tags) }
    val dirty: Boolean get() = preparation != original.recipe.preparation || name != original.recipe.name || description != original.recipe.description || coverPath != original.recipe.coverPath || categoryId != original.recipe.categoryId || prepare != original.recipe.prepareMinutes?.toString().orEmpty() || cook != original.recipe.cookMinutes?.toString().orEmpty() || ingredients.toList() != original.foodIngredients || seasonings.toList() != original.seasonings || steps.toList() != original.steps.sortedBy { it.sortOrder } || tags.toList() != original.tags
    fun build(): RecipeFull {
        fun minutes(raw: String): Int? { if (raw.isBlank()) return null; return raw.toIntOrNull()?.takeIf { it >= 0 } ?: error("时间须为非负整数分钟") }
        return RecipeFull(original.recipe.copy(name = name, description = description, preparation = preparation, coverPath = coverPath, categoryId = categoryId, prepareMinutes = minutes(prepare), cookMinutes = minutes(cook)), ingredients.toList() + seasonings.toList(), steps.toList(), tags.toList())
    }
}
class AppViewModel(val container: AppContainer) : ViewModel() {
    val snapshot = container.repository.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, AppSnapshot())
    val preferences = container.preferences.home.stateIn(viewModelScope, SharingStarted.Eagerly, "全部" to null)
    private val notices = Channel<Notice>(Channel.UNLIMITED)
    val events = notices.receiveAsFlow()
    var busy by mutableStateOf(false); private set
    var editor by mutableStateOf<EditorDraft?>(null); private set
    fun openEditor(full: RecipeFull?) { editor = EditorDraft(full ?: RecipeFull(Recipe(name = ""), emptyList(), emptyList(), emptyList())) }
    fun clearEditor() { editor = null }
    fun notify(text: String) { viewModelScope.launch { notices.send(Notice(text)) } }
    fun run(success: (() -> Unit)? = null, onError: ((String) -> Unit)? = null, action: suspend () -> Unit): Job = viewModelScope.launch {
        try { action(); success?.invoke() } catch (e: CancellationException) { throw e } catch (e: Exception) {
            val message = when (e) {
                is java.io.IOException -> "文件读取或写入失败，请检查文件和存储空间"
                is android.database.SQLException -> "保存失败，请检查分类或标签是否已被删除"
                is SecurityException -> "权限未允许，请到设置中检查相关权限"
                else -> e.message ?: "操作失败，请重试"
            }
            if (onError != null) onError(message) else notices.send(Notice(message))
        }
    }
    fun saveEditor(success: () -> Unit) {
        if (busy) return
        busy = true
        run(action = { try { container.repository.save(editor?.build() ?: error("编辑内容不存在")); clearEditor(); success() } finally { busy = false } })
    }
    fun addShopping(names: List<String>, success: (() -> Unit)? = null) = run(success) {
        val restored = container.repository.addNames(names)
        notices.send(Notice(if (restored > 0) "已加入清单，其中 $restored 项恢复为待买" else "已加入购物清单，同名食材已去重"))
    }
    fun addIngredients(ingredients: List<RecipeIngredient>, success: (() -> Unit)? = null) = run(success) {
        val recipes = snapshot.value.recipes.associate { it.recipe.id to it.recipe.name }
        val sources = ingredients.groupBy { normalizeName(it.name) }.mapValues { (_, items) -> items.mapNotNull { recipes[it.recipeId] }.distinct() }
        val restored = container.repository.addNames(ingredients.map { it.name }, sources)
        notices.send(Notice(if (restored > 0) "已加入清单，其中 $restored 项恢复为待买" else "已加入购物清单，同名食材已去重"))
    }
    fun deleteShopping(item: ShoppingItem) = run {
        container.repository.deleteItem(item.id)
        notices.send(Notice("已删除 ${item.name}") { container.repository.restoreItem(item) })
    }
    fun image(uri: Uri) = run {
        busy = true
        try { editor?.coverPath = container.images.import(uri) } finally { busy = false }
    }
    fun export(uri: Uri) = run {
        busy = true
        try { container.backup.export(uri); notices.send(Notice("备份导出成功")) } finally { busy = false }
    }
    fun restore(uri: Uri, success: () -> Unit) = run {
        busy = true
        try { val count = container.backup.restore(uri); clearEditor(); success(); notices.send(Notice("已恢复 $count 道菜谱及购物清单")) } finally { busy = false }
    }
}
