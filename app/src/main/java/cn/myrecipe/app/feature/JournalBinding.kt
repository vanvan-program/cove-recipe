package cn.myrecipe.app.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp

@Composable internal fun JournalBinding() {
    Box(Modifier.fillMaxWidth().background(Color(0xff6e442e)).drawWithCache {
        val stitching = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
        onDrawBehind {
            drawLine(Color(0xffb6936d), Offset(0f, size.height - 4.dp.toPx()),
                Offset(size.width, size.height - 4.dp.toPx()), 1.dp.toPx(), pathEffect = stitching)
        }
    }.padding(horizontal = 20.dp, vertical = 9.dp)) {
        Text("Cove Recipe", color = Color(0xfff2ddba), fontStyle = FontStyle.Italic,
            fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineSmall)
    }
}
