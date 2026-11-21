package cn.myrecipe.app

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.myrecipe.app.core.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.junit.*
import org.junit.Assert.*

class UiDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as RecipeApplication).container
    @Before fun clean() = runBlocking {
        container.timer.cancel()
        container.gate.withLock { val dao = container.db.dao(); dao.clearRecipes(); dao.clearCategories(); dao.clearTags(); dao.clearShopping() }
        container.preferences.saveHome("全部", null)
        Unit
    }
    private fun seed(): RecipeFull = runBlocking {
        val recipe = Recipe(name = "番茄炒蛋"); val full = RecipeFull(recipe, listOf(RecipeIngredient(recipeId = recipe.id, name = "番茄", amount = "2", unit = "个"), RecipeIngredient(recipeId = recipe.id, name = "鸡蛋", amount = "3", unit = "个")), listOf(RecipeStep(recipeId = recipe.id, content = "鸡蛋打散后炒熟", heat = "中火", timerSeconds = 60), RecipeStep(recipeId = recipe.id, content = "加入番茄翻炒", heat = "大火")), emptyList())
        container.repository.save(full); full
    }
    @Test fun createRecipeThroughEditor() {
        compose.onNodeWithTag("newRecipe").performClick()
        compose.onNodeWithText("菜名（必填）").performTextInput("青椒土豆丝")
        compose.onNodeWithText("保存菜谱", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("青椒土豆丝").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("青椒土豆丝").assertIsDisplayed()
        assertEquals("青椒土豆丝", runBlocking { container.db.dao().recipes().single().recipe.name })
    }
    @Test fun unsavedEditorBackOffersSaveAndDiscard() {
        compose.onNodeWithTag("newRecipe").performClick(); compose.onNodeWithText("菜名（必填）").performTextInput("测试菜")
        compose.onNodeWithText("返回").performClick(); compose.onNodeWithText("保存修改？").assertIsDisplayed(); compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithText("我的菜谱").assertIsDisplayed(); assertTrue(runBlocking { container.db.dao().recipes().isEmpty() })
    }
    @Test fun shoppingManualEntryEditAndBoughtToggle() {
        compose.onNodeWithTag("nav-shopping").performClick(); compose.onNodeWithTag("addShopping").performClick()
        compose.onNodeWithText("食材名称").assertIsFocused()
        compose.onNodeWithText("食材名称").performTextInput("盐"); compose.onNodeWithText("采购备注（可选）").performTextInput("小袋装"); compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("盐").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("小袋装").assertIsDisplayed(); compose.onNode(isToggleable()).performClick()
        compose.waitUntil(5000) { runBlocking { container.db.dao().shopping().single().bought } }
        compose.onNodeWithText("已买", useUnmergedTree = true).assertExists()
    }
    @Test fun kitchenNavigationKeepsScreenAwakeThenRestores() {
        val full = seed(); compose.waitUntil(5000) { compose.onAllNodesWithText(full.recipe.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(full.recipe.name).performClick(); compose.onNodeWithText("开始做饭").performClick()
        compose.onNodeWithText("第 1 / 2 步").assertIsDisplayed(); compose.onNodeWithText("下一步").performClick(); compose.onNodeWithText("第 2 / 2 步").assertIsDisplayed()
        assertTrue(compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        compose.onNodeWithText("查看食材").performClick(); compose.onNodeWithText("返回步骤").performClick(); compose.onNodeWithText("返回").performClick()
        compose.waitForIdle(); assertEquals(0, compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        assertNotNull(runBlocking { container.db.dao().recipes().single().recipe.lastUsedAt })
    }
    @Test fun swipePagesAndNavigationStayInSync() {
        compose.onNodeWithTag("mainPager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("nav-shopping").assertIsSelected()
        compose.onNodeWithTag("addShopping").assertIsDisplayed()
        compose.onNodeWithTag("mainPager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithTag("mainPager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("nav-shopping").assertIsSelected()
        compose.onNodeWithTag("nav-recipes").performClick()
        compose.onNodeWithTag("newRecipe").assertIsDisplayed()
    }
    @Test fun circleCheckMultiSelectAddsIngredients() {
        val full = seed()
        compose.waitUntil(5000) { compose.onAllNodesWithText(full.recipe.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("多选").performClick()
        compose.onNodeWithText(full.recipe.name).performClick()
        compose.onNodeWithText("将选中的 1 道菜加入购物清单").performClick()
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(5000) { runBlocking { container.db.dao().shopping().size == 2 } }
        compose.onNodeWithContentDescription("多选").assertExists()
    }
    @Test fun notificationShowsLargeLiveCountdownAndFrozenPause() {
        val full = seed()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("appops set cn.myrecipe.app SCHEDULE_EXACT_ALARM allow", "pm grant cn.myrecipe.app android.permission.POST_NOTIFICATIONS").forEach { command -> automation.executeShellCommand(command).use { ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() } }
        val manager = compose.activity.getSystemService(android.app.NotificationManager::class.java)
        fun current(status: String): android.app.Notification {
            val deadline = android.os.SystemClock.elapsedRealtime() + 5000
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                manager.activeNotifications.firstOrNull { it.id == 41 && it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() == status }?.let { return it.notification }
                Thread.sleep(50)
            }
            error("Notification not published: $status")
        }
        runBlocking { container.timer.start(full.recipe, full.steps.first(), 120) }
        val active = current("计时中")
        val firstPost = manager.activeNotifications.single { it.id == 41 }.postTime
        runBlocking { container.timer.reconcile() }
        assertEquals(firstPost, manager.activeNotifications.single { it.id == 41 }.postTime)
        assertEquals(full.recipe.name, active.extras.getCharSequence(android.app.Notification.EXTRA_TITLE))
        assertTrue(active.actions.isNullOrEmpty())
        val root = active.contentView.apply(compose.activity, null)
        val countdown = root.findViewById<android.widget.Chronometer>(R.id.timer_countdown)
        assertTrue(countdown.isCountDown)
        assertEquals(runBlocking { container.db.dao().timer()!!.deadlineElapsed }, countdown.base)
        assertTrue(countdown.textSize / compose.activity.resources.displayMetrics.scaledDensity >= 28)
        assertEquals("计时中", root.findViewById<android.widget.TextView>(R.id.timer_status).text.toString())
        runBlocking { container.timer.pause() }
        val paused = current("计时已暂停").contentView.apply(compose.activity, null)
        assertEquals(android.view.View.GONE, paused.findViewById<android.view.View>(R.id.timer_countdown).visibility)
        assertTrue(paused.findViewById<android.widget.TextView>(R.id.timer_remaining).text.isNotBlank())
        runBlocking { container.timer.resume() }
        current("计时中")
        runBlocking { container.timer.change(180) }
        val timer = runBlocking { container.db.dao().timer()!! }
        compose.waitUntil(5000) { manager.activeNotifications.firstOrNull { it.id == 41 }?.notification?.contentView?.apply(compose.activity, null)?.findViewById<android.widget.Chronometer>(R.id.timer_countdown)?.base == timer.deadlineElapsed }
        runBlocking { container.timer.cancel() }
        compose.waitUntil(5000) { manager.activeNotifications.none { it.id == 41 } }
    }
    @Test fun createCategoryInEditorAndPersistPreparation() {
        compose.onNodeWithTag("newRecipe").performClick()
        compose.onNodeWithText("菜名（必填）").performTextInput("备菜测试")
        compose.onNodeWithText("分类：未分类").performClick()
        compose.onNodeWithText("＋ 新建分类").performClick()
        compose.onAllNodesWithText("保存").filterToOne(hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("分类名称不能为空").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("分类名称不能为空").assertIsDisplayed()
        compose.onNodeWithText("分类名称").performTextInput("家常菜")
        compose.onAllNodesWithText("保存").filterToOne(hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5000) { runBlocking { container.db.dao().categories().any { it.name == "家常菜" } } }
        compose.onNodeWithText("分类：家常菜").assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("备菜说明"))
        compose.onNodeWithText("备菜说明").performTextInput("番茄切块，鸡蛋打散。")
        compose.onNodeWithText("保存菜谱", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { runBlocking { container.db.dao().recipes().any { it.recipe.name == "备菜测试" } } }
        val stored = runBlocking { container.db.dao().recipes().single() }
        assertEquals("番茄切块，鸡蛋打散。", stored.recipe.preparation)
        assertEquals("家常菜", runBlocking { container.db.dao().categories().single().name })
        assertNotNull(stored.recipe.categoryId)
        compose.onNodeWithText("备菜测试").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("备菜"))
        compose.onNodeWithText("番茄切块，鸡蛋打散。").assertIsDisplayed()
        compose.onNodeWithContentDescription("菜谱封面").assertDoesNotExist()
        compose.onNodeWithText("☆ 收藏").performClick()
        compose.waitUntil(5000) { runBlocking { container.db.dao().recipes().single().recipe.favorite } }
    }
    @Test fun managementCreatesCategoryAndShowsDuplicateError() {
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("分类与标签管理").performClick()
        compose.onNodeWithText("＋ 新建分类").performClick()
        compose.onNodeWithText("分类名称").performTextInput("蔬菜")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5000) { runBlocking { container.db.dao().categories().any { it.name == "蔬菜" } } }
        compose.onNodeWithText("＋ 新建分类").performClick()
        compose.onNodeWithText("分类名称").performTextInput("蔬菜")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("该层级已有同名分类").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("该层级已有同名分类").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertEquals(1, runBlocking { container.db.dao().categories().size })
    }
    @Test fun actualAlarmReceiverFinishesWhileActivityBackgrounded() {
        val full = seed(); val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("appops set cn.myrecipe.app SCHEDULE_EXACT_ALARM allow", "pm grant cn.myrecipe.app android.permission.POST_NOTIFICATIONS").forEach { command -> automation.executeShellCommand(command).use { ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() } }
        runBlocking { container.timer.start(full.recipe, full.steps.first(), 2) }
        compose.activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = System.currentTimeMillis() + 10000
        while (System.currentTimeMillis() < deadline && runBlocking { container.db.dao().timer()?.state } != "FINISHED") Thread.sleep(200)
        assertEquals("FINISHED", runBlocking { container.db.dao().timer()?.state })
        val manager = compose.activity.getSystemService(android.app.NotificationManager::class.java)
        compose.waitUntil(5000) { manager.activeNotifications.any { it.id == 42 } }
        val finished = manager.activeNotifications.single { it.id == 42 }.notification
        assertTrue(finished.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(finished.flags and android.app.Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals("确认", finished.actions.single().title.toString())
        assertNotNull(finished.contentIntent)
        finished.actions.single().actionIntent.send()
        compose.waitUntil(5000) { runBlocking { container.db.dao().timer() == null } && manager.activeNotifications.none { it.id == 42 } }
        runBlocking { container.timer.start(full.recipe, full.steps.first(), 60) }
        finished.actions.single().actionIntent.send()
        Thread.sleep(300)
        assertEquals("RUNNING", runBlocking { container.db.dao().timer()?.state })
    }

    @Test fun editorAddedIngredientIsVisibleAndFocused() {
        compose.onNodeWithTag("newRecipe").performClick()
        compose.onNodeWithText("菜名（必填）").performTextInput("自动聚焦测试")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("＋ 添加食材"))
        compose.onNodeWithText("＋ 添加食材").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("食材名称").fetchSemanticsNodes().any { it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false } } }
        compose.onNodeWithText("食材名称").assertIsFocused().performTextInput("蒜")
        compose.onNodeWithText("保存菜谱", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { runBlocking { container.db.dao().recipes().any { it.recipe.name == "自动聚焦测试" } } }
        assertEquals("蒜", runBlocking { container.db.dao().recipes().single().ingredients.single().name })
    }

    @Test fun timeWheelsKeepHoursMinutesSecondsAndValidateBoundaries() {
        val full = seed()
        compose.waitUntil(5000) { compose.onAllNodesWithText(full.recipe.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(full.recipe.name).performClick(); compose.onNodeWithText("开始做饭").performClick()
        compose.onNodeWithText("修改").performClick()
        fun select(label: String, value: Int) = compose.onNodeWithTag("timer-$label").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(value.toFloat()) }
        select("小时", 1); select("分钟", 2); select("秒", 3)
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("参考计时 1:02:03").assertExists()
        compose.onNodeWithText("修改").performClick()
        compose.onNodeWithTag("timer-小时").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "1"))
        select("小时", 0); select("分钟", 0); select("秒", 0)
        compose.onNodeWithText("确定").assertIsNotEnabled()
        select("小时", 168)
        compose.onNodeWithText("确定").assertIsEnabled().performClick()
        compose.onNodeWithText("参考计时 168:00:00").assertExists()
    }

    @Test fun multiRecipeShoppingKeepsEverySourceAfterDeduplication() {
        val first = seed()
        val second = Recipe(name = "番茄汤")
        runBlocking { container.repository.save(RecipeFull(second, listOf(RecipeIngredient(recipeId = second.id, name = "番茄")), emptyList(), emptyList())) }
        compose.waitUntil(5000) { compose.onAllNodesWithText(second.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("多选").performClick()
        compose.onNodeWithText(first.recipe.name).performClick(); compose.onNodeWithText(second.name).performClick()
        compose.onNodeWithText("将选中的 2 道菜加入购物清单").performClick()
        compose.onNodeWithText("番茄", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(5000) { runBlocking { container.db.dao().shopping().size == 2 } }
        val item = runBlocking { container.db.dao().shopping().single { it.name == "番茄" } }
        assertEquals(setOf(first.recipe.name, second.name), item.sourceRecipeNames.toSet())
        compose.onNodeWithTag("nav-shopping").performClick()
        compose.onNodeWithText("来自：${item.sourceRecipeNames.joinToString("、")}").assertIsDisplayed()
    }
    @Test fun seasoningEditorPersistsAndAppearsInDetailAndKitchen() {
        compose.onNodeWithTag("newRecipe").performClick(); compose.onNodeWithText("菜名（必填）").performTextInput("调料测试")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("＋ 添加调料")); compose.onNodeWithText("＋ 添加调料").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("调料名称").fetchSemanticsNodes().any { it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false } } }
        compose.onNodeWithText("调料名称").performTextInput("蚝油")
        compose.onNodeWithText("数量").performTextInput("2"); compose.onNodeWithText("单位（可自定义）").performTextInput("勺")
        compose.onNodeWithText("保存菜谱", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { runBlocking { container.db.dao().recipes().any { it.recipe.name == "调料测试" } } }
        val full = runBlocking { container.db.dao().recipes().single() }; assertTrue(full.foodIngredients.isEmpty()); assertEquals("蚝油", full.seasonings.single().name)
        compose.onNodeWithText("调料测试").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("调料备注")); compose.onNodeWithText("蚝油").assertIsDisplayed()
        compose.onNodeWithText("开始做饭").performClick(); compose.onNodeWithText("查看食材").performClick()
        compose.onNodeWithText("调料备注").assertIsDisplayed(); compose.onNodeWithText("蚝油").assertIsDisplayed(); compose.onNodeWithText("2勺").assertIsDisplayed()
    }
    @Test fun longPressDragPersistsRecipeOrderAcrossRecreation() {
        val recipes = (1..3).map { RecipeFull(Recipe(name = "拖动菜$it"), emptyList(), emptyList(), emptyList()) }
        runBlocking { recipes.forEach { container.repository.save(it) }; container.repository.reorderRecipes(recipes.map { it.recipe.id }) }
        val first = recipes[0].recipe.id; val last = recipes[2].recipe.id
        compose.waitUntil(5000) { compose.onAllNodesWithTag("recipe-$last").fetchSemanticsNodes().isNotEmpty() }
        val list = compose.onNodeWithTag("recipeList").fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithTag("recipe-$first").fetchSemanticsNode().boundsInRoot.center - list.topLeft
        val end = compose.onNodeWithTag("recipe-$last").fetchSemanticsNode().boundsInRoot.center - list.topLeft
        compose.onNodeWithTag("recipeList").performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("recipeList").performTouchInput {
            for (step in 1..20) moveTo(androidx.compose.ui.geometry.Offset(start.x, start.y + (end.y + 15 - start.y) * step / 20), delayMillis = 20)
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("recipeList").performTouchInput { up() }
        compose.waitUntil(5000) { runBlocking { filterRecipes(AppSnapshot(recipes = container.db.dao().recipes()), "", "全部", null).last().recipe.id == first } }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        val top = compose.onNodeWithTag("recipe-${recipes[1].recipe.id}").fetchSemanticsNode().boundsInRoot.top
        assertTrue(top < compose.onNodeWithTag("recipe-$first").fetchSemanticsNode().boundsInRoot.top)
    }


    @Test fun detailAndShoppingPickerBothRecordRecipeSources() {
        val first = seed(); val second = Recipe(name = "番茄汤")
        runBlocking { container.repository.save(RecipeFull(second, listOf(RecipeIngredient(recipeId = second.id, name = "番茄")), emptyList(), emptyList())) }
        compose.waitUntil(5000) { compose.onAllNodesWithText(first.recipe.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(first.recipe.name).performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("加入购物清单"))
        compose.onNodeWithText("加入购物清单").performClick(); compose.onNodeWithText("确定").performClick()
        compose.waitUntil(5000) { runBlocking { container.db.dao().shopping().size == 2 } }
        assertEquals(listOf(first.recipe.name), runBlocking { container.db.dao().shopping().single { it.name == "番茄" }.sourceRecipeNames })
        compose.onNodeWithText("返回").performClick(); compose.onNodeWithTag("nav-shopping").performClick()
        compose.onNodeWithText("从菜谱添加").performClick(); compose.onAllNodesWithText(second.name).filterToOne(hasAnyAncestor(isDialog())).performClick(); compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(5000) { runBlocking { container.db.dao().shopping().single { it.name == "番茄" }.sourceRecipeNames.size == 2 } }
        assertEquals(2, runBlocking { container.db.dao().shopping().size })
        compose.onNodeWithText("来自：${first.recipe.name}、${second.name}").assertIsDisplayed()
    }
}
