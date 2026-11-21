package cn.myrecipe.app.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Transaction @Query("SELECT * FROM recipes") fun observeRecipes(): Flow<List<RecipeFull>>
    @Query("SELECT * FROM categories ORDER BY sortOrder, name") fun observeCategories(): Flow<List<Category>>
    @Query("SELECT * FROM tags ORDER BY name") fun observeTags(): Flow<List<Tag>>
    @Query("SELECT * FROM shopping ORDER BY sortOrder, createdAt") fun observeShopping(): Flow<List<ShoppingItem>>
    @Query("SELECT * FROM active_timer WHERE id = 1") fun observeTimer(): Flow<ActiveTimer?>
    @Transaction @Query("SELECT * FROM recipes") suspend fun recipes(): List<RecipeFull>
    @Query("SELECT * FROM categories") suspend fun categories(): List<Category>
    @Query("SELECT * FROM tags") suspend fun tags(): List<Tag>
    @Query("SELECT * FROM recipe_tags") suspend fun links(): List<RecipeTag>
    @Query("SELECT * FROM shopping ORDER BY sortOrder, createdAt") suspend fun shopping(): List<ShoppingItem>
    @Query("SELECT * FROM active_timer WHERE id = 1") suspend fun timer(): ActiveTimer?
    @Upsert suspend fun putRecipe(value: Recipe)
    @Upsert suspend fun putIngredients(value: List<RecipeIngredient>)
    @Upsert suspend fun putSteps(value: List<RecipeStep>)
    @Upsert suspend fun putCategories(value: List<Category>)
    @Upsert suspend fun putTags(value: List<Tag>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putLinks(value: List<RecipeTag>)
    @Upsert suspend fun putShopping(value: ShoppingItem)
    @Upsert suspend fun putTimer(value: ActiveTimer)
    @Query("UPDATE recipes SET sortOrder = :order WHERE id = :id") suspend fun setRecipeOrder(id: String, order: Int)
    @Query("DELETE FROM ingredients WHERE recipeId = :id") suspend fun deleteIngredients(id: String)
    @Query("DELETE FROM steps WHERE recipeId = :id") suspend fun deleteSteps(id: String)
    @Query("DELETE FROM recipe_tags WHERE recipeId = :id") suspend fun deleteLinks(id: String)
    @Query("UPDATE recipes SET deletedAt = :at WHERE id = :id") suspend fun setDeletedAt(id: String, at: Long?)
    @Query("DELETE FROM recipes WHERE id = :id AND deletedAt IS NOT NULL") suspend fun purgeTrashed(id: String)
    @Query("DELETE FROM recipes WHERE id = :id") suspend fun deleteRecipe(id: String)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: String)
    @Query("DELETE FROM tags WHERE id = :id") suspend fun deleteTag(id: String)
    @Query("DELETE FROM shopping WHERE id = :id") suspend fun deleteShopping(id: String)
    @Query("DELETE FROM shopping WHERE bought = 1") suspend fun clearBought()
    @Query("DELETE FROM shopping") suspend fun clearShopping()
    @Query("DELETE FROM active_timer") suspend fun clearTimer()
    @Query("DELETE FROM recipes") suspend fun clearRecipes()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM tags") suspend fun clearTags()
}
@Database(entities = [Recipe::class, RecipeIngredient::class, RecipeStep::class, Category::class, Tag::class, RecipeTag::class, ShoppingItem::class, ActiveTimer::class], version = 4, exportSchema = true)
@TypeConverters(ShoppingSourcesConverter::class)
abstract class RecipeDatabase : RoomDatabase() {
    abstract fun dao(): RecipeDao
    companion object {
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN deletedAt INTEGER")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ingredients ADD COLUMN isSeasoning INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shopping ADD COLUMN sourceRecipeNames TEXT NOT NULL DEFAULT '[]'")
            }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN preparation TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
