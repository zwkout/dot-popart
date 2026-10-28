package com.dotpopart.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotpopart.app.data.AppCueTarget
import com.dotpopart.app.data.AppCueTargets
import com.dotpopart.app.data.AppSettings
import com.dotpopart.app.ui.PreviewEngine
import com.dotpopart.app.ui.components.DotIcon
import com.dotpopart.app.ui.components.DotIcons
import com.dotpopart.app.ui.components.DotSwitch
import com.dotpopart.app.ui.components.MatrixWindow
import com.dotpopart.app.ui.components.tap
import com.dotpopart.app.ui.theme.DotFonts
import com.dotpopart.app.ui.theme.LocalDot
import com.dotpopart.engine.Geo

/**
 * アプリ連動。対象アプリごとのオン／オフだけを置く（docs/app-cue.md の 3.2）。
 * 同じアニメーションのアプリ（Netflix と Prime Video）は 1 枚のカードにまとめ、1 つのスイッチで両方を切り替える。
 * 使用状況へのアクセスが許可されていなければ、許可を求めるボタンを出し、スイッチは押せなくする。
 *
 * 一覧（ホーム）と同じく、見えているカードだけを作り（LazyColumn）、スクロール中はプレビューの更新を止める。
 */
@Composable
fun AppLinkScreen(
    s: AppSettings,
    geo: Geo,
    engine: PreviewEngine,
    usageAccess: Boolean,
    bottomInset: Dp,
    onToggle: (String, Boolean) -> Unit,
    onOpenUsageAccess: () -> Unit,
) {
    val c = LocalDot.current
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { engine.scrolling = it }
    }
    // スクロールの途中で画面を離れても、プレビューが止まったままにならないようにする
    DisposableEffect(Unit) { onDispose { engine.scrolling = false } }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "desc") {
            Text(
                "有効にしたアプリの使用時、固有のアニメーションを表示します。",
                style = TextStyle(fontFamily = DotFonts.sans, fontSize = 13.sp, lineHeight = 20.sp),
                color = c.text3,
            )
        }
        if (!usageAccess) item(key = "access") { UsageAccessButton(onOpenUsageAccess) }
        items(CARDS, key = { it.first().id }) { targets ->
            AppCueCard(targets, s, geo, engine, usageAccess) { on -> targets.forEach { onToggle(it.id, on) } }
        }
    }
}

/** カードの並び。同じアニメーションのアプリは 1 枚にまとめる（並びは最初のアプリの位置）。 */
private val CARDS: List<List<AppCueTarget>> = AppCueTargets.all.groupBy { it.animId }.values.toList()

@Composable
private fun UsageAccessButton(onClick: () -> Unit) {
    val c = LocalDot.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(c.surface, shape)
            .border(1.dp, c.border2, shape)
            .tap("使用状況へのアクセスを許可", onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(7.dp).background(c.accent, CircleShape))
        Text(
            "使用状況へのアクセスを許可",
            Modifier.weight(1f),
            style = TextStyle(fontFamily = DotFonts.sans, fontWeight = FontWeight.Medium, fontSize = 14.sp),
            color = c.text,
        )
        DotIcon(DotIcons.EXTERNAL, 16.dp, c.text3)
    }
}

/**
 * 一覧のカードと同じ寸法。カードのどこを押しても、オン／オフが切り替わる（右のスイッチは見せるだけ）。
 * 2 つのアプリをまとめたカードは、名前を 2 行に並べる。スイッチは、どれかがオンならオンとして出す。
 */
@Composable
private fun AppCueCard(targets: List<AppCueTarget>, s: AppSettings, geo: Geo, engine: PreviewEngine, usageAccess: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalDot.current
    val shape = RoundedCornerShape(22.dp)
    val t = targets.first()
    val on = targets.any { s.appCueOn(it.id) }
    val label = targets.joinToString(" / ") { it.label }
    val frame = engine.cueFrame("C:${t.id}", geo, live = on && usageAccess, anim = t::anim)
    Row(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .tap("$label のアプリ連動", Role.Switch, enabled = usageAccess) { onChange(!on) }
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MatrixWindow(frame, geo, engine, 72.dp, 60.dp, 1.3f, 0.5f, 3f, s.brightness)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (each in targets) {
                // 収まらない名前（Flightradar24 など）は 20px まで縮める。2 行のカードは 20px から
                val max = if (targets.size > 1) 20.sp else 26.sp
                BasicText(
                    each.label,
                    style = TextStyle(fontFamily = DotFonts.doto, fontWeight = FontWeight.Black, fontSize = max, lineHeight = max, letterSpacing = 0.03.em(), color = c.text),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = if (targets.size > 1) 16.sp else 20.sp, maxFontSize = max, stepSize = 1.sp),
                )
            }
        }
        Box(Modifier.alpha(if (usageAccess) 1f else 0.35f)) {
            DotSwitch(on, "$label のアプリ連動", enabled = usageAccess, onChange = null)
        }
    }
}
