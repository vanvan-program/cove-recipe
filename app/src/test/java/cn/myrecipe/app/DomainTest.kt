package cn.myrecipe.app

import cn.myrecipe.app.core.database.*
import cn.myrecipe.app.core.backup.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    private fun sample(): BackupData {
        val c = Category(name = "肉菜"); val t = Tag(name = "家常"); val r = Recipe(name = "红烧肉", categoryId = c.id, favorite = true, preparation = "五花肉切块，调料提前备好。")
        return BackupData(listOf(RecipeFull(r, listOf(RecipeIngredient(recipeId = r.id, name = "五花肉", amount = "500.25", unit = "克")), listOf(RecipeStep(recipeId = r.id, content = "小火炖煮", heat = "小火", timerSeconds = 2700)), listOf(t))), listOf(c), listOf(t), listOf(ShoppingItem(name = "番茄", note = "买熟一点的", bought = true)))
    }
    private fun rejects(block: () -> Unit) { assertThrows(Exception::class.java, block) }
    @Test fun backupRoundTripPreservesReferencesAndExactDecimals() { val data = sample(); assertEquals(data, BackupCodec.decode(BackupCodec.encode(data))) }
    @Test fun legacyBackupImportsWithEmptyPreparation() {
        val entries = BackupCodec.encode(sample()).toMutableMap()
        entries["manifest.json"] = JSONObject(entries.getValue("manifest.json")).put("version", 1).toString()
        val recipes = org.json.JSONArray(entries.getValue("recipes.json"))
        recipes.getJSONObject(0).remove("preparation")
        entries["recipes.json"] = recipes.toString()
        val old = BackupCodec.decode(entries)
        assertEquals("", old.recipes.single().recipe.preparation)
        assertEquals(sample().recipes.single().ingredients.map { it.name }, old.recipes.single().ingredients.map { it.name })
    }
    @Test fun unknownBackupVersionRejected() { val entries = BackupCodec.encode(sample()).toMutableMap(); entries["manifest.json"] = JSONObject(entries.getValue("manifest.json")).put("version", 99).toString(); rejects { BackupCodec.decode(entries) } }
    @Test fun missingDataFileRejected() { rejects { BackupCodec.decode(BackupCodec.encode(sample()) - "tags.json") } }
    @Test fun duplicateRecipeIdsRejected() { val data = sample(); rejects { BackupCodec.validate(data.copy(recipes = data.recipes + data.recipes)) } }
    @Test fun orphanCategoryRejected() { val data = sample(); rejects { BackupCodec.validate(data.copy(categories = emptyList())) } }
    @Test fun negativeIngredientQuantityRejected() { val data = sample(); val full = data.recipes.single(); rejects { BackupCodec.validate(data.copy(recipes = listOf(full.copy(ingredients = listOf(full.ingredients.single().copy(amount = "-1")))))) } }
    @Test fun traversalImagePathRejected() { val data = sample(); val full = data.recipes.single(); rejects { BackupCodec.validate(data.copy(recipes = listOf(full.copy(recipe = full.recipe.copy(coverPath = "images/../../bad.jpg"))))) } }
    @Test fun shoppingNormalizesWidthButNotSynonyms() { assertEquals(normalizeName("  Ａ番茄　"), "A番茄"); assertNotEquals(normalizeName("番茄"), normalizeName("西红柿")) }
    @Test fun normalizedShoppingDuplicatesRejected() { val data = sample(); rejects { BackupCodec.validate(data.copy(shopping = listOf(ShoppingItem(name = "Ａ"), ShoppingItem(name = "A")))) } }
    @Test fun categoryCyclesRejected() { val a = Category(name = "A"); val b = Category(name = "B", parentId = a.id); rejects { validateCategories(listOf(a.copy(parentId = b.id), b)) } }
    @Test fun fourthCategoryLevelRejected() { val a = Category(name = "A"); val b = Category(name = "B", parentId = a.id); val c = Category(name = "C", parentId = b.id); val d = Category(name = "D", parentId = c.id); rejects { validateCategories(listOf(a, b, c, d)) } }
    @Test fun categoryFilterIncludesChildren() { val data = sample(); val child = Category(name = "猪肉", parentId = data.categories.single().id); val full = data.recipes.single().let { it.copy(recipe = it.recipe.copy(categoryId = child.id)) }; assertEquals(1, filterRecipes(AppSnapshot(recipes = listOf(full), categories = data.categories + child), "", "全部", data.categories.single().id).size) }
    @Test fun searchWordsCanMatchSeparateFields() { val data = sample(); val snapshot = AppSnapshot(recipes = data.recipes, categories = data.categories); assertEquals(1, filterRecipes(snapshot, "红烧 家常 五花", "全部", null).size); assertTrue(filterRecipes(snapshot, "红烧 鸡蛋", "全部", null).isEmpty()) }
    @Test fun browseDoesNotCountAsRecentlyUsed() { val data = sample(); assertTrue(filterRecipes(AppSnapshot(recipes = data.recipes), "", "最近使用", null).isEmpty()) }
    @Test fun pinnedRecipesLeadNormalSort() { val first = RecipeFull(Recipe(name = "新", updatedAt = 100), emptyList(), emptyList(), emptyList()); val pinned = first.copy(recipe = Recipe(name = "旧", pinned = true, updatedAt = 1)); assertEquals("旧", filterRecipes(AppSnapshot(recipes = listOf(first, pinned)), "", "全部", null).first().recipe.name) }

    @Test fun versionThreeBackupPreservesSeasoningsSourcesAndOrder() {
        val old = sample(); val full = old.recipes.single()
        val data = old.copy(recipes = listOf(full.copy(recipe = full.recipe.copy(sortOrder = 7), ingredients = full.ingredients + RecipeIngredient(recipeId = full.recipe.id, name = "蚝油", amount = "2", unit = "勺", isSeasoning = true))), shopping = listOf(old.shopping.single().copy(sourceRecipeNames = listOf("番茄炒蛋", "红烧肉"))))
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
    }
    @Test fun versionTwoBackupDefaultsNewFieldsWithoutGuessingSources() {
        val entries = BackupCodec.encode(sample()).toMutableMap()
        entries["manifest.json"] = JSONObject(entries.getValue("manifest.json")).put("version", 2).toString()
        val recipes = org.json.JSONArray(entries.getValue("recipes.json")); val recipe = recipes.getJSONObject(0)
        recipe.remove("sortOrder"); recipe.getJSONArray("ingredients").getJSONObject(0).remove("isSeasoning")
        entries["recipes.json"] = recipes.toString()
        val shopping = org.json.JSONArray(entries.getValue("shopping.json")); shopping.getJSONObject(0).remove("sourceRecipeNames"); entries["shopping.json"] = shopping.toString()
        val data = BackupCodec.decode(entries)
        assertEquals(0, data.recipes.single().recipe.sortOrder); assertTrue(data.recipes.single().seasonings.isEmpty())
        assertTrue(data.shopping.single().sourceRecipeNames.isEmpty()); assertEquals(sample().recipes.single().recipe.preparation, data.recipes.single().recipe.preparation)
    }
    @Test fun manualOrderWinsOverEditsAndWorksWithFilters() {
        val full = sample().recipes.single(); val a = full.copy(recipe = full.recipe.copy(name = "甲", sortOrder = 2, updatedAt = 999))
        val b = full.copy(recipe = full.recipe.copy(id = newId(), name = "乙", sortOrder = 1, updatedAt = 1))
        assertEquals(listOf("乙", "甲"), filterRecipes(AppSnapshot(recipes = listOf(a, b)), "", "收藏", null).map { it.recipe.name })
    }

}
