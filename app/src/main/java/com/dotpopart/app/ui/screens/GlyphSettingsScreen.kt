package com.dotpopart.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotpopart.app.data.AppSettings
import com.dotpopart.app.data.ButtonAction
import com.dotpopart.app.device.DeviceInfo
import com.dotpopart.app.ui.components.DotCard
import com.dotpopart.app.ui.components.DotIcon
import com.dotpopart.app.ui.components.DotIcons
import com.dotpopart.app.ui.components.SectionLabel
import com.dotpopart.app.ui.components.Segmented
import com.dotpopart.app.ui.components.TitleDesc
import com.dotpopart.app.ui.components.tap
import com.dotpopart.app.ui.theme.DotFonts
import com.dotpopart.app.ui.theme.LocalDot

/**
 * Glyph設定。点灯のしかたの説明とシステムの Glyph の設定への案内、電池の最適化を置く。
 * Phone (3) では Glyph ボタンの長押しの動きも選べる。
 * 「点灯するタイミング」と「Glyphインターフェース設定」をまとめたもの。
 */
@Composable
fun GlyphSettingsScreen(
    s: AppSettings,
    batteryExempt: Boolean,
    bottomInset: Dp,
    onOpenGlyphSettings: () -> Unit,
    onRequestBatteryExempt: () -> Unit,
    onButtonAction: (ButtonAction) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Glyphインターフェース")
            DotCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (DeviceInfo.aodOnly) {
                        TitleDesc(
                            "いつでも光ります",
                            "伏せて置いたときも、手に持っているときも、背面のマトリクスに点灯中のおもちゃが出ます" +
                                "（画面を上にして置いている間は消えます）。" +
                                "システムの「常に Glyph Toy に表示」で「Dot=PopArt」を選んでおいてください。",
                            titleSize = 15f,
                            bold = true,
                        )
                    } else {
                        TitleDesc(
                            "Glyph ボタンで選ぶと光ります",
                            "Glyph ボタンを押しておもちゃを切り替え、「Dot=PopArt」を選ぶと点灯します。" +
                                "システムの設定で、Glyph Toys に「Dot=PopArt」を加えておいてください。",
                            titleSize = 15f,
                            bold = true,
                        )
                    }
                    PillButton("Glyphインターフェース設定を開く", DotIcons.EXTERNAL, onOpenGlyphSettings)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("電池の最適化")
            DotCard(Modifier.fillMaxWidth().tap(onClick = onRequestBatteryExempt)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TitleDesc(
                        if (batteryExempt) "対象外になっています（おすすめ）" else "対象外にするのがおすすめ",
                        "画面が消えている間もコマ落ちしにくくなります。点灯中は電池を少し多めに使います。",
                        titleSize = 15f,
                        bold = true,
                    )
                    if (batteryExempt) {
                        PillButton("電池の最適化の設定を開く", DotIcons.EXTERNAL, onRequestBatteryExempt)
                    } else {
                        PillButton("最適化の対象外にする", null, onRequestBatteryExempt)
                    }
                }
            }
        }

        if (!DeviceInfo.aodOnly) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Glyphボタン")
                DotCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TitleDesc("Glyphボタンで操作", "長押ししたときの動き")
                        Segmented(ButtonAction.entries.map { it.label }, s.buttonAction.ordinal) { onButtonAction(ButtonAction.entries[it]) }
                    }
                }
            }
        }
    }
}

@Composable
fun PillButton(label: String, icon: String?, onClick: () -> Unit) {
    val c = LocalDot.current
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(c.inv).tap(label, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = TextStyle(fontFamily = DotFonts.sans, fontWeight = FontWeight.Medium, fontSize = 13.sp), color = c.invText)
        if (icon != null) DotIcon(icon, 15.dp, c.invText)
    }
}

/** 押せる行（このアプリについてのリンクなど）。 */
@Composable
fun LinkRow(label: String, external: Boolean, divider: Boolean, onClick: () -> Unit) {
    val c = LocalDot.current
    Column {
        Row(
            Modifier.fillMaxWidth().tap(label, onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), style = TextStyle(fontFamily = DotFonts.sans, fontSize = 14.sp), color = c.text)
            DotIcon(if (external) DotIcons.EXTERNAL else DotIcons.CHEVRON, 16.dp, c.chevron)
        }
        if (divider) HorizontalDivider(thickness = 1.dp, color = c.hair)
    }
}

