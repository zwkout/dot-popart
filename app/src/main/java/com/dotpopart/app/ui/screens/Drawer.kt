package com.dotpopart.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotpopart.app.BuildConfig
import com.dotpopart.app.data.AppSettings
import com.dotpopart.app.device.DeviceInfo
import com.dotpopart.app.ui.PreviewEngine
import com.dotpopart.app.ui.components.DotIcon
import com.dotpopart.app.ui.components.DotIcons
import com.dotpopart.app.ui.components.DotSwitch
import com.dotpopart.app.ui.components.MatrixWindow
import com.dotpopart.app.ui.components.tap
import com.dotpopart.app.ui.theme.DotFonts
import com.dotpopart.app.ui.theme.LocalDot
import com.dotpopart.engine.Geo
import com.dotpopart.engine.ToyCatalog

enum class Screen(val title: String) {
    TOYS("Dot=PopArt"),
    ROTATION("ローテーション"),
    APP_LINK("アプリ連動"),
    GLYPH("Glyph設定"),
    ABOUT("このアプリについて"),
    LICENSES("オープンソースライセンス"),
}

private class MenuItem(val screen: Screen, val label: String, val icon: String)

private val MENU = listOf(
    MenuItem(Screen.TOYS, "Toys", DotIcons.GRID),
    MenuItem(Screen.ROTATION, "ローテーション", DotIcons.ROTATE),
    MenuItem(Screen.APP_LINK, "アプリ連動", DotIcons.LINK),
    MenuItem(Screen.GLYPH, "Glyph設定", DotIcons.SUN_SMALL),
    MenuItem(Screen.ABOUT, "このアプリについて", DotIcons.INFO),
)

@Composable
fun Drawer(
    s: AppSettings,
    screen: Screen,
    geo: Geo,
    engine: PreviewEngine,
    topInset: Dp,
    bottomInset: Dp,
    onClose: () -> Unit,
    onGo: (Screen) -> Unit,
    onLight: (Boolean) -> Unit,
) {
    val c = LocalDot.current
    val shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    Column(
        Modifier
            .width(308.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.border, shape)
            .tap(onClick = {})
            .padding(start = 12.dp, end = 12.dp, top = 12.dp + topInset, bottom = 20.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Dot=PopArt", style = TextStyle(fontFamily = DotFonts.doto, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 28.sp, letterSpacing = 0.01.em()), color = c.text)
                Text("GLYPH TOYS · ${DeviceInfo.displayName}", style = TextStyle(fontFamily = DotFonts.mono, fontSize = 10.sp, letterSpacing = 0.12.em()), color = c.text4)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).tap("メニューを閉じる", onClick = onClose), contentAlignment = Alignment.Center) {
                DotIcon(DotIcons.CLOSE, 20.dp, c.text3)
            }
        }

        // NOW ON MATRIX：点灯中のおもちゃ
        val active = ToyCatalog[s.activeToy] ?: ToyCatalog.default
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val frame = engine.frame("L:${active.id}", active, s.paramsOf(active.id), geo)
            MatrixWindow(frame, geo, engine, 64.dp, 54.dp, 1f, 0.4f, 3f, s.brightness)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).background(c.accent, CircleShape))
                    Text("NOW ON MATRIX", style = TextStyle(fontFamily = DotFonts.mono, fontSize = 10.sp, letterSpacing = 0.1.em()), color = c.text3)
                }
                Text(active.en, style = TextStyle(fontFamily = DotFonts.doto, fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 22.sp), color = c.text)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (m in MENU) {
                val sel = m.screen == screen || (m.screen == Screen.ABOUT && screen == Screen.LICENSES)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (sel) c.selRow else Color.Transparent)
                        .tap(m.label) { onGo(m.screen) }
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(3.dp, 24.dp).background(if (sel) c.accent else Color.Transparent, RoundedCornerShape(2.dp)))
                    DotIcon(m.icon, 20.dp, if (sel) c.text else c.text2, stroke = 1.8f)
                    Text(m.label, Modifier.weight(1f), style = TextStyle(fontFamily = DotFonts.sans, fontWeight = FontWeight.Medium, fontSize = 14.sp), color = if (sel) c.text else c.text2)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DotIcon(if (c.light) DotIcons.SUN else DotIcons.MOON, 20.dp, c.text, stroke = 1.8f)
            Text("ライトモード", Modifier.weight(1f), style = TextStyle(fontFamily = DotFonts.sans, fontWeight = FontWeight.Medium, fontSize = 14.sp), color = c.text)
            DotSwitch(c.light, "ライトモード", onChange = onLight)
        }
        Text(
            "v${BuildConfig.VERSION_NAME.substringBeforeLast('.')} · ${if (geo.big) "Phone (3)" else "Phone (4a) Pro"} · ${geo.ledCount} LED",
            Modifier.padding(horizontal = 8.dp),
            style = TextStyle(fontFamily = DotFonts.mono, fontSize = 10.sp, letterSpacing = 0.1.em()),
            color = c.text4,
        )
    }
}
