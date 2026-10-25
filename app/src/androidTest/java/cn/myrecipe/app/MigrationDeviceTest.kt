package cn.myrecipe.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import cn.myrecipe.app.core.database.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MigrationDeviceTest {
    @Test fun versionOneDatabaseMigratesWithoutLosingData() = checkMigration(1)
    @Test fun versionTwoDatabaseMigratesWithoutLosingData() = checkMigration(2)
    private fun checkMigration(version: Int) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<RecipeApplication>()
        val file = context.getDatabasePath("migration-${newId()}.db")
        file.parentFile!!.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("cn.myrecipe.app.core.database.RecipeDatabase/$version.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val id = newId(); val category = newId(); val tag = newId()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES (42, ?)", arrayOf(schema.getString("identityHash")))
            old.execSQL("INSERT INTO categories VALUES (?, '家常', NULL, 0)", arrayOf(category))
            old.execSQL("INSERT INTO tags VALUES (?, '下饭')", arrayOf(tag))
            old.execSQL("INSERT INTO recipes (id, name, description, coverPath, categoryId, prepareMinutes, cookMinutes, favorite, pinned, lastUsedAt, createdAt, updatedAt) VALUES (?, '旧版菜谱', '简介', NULL, ?, 5, 10, 1, 1, 123, 100, 200)", arrayOf(id, category))
            old.execSQL("INSERT INTO ingredients VALUES (?, ?, '番茄', '2', '个', '', 0)", arrayOf(newId(), id))
            old.execSQL("INSERT INTO steps VALUES (?, ?, '翻炒', '中火', 60, 0)", arrayOf(newId(), id))
            old.execSQL("INSERT INTO recipe_tags VALUES (?, ?)", arrayOf(id, tag))
            old.execSQL("INSERT INTO shopping VALUES (?, '盐', '盐', '小袋', 1, 0, 100, 200)", arrayOf(newId()))
            if (version == 2) old.execSQL("UPDATE recipes SET preparation = '保留备菜'")
            old.version = version
        }
        val db = Room.databaseBuilder(context, RecipeDatabase::class.java, file.name).addMigrations(RecipeDatabase.MIGRATION_1_2, RecipeDatabase.MIGRATION_2_3).build()
        try {
            val full = db.dao().recipes().single()
            assertEquals("旧版菜谱", full.recipe.name)
            assertEquals(if (version == 2) "保留备菜" else "", full.recipe.preparation)
            assertEquals(0, full.recipe.sortOrder); assertTrue(full.seasonings.isEmpty()); assertTrue(db.dao().shopping().single().sourceRecipeNames.isEmpty())
            assertTrue(full.recipe.favorite); assertTrue(full.recipe.pinned)
            assertEquals(123L, full.recipe.lastUsedAt)
            assertEquals(category, full.recipe.categoryId)
            assertEquals("番茄", full.ingredients.single().name)
            assertEquals(60L, full.steps.single().timerSeconds)
            assertEquals("下饭", full.tags.single().name)
            assertEquals("小袋", db.dao().shopping().single().note)
            assertTrue(db.dao().shopping().single().bought)
            db.dao().putRecipe(full.recipe.copy(preparation = "番茄切块"))
            assertEquals("番茄切块", db.dao().recipes().single().recipe.preparation)
        } finally { db.close(); context.deleteDatabase(file.name) }
    }
}
