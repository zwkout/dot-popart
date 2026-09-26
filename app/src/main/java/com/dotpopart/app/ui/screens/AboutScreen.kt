package com.dotpopart.app.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotpopart.app.BuildConfig
import com.dotpopart.app.data.AppSettings
import com.dotpopart.app.ui.PreviewEngine
import com.dotpopart.app.ui.components.DotCard
import com.dotpopart.app.ui.components.DotIcon
import com.dotpopart.app.ui.components.DotIcons
import com.dotpopart.app.ui.components.MatrixWindow
import com.dotpopart.app.ui.components.tap
import com.dotpopart.app.ui.theme.DotFonts
import com.dotpopart.app.ui.theme.LocalDot
import com.dotpopart.engine.Geo
import com.dotpopart.engine.ToyCatalog

/**
 * リンク先。URL が決まっていないものは null にしておくと表示しない。
 * アプリ自身は通信しない。リンクは外部のブラウザ（メールアプリ）で開く。
 */
object AboutLinks {
    val FEEDBACK: String? = null
    val PRIVACY: String? = null
    fun playground(big: Boolean) = "https://playground.nothing.tech/toys?device=${if (big) "phone_3" else "phone_4a_pro"}"
}

@Composable
fun AboutScreen(
    s: AppSettings,
    geo: Geo,
    engine: PreviewEngine,
    bottomInset: Dp,
    onOpenUrl: (String) -> Unit,
    onLicenses: () -> Unit,
) {
    val c = LocalDot.current
    val active = ToyCatalog[s.activeToy] ?: ToyCatalog.default
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 32.dp + bottomInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val frame = engine.frame("L:${active.id}", active, s.paramsOf(active.id), geo)
        MatrixWindow(frame, geo, engine, 196.dp, 172.dp, 3f, 1.3f, 8f, s.brightness)
        Text("Dot=PopArt", Modifier.padding(top = 8.dp), style = TextStyle(fontFamily = DotFonts.doto, fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 44.sp), color = c.text)
        Text("VERSION ${BuildConfig.VERSION_NAME}", style = TextStyle(fontFamily = DotFonts.mono, fontSize = 11.sp, letterSpacing = 0.14.em()), color = c.text4)
        Text(
            "Nothing Phone (3) / (4a) Pro の\nGlyph Matrix で遊ぶ、小さなおもちゃ集。",
            style = TextStyle(fontFamily = DotFonts.sans, fontSize = 13.sp, lineHeight = 22.sp),
            color = c.text3,
            textAlign = TextAlign.Center,
        )
        DotCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column {
                AboutLinks.FEEDBACK?.let { url -> LinkRow("フィードバックを送る", external = true, divider = true) { onOpenUrl(url) } }
                LinkRow("オープンソースライセンス", external = false, divider = true, onClick = onLicenses)
                AboutLinks.PRIVACY?.let { url -> LinkRow("プライバシーポリシー", external = true, divider = true) { onOpenUrl(url) } }
                LinkRow("Glyph Toys Playground", external = true, divider = false) { onOpenUrl(AboutLinks.playground(geo.big)) }
            }
        }
        Text("PHONE (3) · PHONE (4a) PRO", Modifier.padding(top = 8.dp), style = TextStyle(fontFamily = DotFonts.mono, fontSize = 10.sp, letterSpacing = 0.1.em()), color = c.text4)
    }
}

/** 同梱しているものと、そのライセンス（本文は assets/licenses/）。 */
private class License(val name: String, val holder: String, val kind: String, val file: String?)

private val LICENSES = listOf(
    License("Doto", "The Doto Project Authors", "SIL Open Font License 1.1", "OFL-Doto.txt"),
    License("IBM Plex Sans JP", "IBM Corp.", "SIL Open Font License 1.1", "OFL-IBMPlexSansJP.txt"),
    License("IBM Plex Mono", "IBM Corp.", "SIL Open Font License 1.1", "OFL-IBMPlexMono.txt"),
    License("Jetpack Compose・AndroidX・DataStore", "The Android Open Source Project", "Apache License 2.0", "Apache-2.0.txt"),
    License("Kotlin・kotlinx.coroutines", "JetBrains s.r.o. and contributors", "Apache License 2.0", "Apache-2.0.txt"),
    License("Glyph Matrix SDK", "Nothing Technology Limited", "Glyph SDK End User License Agreement", null),
    License("Natural Earth（地図データ）", "Natural Earth", "パブリックドメイン", null),
)

@Composable
fun LicensesScreen(bottomInset: Dp) {
    val c = LocalDot.current
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (l in LICENSES) {
            var open by remember { mutableStateOf(false) }
            DotCard(Modifier.fillMaxWidth()) {
                Column(Modifier.tap(l.name, enabled = l.file != null) { open = !open }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(l.name, style = TextStyle(fontFamily = DotFonts.sans, fontWeight = FontWeight.Bold, fontSize = 14.sp), color = c.text)
                    Text("${l.holder} · ${l.kind}", style = TextStyle(fontFamily = DotFonts.sans, fontSize = 12.sp), color = c.text3)
                    if (l.file != null) {
                        if (open) {
                            Text(
                                readAsset(context, "licenses/${l.file}"),
                                Modifier.padding(top = 8.dp),
                                style = TextStyle(fontFamily = DotFonts.mono, fontSize = 10.sp, lineHeight = 15.sp),
                                color = c.text2,
                            )
                        } else {
                            DotIcon(DotIcons.CHEVRON, 14.dp, c.chevron, modifier = Modifier.align(Alignment.End))
                        }
                    }
                }
            }
        }
    }
}

private fun readAsset(context: Context, path: String): String =
    runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("")
