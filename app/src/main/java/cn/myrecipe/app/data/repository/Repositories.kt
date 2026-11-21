package cn.myrecipe.app.data.repository

import cn.myrecipe.app.core.database.*
import androidx.room.withTransaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal

interface RecipeRepository {
    suspend fun save(full: RecipeFull)
    suspend fun delete(id: String)
    suspend fun markUsed(id: String)
}
interface ShoppingRepository {
    suspend fun addNames(names: List<String>, sources: Map<String, List<String>> = emptyMap()): Int
    suspend fun saveItem(item: ShoppingItem)
    suspend fun deleteItem(id: String)
}
interface TaxonomyRepository {
    suspend fun saveCategory(category: Category)
    suspend fun removeCategory(id: String)
    suspend fun saveTag(tag: Tag)
    suspend fun removeTag(id: String)
}

/** Serializes mutations with backup and timer operations, so snapshots never mix generations. */
class LocalRepository(val db: RecipeDatabase, val gate: Mutex) : RecipeRepository, ShoppingRepository, TaxonomyRepository {
    private val dao = db.dao()
    val snapshot: Flow<AppSnapshot> = combine(dao.observeRecipes(), dao.observeCategories(), dao.observeTags(), dao.observeShopping(), dao.observeTimer()) { r, c, t, s, timer -> AppSnapshot(r.filter { it.recipe.deletedAt == null }, c, t, s, timer, r.filter { it.recipe.deletedAt != null }.sortedByDescending { it.recipe.deletedAt }) }

