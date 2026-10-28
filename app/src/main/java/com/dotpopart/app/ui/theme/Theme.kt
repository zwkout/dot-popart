package com.dotpopart.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.dotpopart.app.R

/** デザイントークン（ダークとライト）。 */
@Immutable
data class DotColors(
    val bg: Color,
    val surface: Color,
    val track: Color,
    val sheet: Color,
    val panel: Color,
    val border: Color,
    val border2: Color,
    val hair: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val text4: Color,
    val chevron: Color,
    val inv: Color,
    val invText: Color,
    val accent: Color,
    val scrim: Color,
    val bezel: Color,
    val bezelBorder: Color,
    val selRow: Color,
    val offKnob: Color,
    val light: Boolean,
) {
    companion object {
        val Dark = DotColors(
            bg = Color(0xFF0A0A0A), surface = Color(0xFF141414), track = Color(0xFF1A1A1A), sheet = Color(0xFF111111),
            panel = Color(0xFF101010), border = Color(0xFF242424), border2 = Color(0xFF3A3A3A), hair = Color(0xFF1F1F1F),
            text = Color(0xFFEDEDED), text2 = Color(0xFFD6D6D6), text3 = Color(0xFF9A9A9A), text4 = Color(0xFF7A7A7A),
            chevron = Color(0xFF6A6A6A), inv = Color(0xFFF2F2F2), invText = Color(0xFF0A0A0A), accent = Color(0xFFE5372B),
            scrim = Color(0x9E000000), bezel = Color(0xFF050505), bezelBorder = Color(0xFF222222), selRow = Color(0xFF1D1D1D),
            offKnob = Color(0xFF8A8A8A), light = false,
        )
        val Light = DotColors(
            bg = Color(0xFFEEEBE5), surface = Color(0xFFF8F6F1), track = Color(0xFFE4E0D7), sheet = Color(0xFFF8F6F1),
            panel = Color(0xFFF6F3ED), border = Color(0xFFDCD7CC), border2 = Color(0xFFC7C0B3), hair = Color(0xFFDCD7CC),
            text = Color(0xFF181715), text2 = Color(0xFF34312C), text3 = Color(0xFF645F56), text4 = Color(0xFF7C766B),
            chevron = Color(0xFF9B958A), inv = Color(0xFF181715), invText = Color(0xFFF8F6F1), accent = Color(0xFFD4372C),
            scrim = Color(0x5228221A), bezel = Color(0xFF161513), bezelBorder = Color(0xFF2A2824), selRow = Color(0xFFE9E5DC),
            offKnob = Color(0xFFFFFFFF), light = true,
        )
    }
}

object DotFonts {
    /** Doto：アプリ名・英語名・数字。可変フォントなので太さを指定して使う。 */
    val doto = FontFamily(
        Font(R.font.doto, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
        Font(R.font.doto, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )

    /** IBM Plex Sans JP：本文。 */
    val sans = FontFamily(
        Font(R.font.plex_sans_jp_regular, FontWeight.Normal),
        Font(R.font.plex_sans_jp_medium, FontWeight.Medium),
        Font(R.font.plex_sans_jp_bold, FontWeight.Bold),
    )

    /** IBM Plex Mono：ラベル・数値。 */
    val mono = FontFamily(
        Font(R.font.plex_mono_regular, FontWeight.Normal),
        Font(R.font.plex_mono_medium, FontWeight.Medium),
    )
}

/** アニメーションの時間とイージング。 */
object DotMotion {
    val EaseOpen = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val EaseClose = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    const val OPEN_MS = 340
    const val CLOSE_MS = 260
    const val SCRIM_MS = 300
    const val SCREEN_FADE_MS = 260
    const val SCREEN_MOVE_MS = 300
    const val THEME_MS = 300
    const val KNOB_MS = 200
    /** アプリを起動して最初に画面を出すときのフェードイン。 */
    const val START_FADE_MS = 400
}

val LocalDot = staticCompositionLocalOf { DotColors.Dark }

/** テーマを切り替えたとき、背景と文字色を 300ms で遷移させる。 */
@Composable
fun DotTheme(light: Boolean, content: @Composable () -> Unit) {
    val target = if (light) DotColors.Light else DotColors.Dark
    val spec = tween<Color>(DotMotion.THEME_MS)
    @Composable
    fun anim(c: Color) = animateColorAsState(c, spec, label = "theme").value
    val colors = DotColors(
        bg = anim(target.bg), surface = anim(target.surface), track = anim(target.track), sheet = anim(target.sheet),
        panel = anim(target.panel), border = anim(target.border), border2 = anim(target.border2), hair = anim(target.hair),
        text = anim(target.text), text2 = anim(target.text2), text3 = anim(target.text3), text4 = anim(target.text4),
        chevron = anim(target.chevron), inv = anim(target.inv), invText = anim(target.invText), accent = anim(target.accent),
        scrim = anim(target.scrim), bezel = anim(target.bezel), bezelBorder = anim(target.bezelBorder), selRow = anim(target.selRow),
        offKnob = anim(target.offKnob), light = light,
    )
    val scheme = if (light) {
        lightColorScheme(primary = colors.inv, background = colors.bg, surface = colors.surface, onSurface = colors.text)
    } else {
        darkColorScheme(primary = colors.inv, background = colors.bg, surface = colors.surface, onSurface = colors.text)
    }
    CompositionLocalProvider(LocalDot provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
