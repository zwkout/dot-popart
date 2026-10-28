package com.dotpopart.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

/** ローテーション画面。全体をオフにすると、下の設定は薄く表示して操作できないようにする。 */
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
    val bodyAlpha by animateFloatAsState(if (on) 1f else 0.38f, tween(240), label = "body")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        DotCard(Modifier.fillMaxWidth(), radius = 20.dp) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardTitle("ローテーション", Modifier.weight(1f), titleSize = 15f, bold = true)
                DotSwitch(on, "ローテーション", onChange = onEnabled)
            }
        }
        Column(Modifier.alpha(bodyAlpha), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("切り替えるタイミング")
                Segmented(RotationInterval.entries.map { it.label }, s.rotationInterval.ordinal, enabled = on) { onInterval(RotationInterval.entries[it]) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("順番")
                Segmented(RotationOrder.entries.map { it.label }, s.rotationOrder.ordinal, enabled = on) { onOrder(RotationOrder.entries[it]) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    SectionLabel("ローテーションするおもちゃ")
                    val count = ToyCatalog.all.count { it.id in s.rotationToys }
                    Text("$count / ${ToyCatalog.all.size}", style = TextStyle(fontFamily = DotFonts.mono, fontSize = 12.sp), color = c.text3)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (def in ToyCatalog.all) {
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
                }
            }
        }
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
