package cn.myrecipe.app.feature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.myrecipe.app.R

private val stickerEdge = GenericShape { area, _ ->
    val w = area.width
    val h = area.height
    moveTo(.025f*w, .09f*h); lineTo(.28f*w, .05f*h); lineTo(.45f*w, .08f*h)
    lineTo(.70f*w, .03f*h); lineTo(.96f*w,.08f*h); lineTo(w,.31f*h)
    lineTo(.98f*w,.55f*h); lineTo(w,.72f*h); lineTo(.95f*w,.94f*h)
    lineTo(.68f*w,.96f*h); lineTo(.45f*w,.93f*h); lineTo(.24f*w,.98f*h)
    lineTo(.02f*w,.93f*h); lineTo(0f,.64f*h); lineTo(.02f*w,.44f*h); close()
}

@Composable internal fun StickerAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Row(Modifier.rotate(-2f).shadow(2.dp, stickerEdge).background(Color(0xfffff5dd), stickerEdge)
            .padding(3.dp).background(Brush.verticalGradient(listOf(Color(0xff994e35), Color(0xff84402d))), stickerEdge)
            .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp), tint = Color(0xfffff7e4))
            Text(label, color = Color(0xfffff7e4), style = MaterialTheme.typography.labelLarge)
        }
        Canvas(Modifier.align(Alignment.BottomEnd).offset(x = (-5).dp, y = (-6).dp).size(7.dp)) {
            val corner = Path().apply {
                moveTo(0f, size.height); lineTo(size.width, 0f); lineTo(size.width*.8f,size.height*.86f); close()
            }
            drawPath(corner, Brush.linearGradient(listOf(Color(0xfff9e7ba),Color(0xffae8963))))
        }
    }
}

@Composable private fun WashiTape(modifier: Modifier) {
    Spacer(modifier.background(Color(0xc9dbbf85)).drawBehind {
        val gap = 4.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(Color(0x44fff3cf), Offset(x, size.height), Offset((x+gap).coerceAtMost(size.width), 0f), 1.dp.toPx())
            x += gap
        }
    })
}

@Composable internal fun MarketPrintAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(vertical = 4.dp).clickable(role = Role.Button, onClick = onClick)) {
        val frame = RoundedCornerShape(2.dp)
        Row(Modifier.rotate(-1.3f).shadow(2.dp, frame).background(Color(0xfffff9e9), frame)
            .border(1.dp, Color(0xffc6b799), frame).padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.journal_market), null, Modifier.size(55.dp, 44.dp), contentScale = ContentScale.Crop)
            Text("从菜谱添加", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = journalInk)
            NoteArrow()
        }
        WashiTape(Modifier.align(Alignment.TopStart).offset(x = (-2).dp).rotate(-16f).size(28.dp, 9.dp))
    }
}

internal enum class NotePaper(val tint: Color) {
    Wheat(Color(0xffe9cd88)), Apricot(Color(0xffefd8b8)),
    Sage(Color(0xffcbd3ae)), Rose(Color(0xffe8c5b7))
}

@Composable internal fun StickyNote(title: String, paper: NotePaper, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(2.dp, 1.dp, 7.dp, 1.dp)
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().shadow(3.dp, shape).background(
            Brush.verticalGradient(listOf(paper.tint, paper.tint.copy(alpha = .91f))), shape)
            .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Color(0xff413520), style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            content()
        }
        when (paper) {
            NotePaper.Wheat -> WashiTape(Modifier.align(Alignment.TopEnd).offset((-14).dp, (-3).dp)
                .rotate(7f).size(42.dp, 12.dp))
            NotePaper.Rose -> WashiTape(Modifier.align(Alignment.TopStart).offset(14.dp, (-5).dp)
                .rotate(-7f).size(39.dp, 11.dp))
            NotePaper.Apricot -> Canvas(Modifier.align(Alignment.TopEnd).offset((-14).dp, (-3).dp)
                .size(9.dp, 23.dp).rotate(9f)) {
                drawRoundRect(Color(0xff998865),Offset(1f,1f),Size(size.width-2,size.height-2),
                    CornerRadius(size.width/2), style = Stroke(1.6.dp.toPx()))
            }
            NotePaper.Sage -> Canvas(Modifier.align(Alignment.TopEnd).padding(10.dp).size(9.dp)) {
                drawCircle(Brush.radialGradient(listOf(Color(0xfff2dfac),Color(0xffb49c64),Color(0xff766342)),
                    center=Offset(size.width*.35f,size.height*.3f),radius=size.width*.7f))
            }
        }
    }
}

@Composable private fun NoteArrow() {
    Canvas(Modifier.size(12.dp)) {
        val stroke = 1.4.dp.toPx()
        val middle = Offset(size.width*.7f, size.height*.5f)
        drawLine(journalInk, Offset(size.width*.35f,size.height*.15f), middle, stroke)
        drawLine(journalInk, middle, Offset(size.width*.35f,size.height*.85f), stroke)
    }
}

@Composable internal fun NoteAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    HorizontalDivider(color = Color(0x66867551))
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = journalInk.copy(alpha = if (enabled) 1f else .5f),
            style = MaterialTheme.typography.labelLarge)
        NoteArrow()
    }
}
