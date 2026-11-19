package dev.thirtymin.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Design reference: docs/design/30-Min.dc.html (style sheet section).
// Always dark. Amber = actions (at most once or twice per screen), green = success only.
private val Colors =
    darkColorScheme(
        primary = Color(0xFFF2B35B),
        onPrimary = Color(0xFF2B1C06),
        primaryContainer = Color(0xFF3D2E19),
        onPrimaryContainer = Color(0xFFF8D7A6),
        secondary = Color(0xFFCDBFA9),
        onSecondary = Color(0xFF2B1C06),
        secondaryContainer = Color(0xFF3D2E19),
        onSecondaryContainer = Color(0xFFF8D7A6),
        tertiary = Color(0xFF8DB58A),
        onTertiary = Color(0xFF13240F),
        tertiaryContainer = Color(0xFF263323),
        onTertiaryContainer = Color(0xFFC3DDBF),
        error = Color(0xFFE59A8C),
        background = Color(0xFF14120F),
        onBackground = Color(0xFFECE3D6),
        surface = Color(0xFF14120F),
        onSurface = Color(0xFFECE3D6),
        surfaceVariant = Color(0xFF2A2620),
        onSurfaceVariant = Color(0xFFA39A8C),
        surfaceContainerLowest = Color(0xFF0F0E0B),
        surfaceContainerLow = Color(0xFF1B1915),
        surfaceContainer = Color(0xFF201D19),
        surfaceContainerHigh = Color(0xFF2A2620),
        surfaceContainerHighest = Color(0xFF34302A),
        outline = Color(0xFF6B6357),
        outlineVariant = Color(0xFF2F2B25)
    )

// One variable font file (res/font/manrope.ttf, OFL), one Font entry per weight used.
private val Manrope =
    FontFamily(
        listOf(200, 300, 400, 500, 600, 700, 800).map { w ->
            Font(
                R.font.manrope,
                FontWeight(w),
                variationSettings = FontVariation.Settings(FontVariation.weight(w))
            )
        }
    )

private fun style(size: Int, line: Int, weight: Int, spacing: Float = 0f) = TextStyle(
    fontFamily = Manrope,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = spacing.sp
)

private val Type =
    Typography(
        displayLarge = style(128, 128, 200, -6f), // streak hero
        displayMedium = style(68, 68, 300, -2f).copy(fontFeatureSettings = "tnum"), // countdown
        headlineLarge = style(32, 40, 500, -0.5f),
        headlineMedium = style(28, 36, 600, -0.4f),
        titleLarge = style(22, 28, 600),
        titleMedium = style(16, 24, 700),
        titleSmall = style(15, 20, 600),
        bodyLarge = style(16, 24, 400),
        bodyMedium = style(14, 20, 400),
        bodySmall = style(13, 18, 400),
        labelLarge = style(14, 20, 700),
        labelMedium = style(12, 16, 700),
        labelSmall = style(12, 16, 600)
    )

private val Shape =
    Shapes(
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )

@Composable
fun ThirtyMinTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Colors, typography = Type, shapes = Shape, content = content)
