package com.dotpopart.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotpopart.app.data.AppSettings
import com.dotpopart.app.data.RotationInterval
import com.dotpopart.app.data.RotationOrder
import com.dotpopart.app.ui.PreviewEngine
import com.dotpopart.app.ui.components.DotCard
import com.dotpopart.app.ui.components.DotIcon
import com.dotpopart.app.ui.components.DotIcons
import com.dotpopart.app.ui.components.DotSwitch
import com.dotpopart.app.ui.components.MatrixWindow
import com.dotpopart.app.ui.components.SectionLabel
import com.dotpopart.app.ui.components.Segmented
import com.dotpopart.app.ui.components.CardTitle
import com.dotpopart.app.ui.components.tap
import com.dotpopart.app.ui.theme.DotFonts
import com.dotpopart.app.ui.theme.LocalDot
import com.dotpopart.engine.Geo
import com.dotpopart.engine.ToyCatalog
import com.dotpopart.engine.ToyDef

/**
 * ローテーション画面。全体をオフにすると、下の設定は薄く表示して操作できないようにする。
 *
 * 一覧（ホーム）と同じく、見えている行だけを作り（LazyColumn）、スクロール中はプレビューの更新を止める。
 * 薄くする度合いは描く段階（graphicsLayer）でだけ読み、切り替えの間に画面全体を作り直さないようにする。
 */
@Composable
fun RotationScreen(
    s: AppSettings,
    geo: Geo,
    engine: PreviewEngine,
    bottomInset: Dp,
    onEnabled: (Boolean) -> Unit,
    onInterval: (RotationInterval) -> Unit,
    onOrder: (RotationOrder) -> Unit,
    onToys: (Set<String>) -> Unit,
) {
    val c = LocalDot.current
    val on = s.rotationEnabled
    val bodyAlpha = animateFloatAsState(if (on) 1f else 0.38f, tween(240), label = "body")
    val body = Modifier.graphicsLayer { alpha = bodyAlpha.value }
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
    ) {
        item(key = "switch") {
            DotCard(Modifier.fillMaxWidth().padding(bottom = 20.dp), radius = 20.dp) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CardTitle("ローテーション", Modifier.weight(1f), titleSize = 15f, bold = true)
                    DotSwitch(on, "ローテーション", onChange = onEnabled)
                }
            }
        }
        item(key = "interval") {
            Column(body.padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("切り替えるタイミング")
                Segmented(RotationInterval.entries.map { it.label }, s.rotationInterval.ordinal, enabled = on) { onInterval(RotationInterval.entries[it]) }
            }
        }
        item(key = "order") {
            Column(body.padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("順番")
                Segmented(RotationOrder.entries.map { it.label }, s.rotationOrder.ordinal, enabled = on) { onOrder(RotationOrder.entries[it]) }
            }
        }
        item(key = "toys") {
            Row(body.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                SectionLabel("ローテーションする Toys")
                val count = ToyCatalog.all.count { it.id in s.rotationToys }
                Text("$count / ${ToyCatalog.all.size}", style = TextStyle(fontFamily = DotFonts.mono, fontSize = 12.sp), color = c.text3)
            }
        }
        itemsIndexed(ToyCatalog.all, key = { _, def -> "T:${def.id}" }) { i, def ->
            Box(body.padding(top = if (i > 0) 6.dp else 0.dp)) {
                ToyRow(def, s, geo, engine, on, onToys)
            }
        }
    }
}

@Composable
private fun ToyRow(def: ToyDef, s: AppSettings, geo: Geo, engine: PreviewEngine, on: Boolean, onToys: (Set<String>) -> Unit) {
    val c = LocalDot.current
    val checked = def.id in s.rotationToys
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .tap(def.en, Role.Checkbox, enabled = on) {
                onToys(if (checked) s.rotationToys - def.id else s.rotationToys + def.id)
            }
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val frame = engine.frame("L:${def.id}", def, s.paramsOf(def.id), geo)
        MatrixWindow(frame, geo, engine, 42.dp, 34.dp, 0.6f, 0.25f, 2f, s.brightness, border = false)
        Text(def.en, Modifier.weight(1f), style = TextStyle(fontFamily = DotFonts.doto, fontWeight = FontWeight.Black, fontSize = 18.sp, lineHeight = 18.sp), color = c.text, maxLines = 1)
        CheckCircle(checked)
    }
}

@Composable
private fun CheckCircle(checked: Boolean) {
    val c = LocalDot.current
    val bg by animateColorAsState(if (checked) c.inv else Color.Transparent, tween(160), label = "check")
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(bg).border(1.5.dp, if (checked) c.inv else c.border2, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) DotIcon(DotIcons.CHECK, 14.dp, c.invText, stroke = 3f)
    }
}
