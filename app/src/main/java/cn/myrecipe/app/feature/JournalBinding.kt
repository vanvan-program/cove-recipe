package cn.myrecipe.app.feature

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private val thread = Color(0xffd8b580)
private val rule = Color(0xffc7ae85)
private val ink = Color(0xff51331f)
private val pageShape = RoundedCornerShape(4.dp, 8.dp, 8.dp, 4.dp)

private fun grain(leather: Boolean): Bitmap {
    val random = Random(if (leather) 421 else 817)
    val tile = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(160 * 160) {
        val light = random.nextBoolean()
        android.graphics.Color.argb(random.nextInt(if (leather) 46 else 24),
            if (light) 255 else 74, if (light) 240 else 44, if (light) 201 else 17)
    }
    tile.setPixels(pixels, 0, 160, 0, 0, 160, 160)
    val canvas = android.graphics.Canvas(tile)
    val pen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    repeat(if (leather) 640 else 190) {
        val x = random.nextInt(160).toFloat()
        val y = random.nextInt(160).toFloat()
        pen.color = if (leather) 0x35553622 else 0x247d633e
        pen.strokeWidth = if (leather) 1.6f else 0.8f
        canvas.drawLine(x, y, x + random.nextInt(2, 9), y + random.nextInt(-3, 4), pen)
    }
    return tile
}

private val hideGrain by lazy { grain(true) }
private val paperGrain by lazy { grain(false) }
private fun Modifier.material(leather: Boolean) = drawWithCache {
    val colors = if (leather) listOf(Color(0xff40281d), Color(0xff825237), Color(0xff5d3827), Color(0xff42291f))
        else listOf(Color(0xffe6d5b6), Color(0xfffbf2de), Color(0xfffff8e7), Color(0xfff4e5ca))
    val base = if (leather) Brush.linearGradient(colors) else Brush.horizontalGradient(
        0f to colors[0], 0.03f to colors[1], 0.58f to colors[2], 1f to colors[3])
    val texture = android.graphics.Paint().apply {
        shader = BitmapShader(if (leather) hideGrain else paperGrain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    onDrawBehind {
        drawRect(base)
        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, texture) }
    }
}

private fun Modifier.seam() = drawWithCache {
    val inset = 4.dp.toPx()
    val radius = CornerRadius(7.dp.toPx())
    val stitch = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
    onDrawBehind {
        val bounds = Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f))
        drawRoundRect(Color(0xff39241b), Offset(inset, inset + 1.dp.toPx()), bounds, radius, style = stitch)
        drawRoundRect(thread, Offset(inset, inset), bounds, radius, style = stitch)
    }
}

@Composable private fun RiceSprig() {
    Canvas(Modifier.size(36.dp, 34.dp)) {
        val unit = size.minDimension / 40f
        fun p(x: Float, y: Float) = Offset(unit * x, unit * y)
        val stem = Path().apply {
            moveTo(unit * 6, unit * 37)
            cubicTo(unit * 15, unit * 29, unit * 18, unit * 6, unit * 34, unit * 6)
        }
        drawPath(stem, thread, style = Stroke(width = 1.4f * unit, cap = StrokeCap.Round))
        fun grain(x: Float, y: Float, dx: Float, dy: Float) {
            val path = Path().apply {
                moveTo(x * unit, y * unit)
                quadraticTo((x + dx * 0.65f - dy * 0.31f) * unit, (y + dy * 0.65f + dx * 0.31f) * unit, (x + dx) * unit, (y + dy) * unit)
                quadraticTo((x + dx * 0.48f + dy * 0.31f) * unit, (y + dy * 0.48f - dx * 0.31f) * unit, x * unit, y * unit)
                close()
            }
            drawPath(path, Color(0xffe9c782))
            drawLine(thread, p(x + dx, y + dy), p(x + dx * 1.22f, y + dy * 1.22f), unit * 0.65f)
        }
        grain(12f, 29f, -5f, -10f)
        grain(15f, 24f, 9f, -3f)
        grain(17f, 19f, -3f, -10f)
        grain(20f, 15f, 9f, -1f)
        grain(23f, 12f, 0f, -9f)
        grain(27f, 9f, 8f, 3f)
        grain(31f, 7f, 7f, 0f)
    }
}

@Composable internal fun JournalBook(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().material(true).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Column(Modifier.fillMaxSize().seam().padding(start = 11.dp, end = 11.dp, bottom = 11.dp, top = 5.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 7.dp, top = 2.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RiceSprig()
                Text("Cove Recipe", color = Color(0xffffe5b5), fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic, style = MaterialTheme.typography.titleLarge)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                listOf(Color(0xffb29872), Color(0xffe5d4b1), Color(0xffc3aa83)).forEachIndexed { index, color ->
                    Box(Modifier.matchParentSize().padding(start = 4.dp, end = (index * 2).dp, top = (4 - index).dp, bottom = index.dp)
                        .clip(pageShape).background(color))
                }
                Box(Modifier.matchParentSize().padding(end = 7.dp, bottom = 3.dp).clip(pageShape).material(false)) {
                    content()
                }
            }
        }
    }
}

@Composable internal fun JournalTabs(selected: Int, position: Float, select: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(Color(0xaaf0dfc0))) {
        val width = maxWidth / 3
        HorizontalDivider(color = rule)
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            listOf("菜谱" to "recipes", "购物清单" to "shopping", "设置" to "settings").forEachIndexed { i, (label, tag) ->
                Box(Modifier.weight(1f).heightIn(min = 48.dp).testTag("nav-$tag")
                    .selectable(selected = selected == i, role = Role.Tab, onClick = { select(i) }),
                    contentAlignment = Alignment.Center) {
                    if (i > 0) VerticalDivider(Modifier.align(Alignment.CenterStart).height(17.dp), color = rule)
                    Text(label, color = if (i == selected) ink else Color(0xff705d46), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Box(Modifier.offset(x = width * position).width(width).height(3.dp).background(ink))
    }
}
