package com.dotpopart.app.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * 使用状況へのアクセスが許可されていなければ、許可を求めるボタンを出し、スイッチは押せなくする。
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "有効にしたアプリの使用時、固有のアニメーションを表示します。",
            style = TextStyle(fontFamily = DotFonts.sans, fontSize = 13.sp, lineHeight = 20.sp),
            color = c.text3,
        )
        if (!usageAccess) UsageAccessButton(onOpenUsageAccess)
        for (t in AppCueTargets.all) {
            AppCueCard(t, s, geo, engine, usageAccess) { onToggle(t.id, it) }
        }
        val dashed = RoundedCornerShape(18.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .drawBehind {
                    val w = 1.dp.toPx()
                    drawRoundRect(
                        c.border2,
                        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("COMING SOON", style = TextStyle(fontFamily = DotFonts.mono, fontSize = 11.sp, letterSpacing = 0.16.em()), color = c.text4)
        }
    }
}

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

/** 一覧のカードと同じ寸法。押しても何も開かない。 */
@Composable
private fun AppCueCard(t: AppCueTarget, s: AppSettings, geo: Geo, engine: PreviewEngine, usageAccess: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalDot.current
    val shape = RoundedCornerShape(22.dp)
    val on = s.appCueOn(t.id)
    val frame = engine.cueFrame("C:${t.id}", geo, live = on && usageAccess, anim = t.anim)
    Row(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MatrixWindow(frame, geo, engine, 72.dp, 60.dp, 1.3f, 0.5f, 3f, s.brightness)
        Text(
            t.label,
            Modifier.weight(1f),
            style = TextStyle(fontFamily = DotFonts.doto, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 0.03.em()),
            color = c.text,
            maxLines = 1,
            softWrap = false,
        )
        Box(Modifier.alpha(if (usageAccess) 1f else 0.35f)) {
            DotSwitch(on, "${t.label} のアプリ連動", enabled = usageAccess, onChange = onChange)
        }
    }
}
