package cn.myrecipe.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import cn.myrecipe.app.core.database.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Test
import org.junit.Assert.assertEquals

/** Only the test APK seeds these examples; fresh user installations remain empty. */
class PreviewDeviceTest {
    @Test fun seedPreviewAndVerifyPersistence() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<RecipeApplication>(); val container = app.container
        container.timer.cancel()
        container.gate.withLock { val dao = container.db.dao(); dao.clearRecipes(); dao.clearCategories(); dao.clearTags(); dao.clearShopping() }
        container.preferences.saveHome("全部", null)
        val meat = Category(name = "肉菜"); val pork = Category(name = "猪肉", parentId = meat.id); val vegetable = Category(name = "家常小炒")
        listOf(meat, pork, vegetable).forEach { container.repository.saveCategory(it) }
        val home = Tag(name = "家常"); val rice = Tag(name = "下饭"); val fast = Tag(name = "快手")
        listOf(home, rice, fast).forEach { container.repository.saveTag(it) }
        val r = Recipe(name = "红烧肉", description = "慢慢炖到软糯，收汁时多留意锅底。家里的经典下饭菜。", categoryId = pork.id, preparation = "五花肉切块，生抽与冰糖提前备好。", prepareMinutes = 15, cookMinutes = 60, favorite = true, pinned = true)
        container.repository.save(RecipeFull(r, listOf(RecipeIngredient(recipeId = r.id, name = "五花肉", amount = "500", unit = "克"), RecipeIngredient(recipeId = r.id, name = "生抽", amount = "2", unit = "勺"), RecipeIngredient(recipeId = r.id, name = "冰糖", amountNote = "少许"), RecipeIngredient(recipeId = r.id, name = "盐", amountNote = "适量")), listOf(RecipeStep(recipeId = r.id, content = "五花肉切成小块，冷水下锅焯水，捞出后洗净。", heat = "大火"), RecipeStep(recipeId = r.id, content = "锅中放少量油和冰糖，炒出糖色后放入五花肉翻炒。", heat = "中火", timerSeconds = 180), RecipeStep(recipeId = r.id, content = "加入生抽和热水，盖上锅盖，小火炖到肉块软糯。", heat = "小火", timerSeconds = 2700), RecipeStep(recipeId = r.id, content = "打开锅盖，收浓汤汁，按口味加盐即可。", heat = "大火", timerSeconds = 300)), listOf(home, rice)))
        val second = Recipe(name = "番茄炒蛋", categoryId = vegetable.id, preparation = "番茄洗净切块，鸡蛋打散备用。", prepareMinutes = 5, cookMinutes = 10)
        container.repository.save(RecipeFull(second, listOf(RecipeIngredient(recipeId = second.id, name = "番茄", amount = "2", unit = "个"), RecipeIngredient(recipeId = second.id, name = "鸡蛋", amount = "3", unit = "个"), RecipeIngredient(recipeId = second.id, name = "盐", amountNote = "适量")), listOf(RecipeStep(recipeId = second.id, content = "鸡蛋打散炒熟盛出，番茄切块炒软后加入鸡蛋。", heat = "中火")), listOf(home, fast)))
        container.repository.addNames(listOf("五花肉", "番茄", "鸡蛋", "生抽"))
        val shopping = container.db.dao().shopping(); container.repository.saveItem(shopping.first { it.name == "五花肉" }.copy(note = "买肥瘦相间的")); container.repository.saveItem(shopping.first { it.name == "生抽" }.copy(bought = true))
        assertEquals(2, container.db.dao().recipes().size)
        InstrumentationRegistry.getArguments().getString("timerSeconds")?.toLongOrNull()?.let { seconds -> container.timer.start(r, container.db.dao().recipes().first { it.recipe.id == r.id }.steps.first(), seconds) }
        Unit
    }
}
