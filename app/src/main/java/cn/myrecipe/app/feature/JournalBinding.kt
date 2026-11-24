package cn.myrecipe.app.feature

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import cn.myrecipe.app.R

private val thread = Color(0xffe8c68e)
private val rule = Color(0xffc7ae85)
internal val journalInk = Color(0xff412a1b)
internal val journalLeaf = Color(0xff47563d)
private val pageShape = RoundedCornerShape(4.dp, 8.dp, 8.dp, 4.dp)
private val LocalSheetTexture = staticCompositionLocalOf<Bitmap?> { null }

private fun Modifier.texture(bitmap: Bitmap?, leather: Boolean = false) = drawWithCache {
    val base = if (leather) Color(0xffb28255) else Color(0xfff6edd8)
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    if (bitmap != null) {
        val tileWidth = (if (leather) 260.dp else 290.dp).toPx()
        paint.shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            val scale = tileWidth / bitmap.width
            setLocalMatrix(Matrix().apply { setScale(scale, scale) })
        }
        if (leather) {
            // Re-tone the original leather grain to the honey-tan cover.
            paint.colorFilter = ColorMatrixColorFilter(floatArrayOf(
                .20f, .53f, .07f, 0f, 143f,
                .20f, .53f, .07f, 0f, 95f,
                .20f, .53f, .07f, 0f, 50f,
                0f, 0f, 0f, 1f, 0f
            ))
        } else {
            paint.alpha = 160
        }
    }
    val edge = if (leather) Brush.horizontalGradient(
        0f to Color(0x5560442e), .025f to Color(0x22f8d6a2), .043f to Color(0x44513c29),
        .065f to Color.Transparent, .94f to Color.Transparent, 1f to Color(0x505c3c26)
    ) else Brush.horizontalGradient(0f to Color(0x3387673f), .045f to Color.Transparent,
        .95f to Color.Transparent, 1f to Color(0x1887673f))
    onDrawBehind {
        drawRect(base)
        if (bitmap != null) drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
        drawRect(edge)
    }
}

@Composable internal fun Modifier.journalPaper(): Modifier = texture(LocalSheetTexture.current)

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
        drawPath(stem, journalInk, style = Stroke(width = 1.4f * unit, cap = StrokeCap.Round))
        fun grain(x: Float, y: Float, dx: Float, dy: Float) {
            val path = Path().apply {
                moveTo(x * unit, y * unit)
                quadraticTo((x + dx * 0.65f - dy * 0.31f) * unit, (y + dy * 0.65f + dx * 0.31f) * unit, (x + dx) * unit, (y + dy) * unit)
                quadraticTo((x + dx * 0.48f + dy * 0.31f) * unit, (y + dy * 0.48f - dx * 0.31f) * unit, x * unit, y * unit)
                close()
            }
            drawPath(path, Color(0xff704828))
            drawLine(journalInk, p(x + dx, y + dy), p(x + dx * 1.22f, y + dy * 1.22f), unit * 0.65f)
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
    val paper = ImageBitmap.imageResource(R.drawable.journal_paper).asAndroidBitmap()
    val leather = ImageBitmap.imageResource(R.drawable.journal_leather).asAndroidBitmap()
    CompositionLocalProvider(LocalSheetTexture provides paper) {
        Box(Modifier.fillMaxSize().texture(leather, leather = true).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Column(Modifier.fillMaxSize().seam().padding(start = 11.dp, end = 11.dp, bottom = 11.dp, top = 5.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 45.dp).padding(start = 7.dp, top = 2.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    RiceSprig()
                    Text("Cove Recipe", color = journalInk, fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.headlineSmall)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    listOf(Color(0xffb29872), Color(0xffe5d4b1), Color(0xffc3aa83)).forEachIndexed { index, color ->
                        Box(Modifier.matchParentSize().padding(start = 4.dp, end = (index * 2).dp, top = (4 - index).dp, bottom = index.dp)
                            .clip(pageShape).background(color))
                    }
                    Box(Modifier.matchParentSize().padding(end = 7.dp, bottom = 3.dp).clip(pageShape).journalPaper()) {
                        content()
                    }
                    BindingRings(Modifier.align(Alignment.CenterStart).offset(x = (-6).dp)
                        .width(14.dp).fillMaxHeight(.85f))
                }
            }
        }
    }
}

@Composable private fun BindingRings(modifier: Modifier) {
    Canvas(modifier) {
        val ringHeight = 4.dp.toPx()
        for (fraction in listOf(.12f, .5f, .88f)) {
            val y = size.height * fraction
            val eyelet = Offset(size.width - 2.dp.toPx(), y + ringHeight / 2)
            drawCircle(Color(0xff715838), 3.3.dp.toPx(), eyelet)
            val strip = Brush.verticalGradient(
                listOf(Color(0xff725b3a), Color(0xffefdbac), Color(0xffbfa575), Color(0xff796341)),
                startY = y, endY = y + ringHeight
            )
            drawRoundRect(strip, Offset(0f,y), Size(size.width,ringHeight), CornerRadius(ringHeight / 2))
        }
    }
}

@Composable internal fun JournalTabs(selected: Int, select: (Int) -> Unit) {
    val shape = RoundedCornerShape(5.dp, 6.dp, 3.dp, 3.dp)
    Column(Modifier.fillMaxWidth().background(Color(0xffeee0c5))) {
        HorizontalDivider(color = rule)
        Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("菜谱" to "recipes", "购物清单" to "shopping", "设置" to "settings").forEachIndexed { i, (label, tag) ->
                val active = i == selected
                val foreground = if (active) Color(0xfffff4df) else Color(0xff66583e)
                val fill = if (active) Modifier.background(Color(0xff805538), shape)
                    .border(1.dp, Color(0xff9a7251), shape) else Modifier
                Row(Modifier.weight(1f).heightIn(min = 48.dp).testTag("nav-$tag").clip(shape).then(fill)
                    .selectable(selected = active, role = Role.Tab, onClick = { select(i) }),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (i == 2) Icon(Icons.Outlined.Settings, null, Modifier.size(16.dp), tint = foreground)
                    else TabGlyph(i == 0, foreground)
                    Text(label, color = foreground, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable private fun TabGlyph(book: Boolean, color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val u = size.width / 16
        val line = Stroke(1.35f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (book) {
            val pages = Path().apply {
                moveTo(u, 3*u); quadraticTo(4*u, 2*u, 8*u, 4*u)
                quadraticTo(12*u, 2*u, 15*u, 3*u); lineTo(15*u, 13*u)
                quadraticTo(11*u, 12*u, 8*u, 14*u); quadraticTo(5*u,12*u,u,13*u); close()
            }
            drawPath(pages,color,style=line)
            drawLine(color,Offset(8*u,4*u),Offset(8*u,14*u),line.width)
        } else {
            drawRoundRect(color,Offset(2*u,5*u),Size(12*u,9*u),CornerRadius(u),style=line)
            drawArc(color,180f,180f,false,Offset(5*u,u),Size(6*u,8*u),style=line)
        }
    }
}
