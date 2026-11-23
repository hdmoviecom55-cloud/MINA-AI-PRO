package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.MinaThemeStyle

@Immutable
data class MinaExtendedPalette(
    val isDark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val glassFill: Color,
    val glassBorderStart: Color,
    val glassBorderEnd: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val goldHighlight: Color,
    val particleColor: Color,
    val codeBackground: Color
)

val LocalMinaPalette = staticCompositionLocalOf {
    MinaExtendedPalette(
        isDark = true,
        bgTop = EspressoDeep,
        bgBottom = Color(0xFF21130B),
        glassFill = Color(0xB8281910),
        glassBorderStart = Color(0xAAFFD54F),
        glassBorderEnd = Color(0x44FF6D00),
        gradientStart = AmberOrange,
        gradientEnd = SunGold,
        goldHighlight = SunGold,
        particleColor = SunGold,
        codeBackground = Color(0xFF120B07)
    )
}

val MinaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MinaAiTheme(
    darkMode: Boolean = true,
    themeStyle: MinaThemeStyle = MinaThemeStyle.MIDNIGHT_AMBER,
    content: @Composable () -> Unit
) {
    val effectiveDark = darkMode && themeStyle != MinaThemeStyle.SOLAR_LIGHT

    val extended = when {
        !effectiveDark -> MinaExtendedPalette(
            isDark = false,
            bgTop = IvoryBackground,
            bgBottom = IvorySurface,
            glassFill = Color(0xE6FFFFFF),
            glassBorderStart = Color(0x88FFA000),
            glassBorderEnd = Color(0x44FF6D00),
            gradientStart = AmberOrange,
            gradientEnd = AmberGold,
            goldHighlight = Color(0xFFD97706),
            particleColor = AmberGold,
            codeBackground = Color(0xFF261910)
        )
        themeStyle == MinaThemeStyle.OBSIDIAN_EMBER -> MinaExtendedPalette(
            isDark = true,
            bgTop = ObsidianDeep,
            bgBottom = ObsidianSurface,
            glassFill = Color(0xC2211715),
            glassBorderStart = Color(0xAAFFB74D),
            glassBorderEnd = Color(0x55FF5722),
            gradientStart = EmberAccent,
            gradientEnd = EmberGold,
            goldHighlight = EmberGold,
            particleColor = EmberGold,
            codeBackground = Color(0xFF0B0808)
        )
        themeStyle == MinaThemeStyle.ROYAL_BRONZE -> MinaExtendedPalette(
            isDark = true,
            bgTop = BronzeDeep,
            bgBottom = BronzeSurface,
            glassFill = Color(0xBA2E1F13),
            glassBorderStart = Color(0xAAFFE082),
            glassBorderEnd = Color(0x55FFA000),
            gradientStart = Color(0xFFE65100),
            gradientEnd = ChampagneGold,
            goldHighlight = ChampagneGold,
            particleColor = ChampagneGold,
            codeBackground = Color(0xFF140D08)
        )
        else -> MinaExtendedPalette(
            isDark = true,
            bgTop = EspressoDeep,
            bgBottom = Color(0xFF24140B),
            glassFill = Color(0xBC26170E),
            glassBorderStart = Color(0xAAFFD54F),
            glassBorderEnd = Color(0x44FF6D00),
            gradientStart = AmberOrange,
            gradientEnd = SunGold,
            goldHighlight = SunGold,
            particleColor = SunGold,
            codeBackground = Color(0xFF100906)
        )
    }

    val colorScheme = if (effectiveDark) {
        darkColorScheme(
            primary = extended.gradientEnd,
            onPrimary = EspressoDeep,
            primaryContainer = Color(0xFF472508),
            onPrimaryContainer = SunGold,
            secondary = extended.gradientStart,
            onSecondary = CreamText,
            secondaryContainer = Color(0xFF3A2212),
            onSecondaryContainer = CreamText,
            tertiary = ChampagneGold,
            background = extended.bgTop,
            onBackground = CreamText,
            surface = extended.bgBottom,
            onSurface = CreamText,
            surfaceVariant = EspressoCard,
            onSurfaceVariant = MutedBronzeText,
            outline = Color(0x66FFB300),
            error = ErrorCoral,
            onError = CreamText
        )
    } else {
        lightColorScheme(
            primary = AmberOrange,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE0B2),
            onPrimaryContainer = IvoryText,
            secondary = AmberGold,
            onSecondary = IvoryText,
            secondaryContainer = IvorySurface,
            onSecondaryContainer = IvoryText,
            tertiary = Color(0xFFB45309),
            background = IvoryBackground,
            onBackground = IvoryText,
            surface = IvoryCard,
            onSurface = IvoryText,
            surfaceVariant = IvorySurface,
            onSurfaceVariant = IvoryMutedText,
            outline = Color(0x88D97706),
            error = ErrorCoral,
            onError = Color.White
        )
    }

    CompositionLocalProvider(LocalMinaPalette provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MinaTypography,
            shapes = MinaShapes,
            content = content
        )
    }
}
