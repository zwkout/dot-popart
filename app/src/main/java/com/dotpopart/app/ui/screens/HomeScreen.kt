package com.dotpopart.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotpopart.app.data.AppSettings
import com.dotpopart.app.ui.PreviewEngine
import com.dotpopart.app.ui.components.DotIcon
import com.dotpopart.app.ui.components.DotIcons
import com.dotpopart.app.ui.components.MatrixWindow
import com.dotpopart.app.ui.components.tap
import com.dotpopart.app.ui.theme.DotFonts
import com.dotpopart.app.ui.theme.LocalDot
import com.dotpopart.engine.Geo
import com.dotpopart.engine.ToyCatalog
import com.dotpopart.engine.ToyDef

/** 一覧（ホーム）。カードに丸いプレビュー、英語名、● ON と ↻ の印だけを置く。 */
@Composable
fun HomeScreen(s: AppSettings, geo: Geo, engine: PreviewEngine, bottomInset: androidx.compose.ui.unit.Dp, onOpen: (ToyDef) -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { engine.scrolling = it }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(ToyCatalog.all, key = { _, def -> def.id }) { i, def ->
            // まとまりの間は少し（14px）間を空ける。見出しは出さない
            val gap = i > 0 && ToyCatalog.all[i - 1].group != def.group
            Box(if (gap) Modifier.padding(top = 14.dp) else Modifier) {
                ToyCard(def, s, geo, engine) { onOpen(def) }
            }
        }
    }
}

@Composable
private fun ToyCard(def: ToyDef, s: AppSettings, geo: Geo, engine: PreviewEngine, onClick: () -> Unit) {
    val c = LocalDot.current
    val shape = RoundedCornerShape(22.dp)
    val frame = engine.frame("L:${def.id}", def, s.paramsOf(def.id), geo)
    Row(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .tap("${def.en} の設定を開く", onClick = onClick)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MatrixWindow(frame, geo, engine, 72.dp, 60.dp, 1.3f, 0.5f, 3f, s.brightness)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                def.en,
                style = TextStyle(fontFamily = DotFonts.doto, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 0.03.em()),
                color = c.text,
                maxLines = 1,
                softWrap = false,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.activeToy == def.id) OnBadge()
                if (s.rotationEnabled && def.id in s.rotationToys) {
                    DotIcon(DotIcons.ROTATE, 13.dp, c.text4, stroke = 2.2f)
                }
            }
        }
        DotIcon(DotIcons.CHEVRON, 18.dp, c.chevron)
    }
}

@Composable
fun OnBadge() {
    val c = LocalDot.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).background(c.accent, CircleShape))
        Text("ON", style = TextStyle(fontFamily = DotFonts.mono, fontSize = 10.sp, letterSpacing = 0.1.em()), color = c.text)
    }
}

/** CSS の letter-spacing（em）を sp に。 */
fun Double.em() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Em)
