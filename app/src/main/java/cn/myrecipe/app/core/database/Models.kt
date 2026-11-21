package cn.myrecipe.app.core.database

import androidx.room.*
import java.util.UUID
import java.text.Normalizer

fun newId(): String = UUID.randomUUID().toString()
fun normalizeName(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)

@Entity(tableName = "recipes", foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("categoryId")])
data class Recipe(@PrimaryKey val id: String = newId(), val name: String, val description: String = "", val coverPath: String? = null, val categoryId: String? = null, val prepareMinutes: Int? = null, val cookMinutes: Int? = null, val favorite: Boolean = false, val pinned: Boolean = false, val lastUsedAt: Long? = null, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis(), @ColumnInfo(defaultValue = "''") val preparation: String = "", @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0, val deletedAt: Long? = null)

@Entity(tableName = "ingredients", foreignKeys = [ForeignKey(entity = Recipe::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE)], indices = [Index("recipeId")])
data class RecipeIngredient(@PrimaryKey val id: String = newId(), val recipeId: String, val name: String = "", val amount: String = "", val unit: String = "", val amountNote: String = "", val sortOrder: Int = 0, @ColumnInfo(defaultValue = "0") val isSeasoning: Boolean = false)

@Entity(tableName = "steps", foreignKeys = [ForeignKey(entity = Recipe::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE)], indices = [Index("recipeId")])
data class RecipeStep(@PrimaryKey val id: String = newId(), val recipeId: String, val content: String = "", val heat: String = "未指定", val timerSeconds: Long? = null, val sortOrder: Int = 0)

@Entity(tableName = "categories", foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("parentId")])
data class Category(@PrimaryKey val id: String = newId(), val name: String, val parentId: String? = null, val sortOrder: Int = 0)
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(@PrimaryKey val id: String = newId(), val name: String)
@Entity(tableName = "recipe_tags", primaryKeys = ["recipeId", "tagId"], foreignKeys = [ForeignKey(entity = Recipe::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)], indices = [Index("tagId")])
data class RecipeTag(val recipeId: String, val tagId: String)
@Entity(tableName = "shopping", indices = [Index(value = ["normalizedName"], unique = true)])
data class ShoppingItem(@PrimaryKey val id: String = newId(), val name: String, val normalizedName: String = normalizeName(name), val note: String = "", val bought: Boolean = false, val sortOrder: Int = 0, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis(), @ColumnInfo(defaultValue = "'[]'") val sourceRecipeNames: List<String> = emptyList())
@Entity(tableName = "active_timer")
data class ActiveTimer(@PrimaryKey val id: Int = 1, val recipeId: String, val stepId: String, val recipeName: String, val stepTitle: String, val durationSeconds: Long, val deadlineElapsed: Long, val bootCount: Int, val remainingMillis: Long = 0, val state: String = "RUNNING", val token: String = newId())

data class RecipeFull(@Embedded val recipe: Recipe,
    @Relation(parentColumn = "id", entityColumn = "recipeId") val ingredients: List<RecipeIngredient>,
    @Relation(parentColumn = "id", entityColumn = "recipeId") val steps: List<RecipeStep>,
    @Relation(parentColumn = "id", entityColumn = "id", associateBy = Junction(RecipeTag::class, parentColumn = "recipeId", entityColumn = "tagId")) val tags: List<Tag>) {
    val foodIngredients: List<RecipeIngredient> get() = ingredients.filterNot { it.isSeasoning }.sortedBy { it.sortOrder }
    val seasonings: List<RecipeIngredient> get() = ingredients.filter { it.isSeasoning }.sortedBy { it.sortOrder }
}
class ShoppingSourcesConverter {
    @TypeConverter fun encode(names: List<String>): String = org.json.JSONArray(names).toString()
    @TypeConverter fun decode(json: String): List<String> = org.json.JSONArray(json).let { array -> (0 until array.length()).map { array.getString(it) } }
}
data class AppSnapshot(val recipes: List<RecipeFull> = emptyList(), val categories: List<Category> = emptyList(), val tags: List<Tag> = emptyList(), val shopping: List<ShoppingItem> = emptyList(), val timer: ActiveTimer? = null, val trash: List<RecipeFull> = emptyList())

fun categoryPath(id: String?, categories: List<Category>): String {
    val parts = mutableListOf<String>(); var current = id; val seen = mutableSetOf<String>()
    while (current != null && seen.add(current)) { val c = categories.find { it.id == current } ?: break; parts.add(0, c.name); current = c.parentId }
    return parts.joinToString(" / ").ifEmpty { "未分类" }
}
fun categoryDescendants(id: String, categories: List<Category>): Set<String> {
    val result = mutableSetOf(id)
    var changed: Boolean
    do { changed = false; categories.forEach { if (it.parentId in result && result.add(it.id)) changed = true } } while (changed)
    return result
}
fun validateCategories(categories: List<Category>) {
    require(categories.map { it.id }.distinct().size == categories.size) { "分类标识重复" }
    categories.forEach { c ->
        require(c.name.isNotBlank()) { "分类名称不能为空" }
        var cursor: Category? = c; val seen = mutableSetOf<String>(); var depth = 0
        while (cursor != null) {
            require(seen.add(cursor.id)) { "分类不能循环引用" }; require(++depth <= 3) { "分类最多三级" }
            cursor = cursor.parentId?.let { parent -> categories.find { it.id == parent } ?: error("父分类不存在") }
        }
    }
}
enum class RecipeSort(val title: String) { MANUAL("自定义"), CREATED("添加时间"), UPDATED("修改时间"), NAME("名称"), COOK("烹饪时长") }
fun filterRecipes(snapshot: AppSnapshot, query: String, mode: String, categoryId: String?, sort: RecipeSort = RecipeSort.MANUAL, descending: Boolean = false): List<RecipeFull> {
    val words = normalizeName(query).split(Regex("\\s+")).filter { it.isNotBlank() }
    val allowed = categoryId?.let { categoryDescendants(it, snapshot.categories) }
    val comparator: Comparator<RecipeFull> = when (sort) {
        RecipeSort.MANUAL -> compareByDescending<RecipeFull> { it.recipe.pinned }.thenBy { it.recipe.sortOrder }.thenByDescending { if (mode == "最近使用") it.recipe.lastUsedAt else it.recipe.updatedAt }
        RecipeSort.CREATED -> compareBy { it.recipe.createdAt }
        RecipeSort.UPDATED -> compareBy { it.recipe.updatedAt }
        RecipeSort.NAME -> java.text.Collator.getInstance(java.util.Locale.SIMPLIFIED_CHINESE).let { collator -> Comparator { a, b -> collator.compare(a.recipe.name, b.recipe.name) } }
        RecipeSort.COOK -> compareBy { it.recipe.cookMinutes }
    }
    val order = if (sort != RecipeSort.MANUAL && descending) comparator.reversed() else comparator
    return snapshot.recipes.filter { full ->
        (mode != "收藏" || full.recipe.favorite) && (mode != "最近使用" || full.recipe.lastUsedAt != null) &&
        (allowed == null || full.recipe.categoryId in allowed) && words.all { word ->
            (listOf(full.recipe.name, categoryPath(full.recipe.categoryId, snapshot.categories)) + full.ingredients.map { it.name } + full.tags.map { it.name }).any { normalizeName(it).contains(word, ignoreCase = true) }
        }
    }.sortedWith(order.thenBy { it.recipe.id })
}
