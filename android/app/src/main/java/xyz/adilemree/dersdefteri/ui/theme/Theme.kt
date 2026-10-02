package xyz.adilemree.dersdefteri.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// Tasarım sistemi: iOS uygulamasındaki Theme ile aynı renkler. Kara tahta
// yeşili ana marka rengi, krem kağıt zemin; koyu modda gece defteri.

@Immutable
data class AppColors(
    /// Kara tahta yeşili — dolgular için, üstüne beyaz yazı gelir
    val board: Color,
    /// Yazı, ikon, bağlantı ve çerçevelerde kullanılan yeşil
    val accent: Color,
    val boardDark: Color,
    /// Kağıt/krem zemin
    val paper: Color,
    /// Kart yüzeyi
    val card: Color,
    val ink: Color,
    val inkSoft: Color,
    val amber: Color,
    val red: Color,
    val blue: Color,
    val green: Color,
    val line: Color,
    val isDark: Boolean,
)

val LightColors = AppColors(
    board = Color(0xFF1E4B39),
    accent = Color(0xFF1E4B39),
    boardDark = Color(0xFF143528),
    paper = Color(0xFFF7F2E7),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF26303E),
    inkSoft = Color(0xFF77808D),
    amber = Color(0xFFDF9E3B),
    red = Color(0xFFC3503E),
    blue = Color(0xFF3C66AE),
    green = Color(0xFF3E8E5F),
    line = Color(0xFF26303E).copy(alpha = 0.08f),
    isDark = false,
)

val DarkColors = AppColors(
    board = Color(0xFF2F6C51),
    accent = Color(0xFF7FC79F),
    boardDark = Color(0xFF224E3B),
    paper = Color(0xFF1A1915),
    card = Color(0xFF262420),
    ink = Color(0xFFEAE6DB),
    inkSoft = Color(0xFF9C988D),
    amber = Color(0xFFE6AF58),
    red = Color(0xFFDE705F),
    blue = Color(0xFF7397D0),
    green = Color(0xFF5CB283),
    line = Color(0xFFEAE6DB).copy(alpha = 0.10f),
    isDark = true,
)

/// Öğrenci renk paleti (iOS ile aynı sıra; sunucuda indeks olarak tutulur)
val StudentPalette = listOf(
    Color(0xFF3C66AE), Color(0xFFC3503E), Color(0xFF3E8E5F), Color(0xFFDF9E3B),
    Color(0xFF7B5CB8), Color(0xFF2E8F9E), Color(0xFFC85C8E), Color(0xFF8A6D3B),
)

fun studentColor(index: Int): Color = StudentPalette[abs(index) % StudentPalette.size]

/// Ödev panosundaki yapışkan notların renkleri (açık, koyu)
val StickyNoteColors = listOf(
    Color(0xFFFDF0C2) to Color(0xFF46402A),
    Color(0xFFE3F2DC) to Color(0xFF2E4433),
    Color(0xFFE4EDFB) to Color(0xFF2C3B50),
    Color(0xFFFBE5DE) to Color(0xFF4A322B),
)

val WhatsAppGreen = Color(0xFF128C7E)
val SettingsGray = Color(0xFF8E8E93)

private val LocalAppColors = staticCompositionLocalOf { LightColors }

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

// MARK: - Yazı stilleri (iOS metin boyutları)

object Type {
    val Serif = FontFamily.Serif

    private fun style(size: TextUnit, weight: FontWeight = FontWeight.Normal, serif: Boolean = false) =
        TextStyle(
            fontSize = size,
            fontWeight = weight,
            fontFamily = if (serif) Serif else FontFamily.Default,
            lineHeight = size * 1.25f,
        )

    val largeTitle = style(34.sp, FontWeight.Bold, serif = true)
    val title = style(28.sp, FontWeight.Bold, serif = true)
    val title2 = style(22.sp, FontWeight.Bold, serif = true)
    val title3 = style(20.sp, FontWeight.Bold, serif = true)
    val headline = style(17.sp, FontWeight.SemiBold)
    val body = style(17.sp)
    val callout = style(16.sp)
    val subheadline = style(15.sp)
    val footnote = style(13.sp)
    val caption = style(12.sp)
    val caption2 = style(11.sp)
}

fun TextStyle.semibold() = copy(fontWeight = FontWeight.SemiBold)
fun TextStyle.bold() = copy(fontWeight = FontWeight.Bold)
fun TextStyle.serif() = copy(fontFamily = FontFamily.Serif)

@Composable
fun DersDefteriTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.board,
            onPrimary = Color.White,
            primaryContainer = colors.board,
            onPrimaryContainer = Color.White,
            secondary = colors.amber,
            tertiary = colors.blue,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.paper,
            onSurface = colors.ink,
            surfaceVariant = colors.card,
            onSurfaceVariant = colors.inkSoft,
            surfaceContainer = colors.card,
            surfaceContainerLow = colors.card,
            surfaceContainerHigh = colors.card,
            surfaceContainerHighest = colors.card,
            surfaceContainerLowest = colors.paper,
            outline = colors.inkSoft.copy(alpha = 0.4f),
            outlineVariant = colors.line,
            error = colors.red,
            secondaryContainer = colors.accent.copy(alpha = 0.18f),
            onSecondaryContainer = colors.accent,
        )
    } else {
        lightColorScheme(
            primary = colors.board,
            onPrimary = Color.White,
            primaryContainer = colors.board,
            onPrimaryContainer = Color.White,
            secondary = colors.amber,
            tertiary = colors.blue,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.paper,
            onSurface = colors.ink,
            surfaceVariant = colors.card,
            onSurfaceVariant = colors.inkSoft,
            surfaceContainer = colors.card,
            surfaceContainerLow = colors.card,
            surfaceContainerHigh = colors.card,
            surfaceContainerHighest = colors.card,
            surfaceContainerLowest = colors.paper,
            outline = colors.inkSoft.copy(alpha = 0.4f),
            outlineVariant = colors.line,
            error = colors.red,
            secondaryContainer = colors.accent.copy(alpha = 0.12f),
            onSecondaryContainer = colors.accent,
        )
    }
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