    override suspend fun save(full: RecipeFull) = gate.withLock {
        require(full.recipe.name.isNotBlank()) { "请填写菜名" }
        require(full.recipe.prepareMinutes == null || full.recipe.prepareMinutes >= 0) { "准备时间不能为负数" }
        require(full.recipe.cookMinutes == null || full.recipe.cookMinutes >= 0) { "烹饪时间不能为负数" }
        full.ingredients.forEach { i -> require(i.name.isNotBlank()) { "食材名称不能为空" }; require(i.amount.isEmpty() || (i.amount.toBigDecimalOrNull()?.let { it >= BigDecimal.ZERO } == true)) { "食材数量须为非负十进制数；适量请填入用量说明" } }
        full.steps.forEach { require(it.content.isNotBlank()) { "步骤正文不能为空" }; require(it.heat in listOf("未指定", "大火", "中火", "小火")) { "火候无效" }; require(it.timerSeconds == null || it.timerSeconds in 1..604800) { "步骤计时须在 1 秒至 7 天之间" } }
        db.withTransaction {
            val existing = dao.recipes()
            val current = existing.find { it.recipe.id == full.recipe.id }?.recipe
            require(current?.deletedAt == null) { "菜谱已移入回收站" }
            val order = current?.sortOrder
                ?: ((existing.minOfOrNull { it.recipe.sortOrder } ?: 1) - 1)
            dao.putRecipe(full.recipe.copy(name = full.recipe.name.trim(), sortOrder = order, updatedAt = System.currentTimeMillis()))
            dao.deleteIngredients(full.recipe.id); dao.deleteSteps(full.recipe.id); dao.deleteLinks(full.recipe.id)
            dao.putIngredients(full.ingredients.mapIndexed { index, i -> i.copy(recipeId = full.recipe.id, name = i.name.trim(), sortOrder = index) })
            dao.putSteps(full.steps.mapIndexed { index, s -> s.copy(recipeId = full.recipe.id, sortOrder = index) })
            dao.putLinks(full.tags.map { RecipeTag(full.recipe.id, it.id) })
        }
    }
    suspend fun reorderRecipes(visibleIds: List<String>) = gate.withLock {
        db.withTransaction {
            val all = filterRecipes(AppSnapshot(recipes = dao.recipes().filter { it.recipe.deletedAt == null }), "", "全部", null)
            val valid = all.map { it.recipe.id }.toSet()
            val requested = visibleIds.filter { it in valid }.distinct()
            // Reorder only the visible slots; hidden recipes and the pinned group keep their places.
            val selected = requested.toSet()
            val queues = all.groupBy { it.recipe.pinned }.mapValues { (_, group) ->
                val groupIds = group.map { it.recipe.id }.toSet()
                ArrayDeque(requested.filter { it in groupIds })
            }
            all.forEachIndexed { index, full ->
                val id = if (full.recipe.id in selected) queues.getValue(full.recipe.pinned).removeFirst() else full.recipe.id
                dao.setRecipeOrder(id, index)
            }
        }
    }
    override suspend fun delete(id: String) = gate.withLock { dao.setDeletedAt(id, System.currentTimeMillis()) }
    suspend fun restoreRecipe(id: String) = gate.withLock { dao.setDeletedAt(id, null) }
    suspend fun purgeRecipe(id: String) = gate.withLock { dao.purgeTrashed(id) }
    suspend fun toggle(id: String, favorite: Boolean? = null, pinned: Boolean? = null) = gate.withLock {
        dao.recipes().find { it.recipe.id == id }?.recipe?.let { dao.putRecipe(it.copy(favorite = favorite ?: it.favorite, pinned = pinned ?: it.pinned)) }
    }
    override suspend fun markUsed(id: String) = gate.withLock {
        dao.recipes().find { it.recipe.id == id }?.recipe?.let { dao.putRecipe(it.copy(lastUsedAt = System.currentTimeMillis())) }
        Unit
    }
    override suspend fun addNames(names: List<String>, sources: Map<String, List<String>>): Int = gate.withLock {
        var restored = 0
        db.withTransaction {
            val existing = dao.shopping().associateBy { it.normalizedName }.toMutableMap()
            var order = (existing.values.maxOfOrNull { it.sortOrder } ?: -1) + 1
            names.filter { it.isNotBlank() }.forEach { raw ->
                val key = normalizeName(raw); val old = existing[key]
                val item = if (old == null) ShoppingItem(name = raw.trim(), sortOrder = order++) else old.copy(bought = false, updatedAt = System.currentTimeMillis())
                if (old?.bought == true) restored++
                val withSources = item.copy(sourceRecipeNames = (item.sourceRecipeNames + sources[key].orEmpty()).filter { it.isNotBlank() }.distinct())
                dao.putShopping(withSources); existing[key] = withSources
            }
        }
        restored
    }
    override suspend fun saveItem(item: ShoppingItem) = gate.withLock {
        require(item.name.isNotBlank()) { "请填写食材名称" }
        val key = normalizeName(item.name)
        require(dao.shopping().none { it.id != item.id && it.normalizedName == key }) { "清单中已有同名食材，请编辑已有项目" }
        dao.putShopping(item.copy(name = item.name.trim(), normalizedName = key, updatedAt = System.currentTimeMillis()))
    }
    override suspend fun deleteItem(id: String) = gate.withLock { dao.deleteShopping(id) }
    suspend fun clearShopping(onlyBought: Boolean) = gate.withLock { if (onlyBought) dao.clearBought() else dao.clearShopping() }
    suspend fun restoreItem(item: ShoppingItem) = gate.withLock {
        if (dao.shopping().none { it.normalizedName == item.normalizedName }) dao.putShopping(item)
    }
    override suspend fun saveCategory(category: Category) = gate.withLock {
        val all = dao.categories().filterNot { it.id == category.id } + category.copy(name = category.name.trim())
        validateCategories(all)
        require(all.count { it.parentId == category.parentId && normalizeName(it.name) == normalizeName(category.name) } == 1) { "该层级已有同名分类" }
        dao.putCategories(listOf(category.copy(name = category.name.trim())))
    }
    override suspend fun removeCategory(id: String) = gate.withLock {
        db.withTransaction {
            val all = dao.categories(); val removed = all.find { it.id == id } ?: return@withTransaction
            dao.putCategories(all.filter { it.parentId == id }.map { it.copy(parentId = removed.parentId) })
            dao.deleteCategory(id)
        }
    }
    override suspend fun saveTag(tag: Tag) = gate.withLock {
        require(tag.name.isNotBlank()) { "标签名称不能为空" }
        require(dao.tags().none { it.id != tag.id && normalizeName(it.name) == normalizeName(tag.name) }) { "已有同名标签" }
        dao.putTags(listOf(tag.copy(name = tag.name.trim())))
    }
    override suspend fun removeTag(id: String) = gate.withLock { dao.deleteTag(id) }
}
