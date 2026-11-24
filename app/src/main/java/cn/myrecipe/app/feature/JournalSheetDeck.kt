package cn.myrecipe.app.feature

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.scrollToIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SheetCount = 3

@Composable
internal fun rememberJournalSheets(): JournalSheets {
    val scope = rememberCoroutineScope()
    return rememberSaveable(saver = Saver(
        save = { sheets: JournalSheets -> sheets.selected },
        restore = { page -> JournalSheets(page, scope) }
    )) { JournalSheets(0, scope) }
}

@Stable
internal class JournalSheets(initial: Int, private val scope: CoroutineScope) {
    var selected by mutableIntStateOf(initial)
        private set
    var from by mutableIntStateOf(initial)
        private set
    var to by mutableIntStateOf(initial)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    private var animation: Job? = null
    private var dragging = false
    private var distance = 0f

    fun snapTo(page: Int) {
        animation?.cancel()
        dragging = false
        selected = page
        finishAt(page)
    }

    private fun finishAt(page: Int) {
        from = page
        to = page
        progress = 0f
    }

    private suspend fun settle(endpoint: Float) {
        val remaining = abs(endpoint - progress)
        if (remaining > 0.001f) {
            animate(progress, endpoint, animationSpec = tween(
                durationMillis = (340 * remaining).roundToInt().coerceAtLeast(75),
                easing = FastOutSlowInEasing
            )) { value, _ -> progress = value }
        }
        finishAt(if (endpoint == 1f) to else from)
    }

    fun select(page: Int) {
        if (page !in 0 until SheetCount) return
        if (animation?.isActive == true && selected == page && !dragging) return
        if (!ValueAnimator.areAnimatorsEnabled()) {
            snapTo(page)
            return
        }
        animation?.cancel()
        dragging = false
        selected = page
        animation = scope.launch {
            if (from != to) {
                // Reverse in place when the user taps back. A third tab finishes
                // the nearer end of the interrupted turn before starting its own.
                val forward = page == to || (page != from && progress >= 0.5f)
                settle(if (forward) 1f else 0f)
            }
            if (page != from) {
                to = page
                settle(1f)
            }
        }
    }

    fun startDrag() {
        dragging = animation?.isActive != true
        distance = 0f
    }

    fun dragBy(fraction: Float) {
        if (!dragging) return
        val min = if (from < SheetCount - 1) -1f else 0f
        val max = if (from > 0) 1f else 0f
        distance = (distance + fraction).coerceIn(min, max)
        to = when {
            distance < 0f -> from + 1
            distance > 0f -> from - 1
            else -> from
        }
        progress = abs(distance)
    }

    fun endDrag(cancelled: Boolean) {
        if (dragging) select(if (!cancelled && progress > 0.25f) to else from)
    }
}

@Composable
internal fun JournalSheetDeck(sheets: JournalSheets, content: @Composable (Int) -> Unit) {
    val focus = LocalFocusManager.current
    LaunchedEffect(sheets.selected) { focus.clearFocus() }
    val gesture = Modifier.pointerInput(sheets) {
        detectHorizontalDragGestures(
            onDragStart = { sheets.startDrag() },
            onDragEnd = { sheets.endDrag(cancelled = false) },
            onDragCancel = { sheets.endDrag(cancelled = true) }
        ) { change, delta ->
            change.consume()
            sheets.dragBy(delta / size.width.coerceAtLeast(1))
        }
    }
    val accessibility = Modifier.semantics {
        horizontalScrollAxisRange = ScrollAxisRange({ sheets.selected.toFloat() }, { (SheetCount - 1).toFloat() })
        scrollBy { x, _ ->
            val page = sheets.selected + x.compareTo(0f)
            (x != 0f && page in 0 until SheetCount).also { if (it) sheets.select(page) }
        }
        scrollToIndex { page -> (page in 0 until SheetCount).also { if (it) sheets.select(page) } }
    }
    val animateDrag = ValueAnimator.areAnimatorsEnabled()
    Box(Modifier.fillMaxSize().clipToBounds().then(gesture).then(accessibility).testTag("mainPager")) {
        Layout(content = {
            repeat(SheetCount) { index ->
                val semantics = if (index == sheets.selected) Modifier else Modifier.clearAndSetSemantics {}
                Box(Modifier.fillMaxSize().then(semantics).graphicsLayer {
                    // The lower-numbered sheet is always on top of the stack.
                    // Forward pulls it off the notebook; back lays it down again.
                    val moving = animateDrag && sheets.from != sheets.to && index == minOf(sheets.from, sheets.to)
                    val travel = when {
                        !moving -> 0f
                        sheets.from < sheets.to -> sheets.progress
                        else -> 1f - sheets.progress
                    }
                    transformOrigin = TransformOrigin(0.42f, 0.62f)
                    translationX = -size.width * 1.12f * travel
                    translationY = -6.dp.toPx() * travel
                    rotationZ = -3.4f * travel
                    shadowElevation = if (moving) 7.dp.toPx() * (4f * travel).coerceAtMost(1f) else 0f
                }.journalPaper()) { content(index) }
            }
        }, modifier = Modifier.fillMaxSize()) { children, constraints ->
            // Unplaced pages retain their composition, including query text,
            // list scroll, selection, and shopping controls, without receiving hits.
            val from = sheets.from
            val to = if (animateDrag) sheets.to else from
            val stationary = maxOf(from, to)
            val surface = minOf(from, to)
            val base = children[stationary].measure(constraints)
            val top = if (surface != stationary) children[surface].measure(constraints) else null
            layout(constraints.maxWidth, constraints.maxHeight) {
                base.place(0, 0)
                top?.place(0, 0, zIndex = 1f)
            }
        }
        if (sheets.from != sheets.to) {
            // Do not activate a button through the sheet as it crosses over it.
            Box(Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            })
        }
    }
}
