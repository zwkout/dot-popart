package com.dotpopart.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.dotpopart.app.ui.components.CardTitle
import com.dotpopart.app.ui.components.tap
import com.dotpopart.app.ui.theme.DotFonts
import com.dotpopart.app.ui.theme.LocalDot

/**
 * Glyph設定。点灯のしかたとシステムの Glyph の設定への案内、電池の最適化を置く。
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
            SystemLinkCard(
                "Glyphインターフェース設定を開く",
                if (DeviceInfo.aodOnly) "「常に表示」で Dot=PopArt を選ぶ" else "Glyph Toys に Dot=PopArt を追加",
                onOpenGlyphSettings,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("電池の最適化")
            SystemLinkCard(
                "電池の最適化の設定を開く",
                if (batteryExempt) "最適化から外してあります" else "最適化から外すのがおすすめ",
                onRequestBatteryExempt,
            )
        }

        if (!DeviceInfo.aodOnly) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Glyphボタン")
                DotCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CardTitle("Glyphボタンで操作")
                        Segmented(ButtonAction.entries.map { it.label }, s.buttonAction.ordinal) { onButtonAction(ButtonAction.entries[it]) }
                    }
                }
            }
        }
    }
}

/** システムの設定を開くカード。下の小さな一行で、そこで何をするかを示す。 */
@Composable
private fun SystemLinkCard(label: String, hint: String, onClick: () -> Unit) {
    val c = LocalDot.current
    DotCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().tap(label, onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = TextStyle(fontFamily = DotFonts.sans, fontWeight = FontWeight.Medium, fontSize = 14.sp), color = c.text)
                Text(hint, style = TextStyle(fontFamily = DotFonts.sans, fontSize = 12.sp, lineHeight = 18.sp), color = c.text3)
            }
            DotIcon(DotIcons.EXTERNAL, 16.dp, c.chevron)
        }
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

