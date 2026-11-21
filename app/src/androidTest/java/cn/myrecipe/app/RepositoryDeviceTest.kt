package cn.myrecipe.app

import android.app.NotificationManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import cn.myrecipe.app.core.database.*
import cn.myrecipe.app.core.backup.*
import cn.myrecipe.app.core.files.ImageStore
import cn.myrecipe.app.core.timer.*
import cn.myrecipe.app.data.repository.LocalRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.zip.*

class RepositoryDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<RecipeApplication>()
    private lateinit var db: RecipeDatabase
    private lateinit var repo: LocalRepository
    private lateinit var timer: TimerController
    private lateinit var backup: ZipBackupRepository
    private lateinit var temp: File
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, RecipeDatabase::class.java).build()
        val gate = Mutex(); repo = LocalRepository(db, gate); timer = TimerController(context, db, gate)
        backup = ZipBackupRepository(context, db, ImageStore(context), timer, gate)
        temp = File(context.cacheDir, "test-${newId()}").apply { mkdirs() }
    }
    @After fun tearDown() = runBlocking { timer.cancel(); db.close(); temp.deleteRecursively(); Unit }
    private suspend fun recipe(): RecipeFull {
        val c = Category(name = "肉菜"); val t = Tag(name = "家常"); repo.saveCategory(c); repo.saveTag(t)
        val r = Recipe(name = "红烧肉", categoryId = c.id, preparation = "五花肉切块，调料装碗。")
        val full = RecipeFull(r, listOf(RecipeIngredient(recipeId = r.id, name = "五花肉", amount = "500", unit = "克")), listOf(RecipeStep(recipeId = r.id, content = "炖煮", heat = "小火", timerSeconds = 60)), listOf(t)); repo.save(full); return full
    }
    private fun shell(command: String) { InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText() } }
    private suspend fun rejects(block: suspend () -> Unit) { try { block(); fail("操作应被拒绝") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }
    @Test fun recipeSaveEditDeleteCascades() = runBlocking {
        val full = recipe(); val edited = full.copy(recipe = full.recipe.copy(name = "家常红烧肉"), ingredients = emptyList(), steps = emptyList(), tags = emptyList()); repo.save(edited)
        assertEquals("家常红烧肉", db.dao().recipes().single().recipe.name); assertTrue(db.dao().recipes().single().ingredients.isEmpty())
        repo.delete(full.recipe.id)
        assertNotNull(db.dao().recipes().single().recipe.deletedAt)
        repo.restoreRecipe(full.recipe.id)
        assertNull(db.dao().recipes().single().recipe.deletedAt)
        assertEquals("家常红烧肉", db.dao().recipes().single().recipe.name)
        assertEquals(edited.ingredients, db.dao().recipes().single().ingredients)
        repo.delete(full.recipe.id); repo.purgeRecipe(full.recipe.id)
        assertTrue(db.dao().recipes().isEmpty()); assertTrue(db.dao().links().isEmpty())
    }
    @Test fun trashBackupRetainsIngredientsAndStepsAfterRestore() = runBlocking {
        val full = recipe()
        repo.delete(full.recipe.id)
        val trashed = db.dao().recipes().single()
        assertNotNull(trashed.recipe.deletedAt)
        rejects { repo.save(full) }
        val file = File(temp, "trash.zip")
        backup.export(Uri.fromFile(file))
        repo.purgeRecipe(full.recipe.id)
        backup.restore(Uri.fromFile(file))
        assertEquals(trashed, db.dao().recipes().single())
        repo.restoreRecipe(full.recipe.id)
        val restored = db.dao().recipes().single()
        assertNull(restored.recipe.deletedAt)
        assertEquals(trashed.ingredients, restored.ingredients)
        assertEquals(trashed.steps, restored.steps)
    }
    @Test fun failedRecipeSavePreservesPreviousChildren() = runBlocking {
        val full = recipe(); rejects { repo.save(full.copy(recipe = full.recipe.copy(name = ""), ingredients = emptyList())) }; assertEquals("五花肉", db.dao().recipes().single().ingredients.single().name)
    }
    @Test fun categoryDeletionPromotesChildrenAndUncategorizesRecipe() = runBlocking {
        val full = recipe(); val parent = full.recipe.categoryId!!; val child = Category(name = "猪肉", parentId = parent); repo.saveCategory(child); repo.removeCategory(parent)
        assertNull(db.dao().recipes().single().recipe.categoryId); assertNull(db.dao().categories().single().parentId)
    }
    @Test fun categoryMoveRejectsTooDeepSubtree() = runBlocking {
        val a = Category(name = "A"); val b = Category(name = "B", parentId = a.id); val c = Category(name = "C", parentId = b.id); val x = Category(name = "X")
        listOf(a, b, c, x).forEach { repo.saveCategory(it) }; rejects { repo.saveCategory(a.copy(parentId = x.id)) }; assertNull(db.dao().categories().find { it.id == a.id }!!.parentId)
    }
    @Test fun shoppingDedupeKeepsNotesAndRestoresBought() = runBlocking {
        repo.addNames(listOf("Ａ", "A", "番茄")); assertEquals(2, db.dao().shopping().size)
        val item = db.dao().shopping().first { it.normalizedName == "A" }; repo.saveItem(item.copy(note = "采购备注", bought = true)); assertEquals(1, repo.addNames(listOf(" A ", "Ａ")))
        val restored = db.dao().shopping().first { it.id == item.id }; assertFalse(restored.bought); assertEquals("采购备注", restored.note)
    }
    @Test fun shoppingSurvivesRecipeDeletionAndUndo() = runBlocking {
        val full = recipe(); repo.addNames(full.ingredients.map { it.name }); repo.delete(full.recipe.id)
        val item = db.dao().shopping().single(); repo.deleteItem(item.id); repo.restoreItem(item); assertEquals(item, db.dao().shopping().single())
    }
    @Test fun renamingToDuplicateShoppingItemRejected() = runBlocking {
        repo.addNames(listOf("番茄", "鸡蛋")); val item = db.dao().shopping().first { it.name == "鸡蛋" }; rejects { repo.saveItem(item.copy(name = "番茄")) }; assertEquals(2, db.dao().shopping().size)
    }
    @Test fun zipRoundTripIncludesImageTagsAndShopping() = runBlocking {
        val full = recipe(); val images = ImageStore(context); val path = "images/${newId()}.jpg"; val source = images.file(path); source.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888); source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        repo.save(full.copy(recipe = full.recipe.copy(coverPath = path))); repo.addNames(listOf("五花肉")); val item = db.dao().shopping().single(); repo.saveItem(item.copy(note = "肥瘦相间", bought = true))
        val file = File(temp, "backup.zip"); backup.export(Uri.fromFile(file)); repo.delete(full.recipe.id); repo.clearShopping(false)
        assertEquals(1, backup.restore(Uri.fromFile(file))); val restored = db.dao().recipes().single()
        assertEquals(full.recipe.id, restored.recipe.id); assertEquals(full.recipe.preparation, restored.recipe.preparation); assertEquals("家常", restored.tags.single().name); assertEquals("500", restored.ingredients.single().amount); assertTrue(images.file(restored.recipe.coverPath!!).isFile); assertNotEquals(path, restored.recipe.coverPath)
        assertEquals("肥瘦相间", db.dao().shopping().single().note); assertTrue(db.dao().shopping().single().bought)
        source.delete(); images.file(restored.recipe.coverPath!!).delete(); Unit
    }
    @Test fun invalidZipCannotOverwriteExistingDatabase() = runBlocking {
        recipe(); val file = File(temp, "invalid.zip"); file.writeText("broken"); rejects { backup.restore(Uri.fromFile(file)) }; assertEquals("红烧肉", db.dao().recipes().single().recipe.name)
    }
    @Test fun importedImageIsCopiedScaledAndIndependentFromOriginal() = runBlocking {
        val source = File(temp, "large.jpg"); val bitmap = Bitmap.createBitmap(2200, 1100, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        val images = ImageStore(context); val path = images.import(Uri.fromFile(source)); val imported = images.file(path)
        assertTrue(imported.isFile); val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }; android.graphics.BitmapFactory.decodeFile(imported.path, options)
        assertTrue(options.outWidth <= 1600); source.delete(); assertTrue(imported.isFile); imported.delete(); Unit
    }
    @Test fun unsafeZipPathCannotOverwriteExistingData() = runBlocking {
        recipe(); val file = File(temp, "unsafe.zip"); ZipOutputStream(file.outputStream()).use { zip -> zip.putNextEntry(ZipEntry("../escape.jpg")); zip.write(byteArrayOf(1)); zip.closeEntry() }
        rejects { backup.restore(Uri.fromFile(file)) }; assertEquals(1, db.dao().recipes().size); assertFalse(File(context.cacheDir, "escape.jpg").exists())
    }
    @Test fun missingImageCannotOverwriteExistingData() = runBlocking {
        val full = recipe(); val file = File(temp, "missing-image.zip"); val data = BackupData(listOf(full.copy(recipe = full.recipe.copy(coverPath = "images/${newId()}.jpg"))), db.dao().categories(), db.dao().tags(), emptyList())
        ZipOutputStream(file.outputStream()).use { zip -> BackupCodec.encode(data).forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() } }
        rejects { backup.restore(Uri.fromFile(file)) }; assertNull(db.dao().recipes().single().recipe.coverPath)
    }
    @Test fun timerLifecycleUsesDeadlineAndProtectsAgainstStaleCallbacks() = runBlocking {
        shell("appops set cn.myrecipe.app SCHEDULE_EXACT_ALARM allow"); shell("pm grant cn.myrecipe.app android.permission.POST_NOTIFICATIONS")
        val full = recipe(); timer.start(full.recipe, full.steps.single(), 60); val original = db.dao().timer()!!
        timer.pause(); assertEquals("PAUSED", db.dao().timer()!!.state); timer.change(90); assertEquals(90000L, db.dao().timer()!!.remainingMillis); timer.resume(); val running = db.dao().timer()!!
        assertEquals("RUNNING", running.state); assertTrue(remainingMillis(running) > 85000); timer.finish(original.token); assertEquals("RUNNING", db.dao().timer()!!.state)
        timer.cancel(); assertNull(db.dao().timer())
    }
    @Test fun timerEndPostsNotification() = runBlocking {
        shell("appops set cn.myrecipe.app SCHEDULE_EXACT_ALARM allow"); shell("pm grant cn.myrecipe.app android.permission.POST_NOTIFICATIONS")
        // The foreground alert service uses the application's persistent timer database.
        val actualTimer = context.container.timer
        val full = recipe(); actualTimer.start(full.recipe, full.steps.single(), 1); val active = context.container.db.dao().timer()!!; delay(1200); actualTimer.finish(active.token)
        assertEquals("FINISHED", context.container.db.dao().timer()!!.state)
        // NotificationManager delivers asynchronously; wait for the system to publish it.
        val notifications = context.getSystemService(NotificationManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (notifications.activeNotifications.none { it.id == 42 } && SystemClock.elapsedRealtime() < deadline) delay(50)
        assertTrue(notifications.activeNotifications.any { it.id == 42 })
        actualTimer.acknowledge(active.token)
    }
    @Test fun rebootDetectionInterruptsWithoutRinging() = runBlocking {
        val full = recipe(); db.dao().putTimer(ActiveTimer(recipeId = full.recipe.id, stepId = full.steps.single().id, recipeName = "红烧肉", stepTitle = "炖煮", durationSeconds = 60, deadlineElapsed = SystemClock.elapsedRealtime() + 60000, bootCount = timer.bootCount() - 1))
        timer.reconcile(); assertEquals("INTERRUPTED", db.dao().timer()!!.state)
    }
    @Test fun openingKitchenMarksRecentlyUsedOnly() = runBlocking { val full = recipe(); assertNull(db.dao().recipes().single().recipe.lastUsedAt); repo.markUsed(full.recipe.id); assertNotNull(db.dao().recipes().single().recipe.lastUsedAt) }

    @Test fun shoppingSourcesMergeWithoutLosingNotesAndSurviveDeleteUndo() = runBlocking {
        repo.saveItem(ShoppingItem(name = "青菜", note = "手动备注", bought = true))
        assertEquals(1, repo.addNames(listOf("青菜", " 青菜 "), mapOf("青菜" to listOf("蚝油青菜", "清炒青菜", "蚝油青菜"))))
        val item = db.dao().shopping().single()
        assertEquals(listOf("蚝油青菜", "清炒青菜"), item.sourceRecipeNames); assertEquals("手动备注", item.note); assertFalse(item.bought)
        repo.addNames(listOf("青菜"), mapOf("青菜" to listOf("蚝油青菜")))
        repo.addNames(listOf("盐"))
        assertTrue(db.dao().shopping().single { it.name == "盐" }.sourceRecipeNames.isEmpty())
        assertEquals(2, db.dao().shopping().single { it.name == "青菜" }.sourceRecipeNames.size)
        repo.deleteItem(item.id); repo.restoreItem(item); assertEquals(item, db.dao().shopping().single { it.id == item.id })
    }
    @Test fun filteredRecipeReorderPreservesHiddenSlotsPinnedAndEdits() = runBlocking {
        val recipes = (1..5).map { n -> RecipeFull(Recipe(name = "菜$n", pinned = n == 1), emptyList(), emptyList(), emptyList()) }
        recipes.forEach { repo.save(it) }
        val ids = recipes.map { it.recipe.id }; repo.reorderRecipes(ids)
        repo.reorderRecipes(listOf(ids[4], ids[2]))
        fun order() = runBlocking { filterRecipes(AppSnapshot(recipes = db.dao().recipes()), "", "全部", null).map { it.recipe.id } }
        assertEquals(listOf(ids[0], ids[1], ids[4], ids[3], ids[2]), order())
        repo.save(recipes[4].copy(recipe = recipes[4].recipe.copy(description = "编辑保留顺序")))
        assertEquals(listOf(ids[0], ids[1], ids[4], ids[3], ids[2]), order())
        repo.reorderRecipes(listOf(ids[2], ids[0])); assertEquals(ids[0], order().first())
    }
    @Test fun zipRoundTripPreservesSeasoningsOrderAndShoppingSources() = runBlocking {
        val full = recipe(); val seasoning = RecipeIngredient(recipeId = full.recipe.id, name = "老抽", amount = "1.25", unit = "勺", amountNote = "上色", isSeasoning = true)
        repo.save(full.copy(ingredients = full.ingredients + seasoning)); repo.reorderRecipes(listOf(full.recipe.id))
        repo.addNames(listOf("五花肉"), mapOf("五花肉" to listOf(full.recipe.name)))
        val before = db.dao().recipes().single(); val shopping = db.dao().shopping()
        val file = File(temp, "140.zip"); backup.export(Uri.fromFile(file)); repo.delete(full.recipe.id); repo.clearShopping(false)
        backup.restore(Uri.fromFile(file))
        assertEquals(before, db.dao().recipes().single()); assertEquals(shopping, db.dao().shopping())
    }

}
