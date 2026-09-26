package com.dotpopart.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotpopart.app.ui.theme.DotFonts
import com.dotpopart.app.ui.theme.DotMotion
import com.dotpopart.app.ui.theme.LocalDot
import kotlin.math.roundToInt

/** SVG のパス（24×24、線で描く）から作るアイコン。デザインの svg をそのまま写す。 */
object DotIcons {
    private val cache = HashMap<String, ImageVector>()

    fun stroke(path: String, width: Float = 2f): ImageVector = cache.getOrPut("$path|$width") {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
    }

    const val MENU = "M3 6h14M3 12h12M3 18h14"
    const val CHEVRON = "M9 5l7 7-7 7"
    const val ROTATE = "M20 12a8 8 0 1 1-2.3-5.7M20 4v4h-4"
    const val CLOSE = "M6 6l12 12M18 6L6 18"
    const val CHECK = "M5 12.5l4.5 4.5L19 7.5"
    const val EXTERNAL = "M14 4h6v6M20 4l-9 9M18 14v6H4V6h6"
    const val GRID = "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z"
    const val SUN_SMALL = "M12 3v2M12 19v2M3 12h2M19 12h2M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8z"
    const val INFO = "M12 8h.01M11 12h1v5h1M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z"
    const val SUN = "M12 4V2M12 22v-2M4 12H2M22 12h-2M5.6 5.6 4.2 4.2M19.8 19.8l-1.4-1.4M5.6 18.4l-1.4 1.4M19.8 4.2l-1.4 1.4M12 7a5 5 0 1 1 0 10 5 5 0 0 1 0-10z"
    const val MOON = "M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z"
}

@Composable
fun DotIcon(path: String, size: Dp, tint: Color, stroke: Float = 2f, modifier: Modifier = Modifier) {
    Icon(DotIcons.stroke(path, stroke), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** 波紋なしで押せるようにする（デザインに合わせる）。 */
@Composable
fun Modifier.tap(label: String? = null, role: Role = Role.Button, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
}

/** 52×32 のスイッチ。つまみは 200ms で動く。 */
@Composable
fun DotSwitch(on: Boolean, label: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = LocalDot.current
    val track by animateColorAsState(if (on) c.inv else c.border2, tween(DotMotion.KNOB_MS), label = "track")
    val knob by animateColorAsState(if (on) c.invText else c.offKnob, tween(DotMotion.KNOB_MS), label = "knob")
    val x by animateDpAsState(if (on) 20.dp else 0.dp, tween(DotMotion.KNOB_MS), label = "x")
    Box(
        Modifier
            .size(52.dp, 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(track)
            .tap(label, Role.Switch, enabled) { onChange(!on) }
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = x).size(24.dp).background(knob, CircleShape))
    }
}

/** 区切りボタン（溝の角丸 14、ボタンの角丸 10、高さ 40）。 */
@Composable
fun Segmented(options: List<String>, selected: Int, enabled: Boolean = true, onPick: (Int) -> Unit) {
    val c = LocalDot.current
    Row(
        Modifier.fillMaxWidth().background(c.track, RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) c.inv else Color.Transparent)
                    .tap(label, Role.RadioButton, enabled) { onPick(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = TextStyle(fontFamily = DotFonts.sans, fontWeight = FontWeight.Medium, fontSize = 12.5.sp),
                    color = if (sel) c.invText else c.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 明るさの4段階。押すとドットの数が増えるボタン。 */
@Composable
fun BrightnessPicker(level: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val c = LocalDot.current
    Row(
        modifier.background(c.track, RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (i in 0..3) {
            val sel = i == level
            Row(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) c.inv else Color.Transparent)
                    .tap("明るさ ${i + 1} / 4", Role.RadioButton) { onPick(i) },
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (j in 0..3) {
                    val dot = if (j <= i) (if (sel) c.invText else c.text) else (if (sel) c.text4 else c.border2)
                    Box(Modifier.size(6.dp).background(dot, CircleShape))
                }
            }
        }
    }
}

/**
 * スライダー。つまみは丸（デザインの range 入力に合わせる）、溝は細い線。
 * 指には連続して追従させ、目盛りへの吸い付きは指を離したときにだけ行う（動きを硬くしない）。
 * 呼び出し側には、目盛りに丸めた値が変わったときだけ知らせる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DotSlider(value: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit) {
    val c = LocalDot.current
    fun snap(v: Float) = (min + ((v - min) / step).roundToInt() * step).coerceIn(min, max)
    var position by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    // 外から値が変わったとき（シートを開き直したなど）は、指を離している間だけ合わせる
    if (!dragging && snap(position) != value) position = value
    val colors = SliderDefaults.colors(
        thumbColor = c.inv,
        activeTrackColor = c.inv,
        inactiveTrackColor = c.border2,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )
    Slider(
        value = position,
        onValueChange = { v ->
            dragging = true
            position = v
            val snapped = snap(v)
            if (snapped != value) onChange(snapped)
        },
        onValueChangeFinished = {
            dragging = false
            position = snap(position)
        },
        valueRange = min..max,
        modifier = Modifier.fillMaxWidth().height(32.dp),
        colors = colors,
        thumb = { Box(Modifier.size(22.dp).background(c.inv, CircleShape)) },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                modifier = Modifier.height(4.dp),
                colors = colors,
                drawStopIndicator = null,
                drawTick = { _, _ -> },
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 2.dp,
            )
        },
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = TextStyle(fontFamily = DotFonts.sans, fontWeight = FontWeight.Medium, fontSize = 13.sp), color = LocalDot.current.text2)
}

/** 面（カード）。角丸と線はデザインどおり。 */
@Composable
fun DotCard(modifier: Modifier = Modifier, radius: Dp = 18.dp, content: @Composable () -> Unit) {
    val c = LocalDot.current
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(radius)),
    ) { content() }
}

/** 見出し＋説明の2行。 */
@Composable
fun TitleDesc(title: String, desc: String?, modifier: Modifier = Modifier, titleSize: Float = 14f, bold: Boolean = false) {
    val c = LocalDot.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = TextStyle(fontFamily = DotFonts.sans, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, fontSize = titleSize.sp), color = c.text)
        if (desc != null) Text(desc, style = TextStyle(fontFamily = DotFonts.sans, fontSize = 12.sp, lineHeight = 18.sp), color = c.text3)
    }
}
