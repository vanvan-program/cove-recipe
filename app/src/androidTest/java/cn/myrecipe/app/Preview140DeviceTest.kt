package cn.myrecipe.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import cn.myrecipe.app.core.database.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

/** Examples are injected by the test APK only. */
class Preview140DeviceTest {
    @Test fun seedAndVerifyVersion140Preview() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<RecipeApplication>(); val c = app.container
        c.timer.cancel()
        c.gate.withLock { val dao = c.db.dao(); dao.clearRecipes(); dao.clearCategories(); dao.clearTags(); dao.clearShopping() }
        c.preferences.saveHome("全部", null)
        val category = Category(name = "家常菜"); c.repository.saveCategory(category)
        val tag = Tag(name = "简单"); c.repository.saveTag(tag)
        val names = listOf("蚝油青菜", "番茄大虾", "清炒青菜")
        val count = InstrumentationRegistry.getArguments().getString("previewCount")?.toIntOrNull() ?: 3
        val recipes = (0 until count).map { index ->
            val r = Recipe(name = names.getOrElse(index) { "家常菜 ${index + 1}" }, categoryId = category.id, preparation = "青菜洗净沥干，蒜切末备用。", prepareMinutes = 5, cookMinutes = 8)
            RecipeFull(r, listOf(RecipeIngredient(recipeId = r.id, name = if (index == 1) "虾" else "青菜", amount = "500", unit = "克"), RecipeIngredient(recipeId = r.id, name = "蒜", amount = "2", unit = "瓣"), RecipeIngredient(recipeId = r.id, name = "蚝油", amount = "1", unit = "勺", amountNote = "提鲜", isSeasoning = true), RecipeIngredient(recipeId = r.id, name = "生抽", amount = "2", unit = "勺", isSeasoning = true)), listOf(RecipeStep(recipeId = r.id, content = "蒜末炒香，放入青菜翻炒，加入调料炒匀。", heat = "大火", timerSeconds = 120)), listOf(tag))
        }
        recipes.forEach { c.repository.save(it) }; c.repository.reorderRecipes(recipes.map { it.recipe.id })
        c.repository.addNames(listOf("青菜", "虾"), mapOf("青菜" to listOf(names[0], names[2]), "虾" to listOf(names[1])))
        c.repository.saveItem(ShoppingItem(name = "鸡蛋", note = "买一盒"))
        val item = c.db.dao().shopping().single { it.name == "青菜" }; c.repository.saveItem(item.copy(note = "挑新鲜的"))
        assertEquals(count, c.db.dao().recipes().size)
        assertEquals(2, c.db.dao().shopping().single { it.name == "青菜" }.sourceRecipeNames.size)
    }
}
