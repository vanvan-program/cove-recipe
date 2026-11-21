package cn.myrecipe.app.core.designsystem

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WarmColors = lightColorScheme(primary = Color(0xFF70432B), onPrimary = Color.White, primaryContainer = Color(0xFFEEDABB), onPrimaryContainer = Color(0xFF3B1808), secondary = Color(0xFF795D42), secondaryContainer = Color(0xFFE4D4B9), background = Color(0xFFF8EFDC), surface = Color(0xFFF8EFDC), surfaceVariant = Color(0xFFEBDDC3), surfaceContainer = Color(0xFFF6EDE2), surfaceContainerLow = Color(0xFFF3E8D3), surfaceContainerHigh = Color(0xFFEBDDC3), surfaceContainerHighest = Color(0xFFEBDCCD), surfaceContainerLowest = Color(0xFFFFF9EC), onSurface = Color(0xFF302820), onSurfaceVariant = Color(0xFF706154), outline = Color(0xFF958679))
private val KitchenType = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp)
)
@Composable fun RecipeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WarmColors, typography = KitchenType,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp)), content = content)
}
