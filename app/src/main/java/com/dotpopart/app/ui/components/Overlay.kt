package com.dotpopart.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import com.dotpopart.app.ui.theme.DotMotion
import com.dotpopart.app.ui.theme.LocalDot
import kotlinx.coroutines.launch

enum class SlideFrom { Bottom, Start }

/**
 * 暗幕の上に、下（設定シート）か左（メニュー）から出てくる面を重ねる。
 * 開く：340ms cubic-bezier(0.2, 0.8, 0.2, 1)、閉じる：260ms cubic-bezier(0.4, 0, 1, 1)、暗幕：300ms。
 *
 * 指で動かせる：シートは下へ、メニューは左へドラッグすると指に合わせて動き、暗幕も連動して薄くなる。
 * 離したとき、速く払ったか半分近く動かしていれば閉じ、そうでなければ元に戻る。
 * シートの中身がスクロールするときは、一番上まで戻ってからさらに引くとシートが下がる（nested scroll）。
 *
 * 閉じるアニメーションが終わるまで中身を残すので、呼ぶ側は閉じる間も中身を渡し続ける。
 */
@Composable
fun SlideOverlay(
    visible: Boolean,
    from: SlideFrom,
    scrimLabel: String,
    onDismiss: () -> Unit,
    onClosed: () -> Unit = {},
    /** 開閉のアニメーション中か、指で動かしている間は true。プレビューの描き直しを止めるのに使う。 */
    onMoving: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    /** 開いている度合い（0：閉じている、1：開いている）。 */
    val progress = remember { Animatable(0f) }
    val scrim = remember { Animatable(0f) }
    var present by remember { mutableStateOf(false) }
    var extent by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    var dragging by remember { mutableStateOf(false) }
    val moving by rememberUpdatedState(onMoving)
    LaunchedEffect(Unit) {
        snapshotFlow { progress.isRunning || dragging }.collect { moving(it) }
    }

    LaunchedEffect(visible) {
        if (visible) {
            present = true
            launch { scrim.animateTo(1f, tween(DotMotion.SCRIM_MS, easing = LinearEasing)) }
            progress.animateTo(1f, tween(DotMotion.OPEN_MS, easing = DotMotion.EaseOpen))
        } else if (present) {
            // 指で途中まで動かしていたら、その位置から閉じる
            val rest = progress.value
            launch { scrim.animateTo(0f, tween((DotMotion.SCRIM_MS * rest).toInt().coerceAtLeast(1), easing = LinearEasing)) }
            progress.animateTo(0f, tween((DotMotion.CLOSE_MS * rest).toInt().coerceAtLeast(1), easing = DotMotion.EaseClose))
            present = false
            onClosed()
        }
    }
    if (!present) return

    /** 指の移動（閉じる向きを正とした px）で動かす。 */
    fun drag(closingPx: Float) {
        dragging = true
        val p = (progress.value - closingPx / extent).coerceIn(0f, 1f)
        scope.launch {
            progress.snapTo(p)
            scrim.snapTo(p)
        }
    }

    /** 指を離したとき（閉じる向きを正とした速さ px/s）。 */
    fun settle(closingVelocity: Float) {
        dragging = false
        if (closingVelocity > FLING_PX_PER_SEC || (closingVelocity > -FLING_PX_PER_SEC && progress.value < 0.6f)) {
            dismiss()
        } else {
            scope.launch {
                launch { scrim.animateTo(1f, tween(DotMotion.SCRIM_MS, easing = LinearEasing)) }
                progress.animateTo(1f, tween(DotMotion.OPEN_MS, easing = DotMotion.EaseOpen))
            }
        }
    }

    // シート：中身のスクロールと取り合う
    val sheetScroll = remember(from) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 下げた状態から上へ戻すときは、先にシートを戻す
                if (available.y < 0 && progress.value < 1f) {
                    drag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // 中身が一番上まで来ても余った下向きの動きで、シートを下げる
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    drag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (progress.value < 1f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    val drawerDrag = rememberDraggableState { delta -> drag(-delta) }

    val c = LocalDot.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrim.value }
                .background(c.scrim)
                .tap(scrimLabel, onClick = onDismiss)
                .then(
                    if (from == SlideFrom.Start) {
                        Modifier.draggable(drawerDrag, Orientation.Horizontal, onDragStopped = { v -> settle(-v) })
                    } else {
                        Modifier
                    },
                ),
        )
        Box(
            Modifier
                .align(if (from == SlideFrom.Bottom) Alignment.BottomCenter else Alignment.TopStart)
                .onSizeChanged { extent = (if (from == SlideFrom.Bottom) it.height else it.width).toFloat().coerceAtLeast(1f) }
                .graphicsLayer {
                    val rest = 1f - progress.value
                    if (from == SlideFrom.Bottom) translationY = size.height * rest else translationX = -size.width * rest
                }
                .then(
                    if (from == SlideFrom.Bottom) {
                        Modifier.nestedScroll(sheetScroll)
                    } else {
                        Modifier.draggable(drawerDrag, Orientation.Horizontal, onDragStopped = { v -> settle(-v) })
                    },
                ),
            content = content,
        )
    }
}

/** これより速く払ったら、動かした量にかかわらず閉じる／戻す。 */
private const val FLING_PX_PER_SEC = 1200f
