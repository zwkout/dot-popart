package com.dotpopart.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotpopart.app.ui.PreviewEngine
import com.dotpopart.app.ui.components.BrightnessPicker
import com.dotpopart.app.ui.components.DotIcon
import com.dotpopart.app.ui.components.DotIcons
import com.dotpopart.app.ui.components.DotSlider
import com.dotpopart.app.ui.components.MatrixWindow
import com.dotpopart.app.ui.components.SectionLabel
import com.dotpopart.app.ui.components.Segmented
import com.dotpopart.app.ui.components.tap
import com.dotpopart.app.ui.theme.DotFonts
import com.dotpopart.app.ui.theme.LocalDot
import com.dotpopart.engine.Control
import com.dotpopart.engine.Geo
import com.dotpopart.engine.ToyDef

/** 設定シートで編集中の値。✕ で捨て、✓ で保存する。 */
@Stable
class SheetDraft(val def: ToyDef, params: Map<String, Float>, brightness: Int) {
    var params by mutableStateOf(def.params(params).asMap())
    var brightness by mutableIntStateOf(brightness)
}

/**
 * 設定シートの中身。プレビューは編集中の設定をその場で反映し、設定を変えると最初からやり直す。
 * @param devTools 開発用の表示（プレビューで見る端末の切り替えと注記）を出すか（隠し操作のあとだけ）
 */
@Composable
fun SettingsSheet(
    draft: SheetDraft,
    geo: Geo,
    engine: PreviewEngine,
    devTools: Boolean,
    notificationAccess: Boolean,
    maxHeight: Dp,
    bottomInset: Dp,
    onDevice: (Int) -> Unit,
    onRequestNotifications: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val c = LocalDot.current
    val def = draft.def
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(shape)
            .background(c.sheet)
            .border(1.dp, c.border, shape)
            .tap(onClick = {}) // シートの中のタップを暗幕に通さない
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).background(c.border2, RoundedCornerShape(2.dp)))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).border(1.dp, c.border2, CircleShape).tap("変更を破棄して閉じる", onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) { DotIcon(DotIcons.CLOSE, 20.dp, c.text) }
            Text(
                def.en,
                Modifier.weight(1f),
                style = TextStyle(fontFamily = DotFonts.doto, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 0.05.em()),
                color = c.text,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(c.inv).tap("保存して適用", onClick = onSave),
                contentAlignment = Alignment.Center,
            ) { DotIcon(DotIcons.CHECK, 22.dp, c.invText, stroke = 2.4f) }
        }

        val frame = engine.frame("S", def, draft.params, geo)
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)) {
            MatrixWindow(frame, geo, engine, 236.dp, 208.dp, 4f, 1.8f, 10f, draft.brightness)
        }

        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (devTools) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PREVIEW", style = TextStyle(fontFamily = DotFonts.mono, fontSize = 10.sp, letterSpacing = 0.12.em()), color = c.text4)
                    SmallSegmented(listOf("(4a) Pro", "(3)"), if (geo.n == 25) 1 else 0) { onDevice(if (it == 1) 25 else 13) }
                }
            }
            Spacer(Modifier.weight(1f))
            val action = def.action
            if (action != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.surface)
                        .border(1.dp, c.border2, RoundedCornerShape(20.dp))
                        .tap(action) { engine.act("S") }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.size(6.dp).background(c.accent, CircleShape))
                    Text(action, style = TextStyle(fontFamily = DotFonts.sans, fontWeight = FontWeight.Medium, fontSize = 12.5.sp), color = c.text)
                }
            }
        }
        // 注記はプレビューの仕組みの説明なので、隠し操作のあとだけに出す
        val note = if (!devTools) null else def.note ?: if (def.usesTilt) "傾きは実機のセンサー" else null
        if (note != null) {
            Text(
                note,
                Modifier.fillMaxWidth().offset(y = (-6).dp), // デザインの margin-top: -6px
                style = TextStyle(fontFamily = DotFonts.mono, fontSize = 10.sp),
                color = c.text4,
                textAlign = TextAlign.End,
            )
        }
        if (def.id == "aquarium" && !notificationAccess) {
            NoticeRow(
                "本物の通知でエサを落とすには、通知へのアクセスを許可してください。通知の中身は読みません。",
                "許可する",
                onRequestNotifications,
            )
        }

        for (ctl in def.controls) {
            val value = draft.params[ctl.key] ?: ctl.default
            when (ctl) {
                is Control.Slider -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        SectionLabel(ctl.label)
                        Text(ctl.format(value), style = TextStyle(fontFamily = DotFonts.mono, fontSize = 13.sp), color = c.text)
                    }
                    DotSlider(value, ctl.min, ctl.max, ctl.step) { draft.params = draft.params + (ctl.key to it) }
                }
                is Control.Choice -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel(ctl.label)
                    Segmented(ctl.options, Math.round(value)) { draft.params = draft.params + (ctl.key to it.toFloat()) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("明るさ", Modifier.width(44.dp))
            BrightnessPicker(draft.brightness, Modifier.weight(1f)) { draft.brightness = it }
        }
    }
}

@Composable
fun NoticeRow(text: String, action: String, onAction: () -> Unit) {
    val c = LocalDot.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.track).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, Modifier.weight(1f), style = TextStyle(fontFamily = DotFonts.sans, fontSize = 12.sp, lineHeight = 18.sp), color = c.text2)
        Box(
            Modifier.clip(RoundedCornerShape(18.dp)).background(c.inv).tap(action, onClick = onAction).padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(action, style = TextStyle(fontFamily = DotFonts.sans, fontWeight = FontWeight.Medium, fontSize = 12.5.sp), color = c.invText)
        }
    }
}

/** 小さな区切りボタン（設定シートの端末切り替え）。 */
@Composable
private fun SmallSegmented(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val c = LocalDot.current
    Row(Modifier.background(c.track, RoundedCornerShape(12.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (sel) c.inv else Color.Transparent)
                    .tap(label) { onPick(i) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(label, style = TextStyle(fontFamily = DotFonts.sans, fontWeight = FontWeight.Medium, fontSize = 12.sp), color = if (sel) c.invText else c.text3)
            }
        }
    }
}
