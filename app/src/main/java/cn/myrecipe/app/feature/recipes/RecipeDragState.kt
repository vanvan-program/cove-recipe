package cn.myrecipe.app.feature

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cn.myrecipe.app.core.database.RecipeFull
import kotlinx.coroutines.delay

@Stable
class RecipeDragState(val listState: LazyListState) {
    var ids by mutableStateOf<List<String>>(emptyList())
    var dragging by mutableStateOf<String?>(null)
    var top by mutableFloatStateOf(0f)
    var delta by mutableFloatStateOf(0f)
    var height by mutableIntStateOf(0)
    var pointerY by mutableFloatStateOf(0f)
    var original: List<String> = emptyList()
    var pinnedIds: Set<String> = emptySet()
    var save: (List<String>) -> Unit = {}
    fun stop() { dragging = null; delta = 0f }
    fun start(id: String, y: Float) {
        val info = listState.layoutInfo.visibleItemsInfo.find { it.key == id } ?: return
        original = ids; dragging = id; top = info.offset.toFloat(); height = info.size; delta = 0f
        pointerY = info.offset + listState.layoutInfo.beforeContentPadding + y
    }
    fun moveOverTarget() {
        val id = dragging ?: return
        val center = top + delta + height / 2f
        val from = ids.indexOf(id)
        if (from < 0) return
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            val to = ids.indexOf(info.key)
            to >= 0 && to != from && ((info.key in pinnedIds) == (id in pinnedIds)) &&
                center in info.offset.toFloat()..(info.offset + info.size).toFloat() &&
                (if (to > from) center > info.offset + info.size / 2f else center < info.offset + info.size / 2f)
        } ?: return
        val firstIndex = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        ids = ids.toMutableList().apply { add(ids.indexOf(target.key), removeAt(from)) }
        // Prevent keyed-list anchoring from following the old first card after a swap.
        listState.requestScrollToItem(firstIndex, firstOffset)
    }
    fun finish() { if (dragging != null && ids != original) save(ids); stop() }
    fun cancel() { if (dragging != null) { ids = original; stop() } }
}

@Composable
fun rememberRecipeDragState(recipes: List<RecipeFull>, enabled: Boolean, onOrder: (List<String>) -> Unit): Pair<RecipeDragState, Modifier> {
    val state = remember { RecipeDragState(LazyListState()) }
    state.save = onOrder
    state.pinnedIds = recipes.filter { it.recipe.pinned }.map { it.recipe.id }.toSet()
    val edge = with(LocalDensity.current) { 88.dp.toPx() }
    LaunchedEffect(recipes, enabled) {
        if (!enabled || state.dragging !in recipes.map { it.recipe.id }) { state.stop(); state.ids = recipes.map { it.recipe.id } }
    }
    LaunchedEffect(state.dragging) {
        while (state.dragging != null) {
            val layout = state.listState.layoutInfo
            val distance = when {
                state.pointerY < layout.viewportStartOffset + edge -> (state.pointerY - layout.viewportStartOffset - edge).coerceAtLeast(-edge)
                state.pointerY > layout.viewportEndOffset - edge -> (state.pointerY - layout.viewportEndOffset + edge).coerceAtMost(edge)
                else -> 0f
            }
            val canScroll = if (distance < 0) state.listState.canScrollBackward else state.listState.canScrollForward
            if (distance != 0f && state.delta != 0f && canScroll) { state.listState.scrollBy(distance * 0.22f); state.moveOverTarget() }
            delay(16)
        }
    }
    return state to Modifier
}

@Composable
fun Modifier.recipeDragItem(state: RecipeDragState, id: String, recipes: List<RecipeFull>, onOrder: (List<String>) -> Unit): Modifier {
    val haptic = LocalHapticFeedback.current
    val index = state.ids.indexOf(id)
    val pinned = recipes.find { it.recipe.id == id }?.recipe?.pinned
    val actions = listOf(-1 to "上移菜谱", 1 to "下移菜谱").mapNotNull { (offset, label) ->
        val other = state.ids.getOrNull(index + offset)
        if (other == null || recipes.find { it.recipe.id == other }?.recipe?.pinned != pinned) null else CustomAccessibilityAction(label) {
            state.ids = state.ids.toMutableList().apply { add(index + offset, removeAt(index)) }
            onOrder(state.ids); true
        }
    }
    return this.zIndex(if (state.dragging == id) 1f else 0f).graphicsLayer {
        if (state.dragging == id) {
            val current = state.listState.layoutInfo.visibleItemsInfo.find { it.key == id }
            translationY = state.top + state.delta - (current?.offset ?: state.top.toInt())
            scaleX = 1.025f; scaleY = 1.025f; shadowElevation = 12.dp.toPx()
        }
    }.pointerInput(state, id) {
        // Handle the card gesture before the parent LazyColumn can consume vertical movement.
        detectDragGesturesAfterLongPress(
            onDragStart = { position -> state.start(id, position.y); haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
            onDrag = { change, amount ->
                if (state.dragging == id) { change.consume(); state.delta += amount.y; state.pointerY += amount.y; state.moveOverTarget() }
            },
            onDragEnd = { if (state.dragging == id) state.finish() },
            onDragCancel = { if (state.dragging == id) state.cancel() }
        )
    }.semantics { customActions = actions }
}
