package com.dotpopart.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotpopart.app.ui.components.DotIcon
import com.dotpopart.app.ui.components.DotIcons
import com.dotpopart.app.ui.components.tap
import com.dotpopart.app.ui.theme.DotFonts
import com.dotpopart.app.ui.theme.LocalDot

/**
 * タイトルバー（高さ 64）。ホームではアプリ名（ドット書体）と右端の LED 数、ほかの画面では画面名。
 * @param onTitleTap アプリ名をタップしたとき（10回連続で端末切り替えを出す）
 */
@Composable
fun Header(home: Boolean, title: String, leds: Int, onMenu: () -> Unit, onTitleTap: () -> Unit) {
    val c = LocalDot.current
    Column {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(44.dp).tap("メニューを開く", onClick = onMenu), contentAlignment = Alignment.Center) {
                DotIcon(DotIcons.MENU, 22.dp, c.text)
            }
            Box(Modifier.weight(1f)) {
                if (home) {
                    Text(
                        "Dot=PopArt",
                        Modifier.tap(onClick = onTitleTap),
                        style = TextStyle(fontFamily = DotFonts.doto, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 0.01.em()),
                        color = c.text,
                    )
                } else {
                    Text(title, style = TextStyle(fontFamily = DotFonts.sans, fontWeight = FontWeight.Bold, fontSize = 16.sp), color = c.text)
                }
            }
            if (home) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("$leds", style = TextStyle(fontFamily = DotFonts.doto, fontWeight = FontWeight.Black, fontSize = 20.sp, lineHeight = 20.sp), color = c.text)
                    Text(
                        "LED",
                        Modifier.padding(bottom = 2.dp),
                        style = TextStyle(fontFamily = DotFonts.mono, fontSize = 10.sp, letterSpacing = 0.12.em()),
                        color = c.text4,
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = c.hair)
    }
}
