package com.dotpopart.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.ceil
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotpopart.app.glyph.LedMapper
import com.dotpopart.app.ui.PreviewEngine
import com.dotpopart.app.ui.theme.LocalDot
import com.dotpopart.engine.Geo

/** プレビューの明るさの4段階（デザインで決めた値）。 */
private val PREVIEW_BRIGHTNESS = floatArrayOf(0.45f, 0.65f, 0.85f, 1f)

/**
 * 丸い窓の中に、エンジンの出力を丸いドットで描く（デザインの cellStyle）。
 * 明るさ 0〜1 を rgb(30,30,30)〜rgb(248,248,248) の灰色で描き、0.55 を超えた点は少し光らせる。
 * 窓はライトモードでも黒のまま（実機の LED が黒いパネルで光る見た目に合わせる）。
 *
 * @param outer 窓の直径
 * @param inner ドットを並べる範囲の一辺
 * @param gapSmall ドットの間隔（13×13 のとき）。25×25 では [gapLarge]
 * @param glow 光らせる半径（px 相当の dp）。25×25 では 0.55 倍
 */
@Composable
fun MatrixWindow(
    frame: FloatArray,
    geo: Geo,
    engine: PreviewEngine,
    outer: Dp,
    inner: Dp,
    gapSmall: Float,
    gapLarge: Float,
    glow: Float,
    level: Int,
    border: Boolean = true,
) {
    val brightness = PREVIEW_BRIGHTNESS[level.coerceIn(0, 3)]
    val c = LocalDot.current
    // 窓の中身は、コマが進んだときだけ画像に描いておき、画面にはその画像を1枚貼る。
    // 点を直接描くと、スクロール中は中身が変わらなくても毎コマ約2000個の丸を描き直すことになり、
    // RenderThread と GPU で 1コマ 11ms ほどかかっていた（120Hz の 8.3ms に収まらない）。
    val cache = remember(geo.n) { MatrixImage() }
    Box(
        Modifier
            .size(outer)
            .background(c.bezel, CircleShape)
            .then(if (border) Modifier.border(1.dp, c.bezelBorder, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        ComposeCanvas(Modifier.size(inner)) {
            val version = engine.version // コマが進んだら描き直す（描く段階でだけ読む）
            engine.touch(frame)
            val gap = (if (geo.big) gapLarge else gapSmall).dp.toPx()
            val g = (if (geo.big) maxOf(1f, glow * 0.55f) else glow).dp.toPx()
            // 光が窓の端で切れないよう、画像は光の幅だけ広く作り、そのぶん左上にずらして貼る
            val pad = ceil(g).toInt()
            val image = cache.update(version, frame, geo, size.width.toInt(), pad, gap, g, brightness)
            drawImage(image, topLeft = Offset(-pad.toFloat(), -pad.toFloat()))
        }
    }
}

/**
 * 1つの窓の中身の画像。明るさ 0〜1 を rgb(30,30,30)〜rgb(248,248,248) の灰色の丸で描き、
 * 0.55 を超えた点のまわりを光らせる（デザインの cellStyle と box-shadow）。
 * CPU の Canvas で描くので、光は本物のグラデーションの画像を貼れる。
 */
private class MatrixImage {
    private var bitmap: Bitmap? = null
    private var image: ImageBitmap? = null
    private var drawnVersion = Long.MIN_VALUE
    private var drawnFrame: FloatArray? = null
    private var drawnValues = FloatArray(0)
    private var drawnBrightness = -1f
    private var glowSprite: Bitmap? = null
    private var glowKey = ""
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun update(
        version: Long,
        frame: FloatArray,
        geo: Geo,
        sizePx: Int,
        pad: Int,
        gap: Float,
        g: Float,
        brightness: Float,
    ): ImageBitmap {
        val inner = sizePx.coerceAtLeast(1)
        val side = inner + pad * 2
        var bmp = bitmap
        if (bmp == null || bmp.width != side) {
            bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            bitmap = bmp
            image = bmp.asImageBitmap()
            drawnVersion = Long.MIN_VALUE
            drawnFrame = null
        }
        if (version == drawnVersion && frame === drawnFrame && brightness == drawnBrightness) return image!!
        // コマが進んでも中身が同じなら（止まっているプレビュー、動かない USE など）描き直さない。
        // 丸を描き直すより、配列を比べるほうがずっと軽い
        val same = frame === drawnFrame && brightness == drawnBrightness && frame.contentEquals(drawnValues)
        drawnVersion = version
        if (same) return image!!
        drawnFrame = frame
        drawnBrightness = brightness
        if (drawnValues.size != frame.size) drawnValues = FloatArray(frame.size)
        frame.copyInto(drawnValues)

        val n = geo.n
        val cell = (inner - gap * (n - 1)) / n
        val r = cell / 2
        val sprite = glow(r, g)
        val canvas = Canvas(bmp)
        bmp.eraseColor(android.graphics.Color.TRANSPARENT)
        // 重なり順：暗い点 → 光 → 光っている点。光は消灯中の点の手前に来る
        for (pass in 0..2) {
            if (pass == 1 && sprite == null) continue
            for (i in 0 until geo.size) {
                if (!geo.inside(i)) continue
                val raw = frame.getOrElse(i) { 0f }
                val v = if (raw <= LedMapper.OFF_MAX) 0f else raw
                val l = (v * brightness).coerceIn(0f, 1f)
                val bright = l > 0.55f
                val x = pad + (i % n) * (cell + gap) + r
                val y = pad + (i / n) * (cell + gap) + r
                if (pass == 1) {
                    if (!bright) continue
                    glowPaint.alpha = (255 * 0.45f * l).toInt()
                    val half = sprite!!.width / 2f
                    canvas.drawBitmap(sprite, x - half, y - half, glowPaint)
                } else {
                    if (bright != (pass == 2)) continue
                    val gray = (30 + (248 - 30) * l).toInt()
                    dotPaint.color = android.graphics.Color.rgb(gray, gray, gray)
                    canvas.drawCircle(x, y, r, dotPaint)
                }
            }
        }
        return image!!
    }

    /** 半径 r の点のまわりに、幅 g で消えていく白い光の画像（大きさが変わったときだけ作り直す）。 */
    private fun glow(r: Float, g: Float): Bitmap? {
        if (g <= 0f) return null
        val key = "$r|$g"
        if (key == glowKey) return glowSprite
        val size = ceil((r + g) * 2).toInt().coerceAtLeast(1)
        val sprite = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                size / 2f, size / 2f, r + g,
                intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT),
                floatArrayOf(0f, r / (r + g), 1f),
                Shader.TileMode.CLAMP,
            )
        }
        Canvas(sprite).drawCircle(size / 2f, size / 2f, r + g, paint)
        glowSprite = sprite
        glowKey = key
        return sprite
    }
}
