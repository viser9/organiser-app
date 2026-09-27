@file:OptIn(ExperimentalTextApi::class)

package com.viser.organiser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.viser.organiser.R

object C {
    val Ground = Color(0xFFF7F6F3)
    val Ink = Color(0xFF151515)
    val Nav = Color(0xFF121212)
    val Card = Color(0xFFFFFFFF)
    val Line = Color(0xFFE4E2DD)
    val LineSoft = Color(0xFFEEECE7)
    val Chip = Color(0xFFF2F0EC)
    val Muted = Color(0xFF5E5C57)
    val Faint = Color(0xFF8A8781)
    val NavIdle = Color(0xFF9A9893)
    val Late = Color(0xFFB42318)
    val Good = Color(0xFF1F5C40)
    val DarkCard = Color(0xFF1C1C1C)
    val DarkLine = Color(0xFF333333)
    val DarkLine2 = Color(0xFF3A3A3A)
    val DarkSeg = Color(0xFF262626)
    val OnDarkMuted = Color(0xFFBDBBB6)
    val OnDarkFaint = Color(0xFFA3A19C)
    val Dashed = Color(0xFF5A5A5A)
    val Sheet = Color(0xFFD4D1CB)
    val GoalPlumText = Color(0xFFE9D6E2)
    val Plum = Color(0xFF7A2358)
    val GoalBlue = Color(0xFF1E3A8A)
    val GoalBlueText = Color(0xFFDDE5FF)
    val GoalTeal = Color(0xFF0F4A46)
    val GoalTealText = Color(0xFFD3EEEB)

    val plumBrush = Brush.radialGradient(
        colors = listOf(Color(0xFF7A2358), Color(0xFF3A0E2E), Color(0xFF1E0818)),
    )

    /** Palette for sections / labels. */
    val labelColors = listOf(
        0xFFD9622B, 0xFF2F7A5B, 0xFF2B5FD9, 0xFF6B4FB8, 0xFFB8860B,
        0xFFB42318, 0xFF0F4A46, 0xFF7A2358, 0xFF5E5C57,
    )

    val goalPalette = listOf(
        Triple(Color(0xFF7A2358), Color(0xFFE9D6E2), true),
        Triple(GoalBlue, GoalBlueText, false),
        Triple(GoalTeal, GoalTealText, false),
    )
}

private fun onest(w: Int) = Font(
    R.font.onest,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

private fun newsreader(w: Int) = Font(
    R.font.newsreader,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Sans = FontFamily(onest(400), onest(500), onest(600), onest(700))
val Serif = FontFamily(newsreader(500), newsreader(600))

object T {
    fun sans(size: Int, weight: Int = 400, spacing: TextUnit = TextUnit.Unspecified, color: Color = C.Ink) =
        TextStyle(fontFamily = Sans, fontSize = size.sp, fontWeight = FontWeight(weight), letterSpacing = spacing, color = color)

    fun serif(size: Int, weight: Int = 600, spacing: TextUnit = TextUnit.Unspecified, color: Color = C.Ink) =
        TextStyle(fontFamily = Serif, fontSize = size.sp, fontWeight = FontWeight(weight), letterSpacing = spacing, color = color)

    /** Small upper-case kicker: 11px / 600 / 0.18em */
    fun kicker(color: Color = C.Muted) = sans(11, 600, 0.18.em, color)
}

@Composable
fun OrganiserTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.Ink,
            onPrimary = Color.White,
            background = C.Ground,
            surface = C.Ground,
            onSurface = C.Ink,
            onBackground = C.Ink,
            secondary = C.Ink,
            surfaceVariant = C.Chip,
            outline = C.Line,
        ),
        typography = MaterialTheme.typography.let { t ->
            t.copy(
                bodyLarge = t.bodyLarge.copy(fontFamily = Sans),
                bodyMedium = t.bodyMedium.copy(fontFamily = Sans),
                labelLarge = t.labelLarge.copy(fontFamily = Sans),
                titleLarge = t.titleLarge.copy(fontFamily = Serif),
                headlineSmall = t.headlineSmall.copy(fontFamily = Serif),
            )
        },
        content = content,
    )
}
