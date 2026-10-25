package cn.myrecipe.app.core.backup

import cn.myrecipe.app.core.database.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BackupData(val recipes: List<RecipeFull>, val categories: List<Category>, val tags: List<Tag>, val shopping: List<ShoppingItem>)

object BackupCodec {
    const val VERSION = 3
    val names = setOf("manifest.json", "recipes.json", "categories.json", "tags.json", "shopping.json")
    private fun objects(array: JSONArray): List<JSONObject> {
        require(array.length() <= 50000) { "备份项目过多" }
        return (0 until array.length()).map { array.getJSONObject(it) }
    }
    private fun JSONObject.nullString(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.nullLong(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
    fun encode(data: BackupData): Map<String, String> = linkedMapOf(
        "manifest.json" to JSONObject().put("format", "my-recipe-backup").put("version", VERSION).put("createdAt", System.currentTimeMillis()).toString(),
        "categories.json" to JSONArray(data.categories.map { JSONObject().put("id", it.id).put("name", it.name).putNullable("parentId", it.parentId).put("sortOrder", it.sortOrder) }).toString(),
        "tags.json" to JSONArray(data.tags.map { JSONObject().put("id", it.id).put("name", it.name) }).toString(),
        "shopping.json" to JSONArray(data.shopping.map { JSONObject().put("id", it.id).put("name", it.name).put("sourceRecipeNames", JSONArray(it.sourceRecipeNames)).put("note", it.note).put("bought", it.bought).put("sortOrder", it.sortOrder).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt) }).toString(),
        "recipes.json" to JSONArray(data.recipes.map { full ->
            val r = full.recipe
            JSONObject().put("id", r.id).put("name", r.name).put("description", r.description).put("preparation", r.preparation).put("sortOrder", r.sortOrder).putNullable("coverPath", r.coverPath).putNullable("categoryId", r.categoryId)
                .putNullable("prepareMinutes", r.prepareMinutes).putNullable("cookMinutes", r.cookMinutes).put("favorite", r.favorite).put("pinned", r.pinned)
                .putNullable("lastUsedAt", r.lastUsedAt).put("createdAt", r.createdAt).put("updatedAt", r.updatedAt)
                .put("tagIds", JSONArray(full.tags.map { it.id }))
                .put("ingredients", JSONArray(full.ingredients.map { JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount).put("unit", it.unit).put("amountNote", it.amountNote).put("isSeasoning", it.isSeasoning).put("sortOrder", it.sortOrder) }))
                .put("steps", JSONArray(full.steps.map { JSONObject().put("id", it.id).put("content", it.content).put("heat", it.heat).putNullable("timerSeconds", it.timerSeconds).put("sortOrder", it.sortOrder) }))
        }).toString()
    )
    fun decode(entries: Map<String, String>): BackupData {
        require(entries.keys == names) { "备份缺少必要的数据文件" }
        val manifest = JSONObject(entries.getValue("manifest.json"))
        require(manifest.getString("format") == "my-recipe-backup" && manifest.get("version") in listOf(1, 2, VERSION)) { "不支持此备份格式或版本" }
        val categories = objects(JSONArray(entries.getValue("categories.json"))).map { Category(it.getString("id"), it.getString("name"), it.nullString("parentId"), it.getInt("sortOrder")) }
        val tags = objects(JSONArray(entries.getValue("tags.json"))).map { Tag(it.getString("id"), it.getString("name")) }
        val shopping = objects(JSONArray(entries.getValue("shopping.json"))).map { ShoppingItem(id = it.getString("id"), name = it.getString("name"), note = it.getString("note"), bought = it.getBoolean("bought"), sortOrder = it.getInt("sortOrder"), createdAt = it.getLong("createdAt"), updatedAt = it.getLong("updatedAt"), sourceRecipeNames = it.optJSONArray("sourceRecipeNames")?.let { a -> (0 until a.length()).map { index -> a.getString(index) } }.orEmpty()) }
        val recipes = objects(JSONArray(entries.getValue("recipes.json"))).map { j ->
            val id = j.getString("id")
            val recipe = Recipe(id, j.getString("name"), j.getString("description"), j.nullString("coverPath"), j.nullString("categoryId"), j.nullLong("prepareMinutes")?.let { require(it in 0..Int.MAX_VALUE); it.toInt() }, j.nullLong("cookMinutes")?.let { require(it in 0..Int.MAX_VALUE); it.toInt() }, j.getBoolean("favorite"), j.getBoolean("pinned"), j.nullLong("lastUsedAt"), j.getLong("createdAt"), j.getLong("updatedAt"), j.optString("preparation", ""), j.optInt("sortOrder", 0))
            val ingredients = objects(j.getJSONArray("ingredients")).map { RecipeIngredient(it.getString("id"), id, it.getString("name"), it.getString("amount"), it.getString("unit"), it.getString("amountNote"), it.getInt("sortOrder"), it.optBoolean("isSeasoning", false)) }
            val steps = objects(j.getJSONArray("steps")).map { RecipeStep(it.getString("id"), id, it.getString("content"), it.getString("heat"), it.nullLong("timerSeconds"), it.getInt("sortOrder")) }
            val tagIds = j.getJSONArray("tagIds"); require(tagIds.length() <= tags.size) { "标签关联无效" }
            val recipeTags = (0 until tagIds.length()).map { index -> tags.find { it.id == tagIds.getString(index) } ?: error("标签关联不存在") }
            require(recipeTags.distinctBy { it.id }.size == recipeTags.size) { "标签关联重复" }
            RecipeFull(recipe, ingredients, steps, recipeTags)
        }
        return BackupData(recipes, categories, tags, shopping).also { validate(it) }
    }
    fun validate(data: BackupData) {
        fun ids(values: List<String>) {
            require(values.size == values.distinct().size) { "备份标识重复" }
            values.forEach { require(runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false)) { "备份标识无效" } }
        }
        ids(data.recipes.map { it.recipe.id }); ids(data.categories.map { it.id }); ids(data.tags.map { it.id }); ids(data.shopping.map { it.id })
        ids(data.recipes.flatMap { it.ingredients }.map { it.id }); ids(data.recipes.flatMap { it.steps }.map { it.id })
        validateCategories(data.categories)
        require(data.tags.all { it.name.isNotBlank() } && data.tags.map { normalizeName(it.name) }.distinct().size == data.tags.size) { "标签名称无效或重复" }
        require(data.shopping.all { it.name.isNotBlank() } && data.shopping.map { normalizeName(it.name) }.distinct().size == data.shopping.size) { "购物食材名称无效或重复" }
        require(data.shopping.all { item -> item.sourceRecipeNames.all { it.isNotBlank() } && item.sourceRecipeNames.distinct().size == item.sourceRecipeNames.size }) { "购物来源菜名无效或重复" }
        data.recipes.forEach { full ->
            val r = full.recipe
            require(r.name.isNotBlank() && (r.prepareMinutes == null || r.prepareMinutes >= 0) && (r.cookMinutes == null || r.cookMinutes >= 0)) { "菜谱信息无效" }
            require(r.categoryId == null || data.categories.any { it.id == r.categoryId }) { "菜谱分类不存在" }
            require(r.coverPath == null || Regex("images/[a-zA-Z0-9_-]+\\.jpg").matches(r.coverPath)) { "图片路径无效" }
            full.ingredients.forEach { require(it.name.isNotBlank() && (it.amount.isEmpty() || it.amount.toBigDecimalOrNull()?.signum()?.let { s -> s >= 0 } == true)) { "食材数量或名称无效" } }
            full.steps.forEach { require(it.content.isNotBlank() && it.heat in listOf("未指定", "大火", "中火", "小火") && (it.timerSeconds == null || it.timerSeconds in 1..604800)) { "步骤无效" } }
        }
    }
}
