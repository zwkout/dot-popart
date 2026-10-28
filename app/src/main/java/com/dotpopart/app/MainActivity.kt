package com.dotpopart.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.dotpopart.app.data.AppSettings
import com.dotpopart.app.data.SettingsRepository
import com.dotpopart.app.data.ThemeMode
import com.dotpopart.app.device.DeviceInfo
import com.dotpopart.app.glyph.InputHub
import com.dotpopart.app.glyph.LiveDraft
import com.dotpopart.app.glyph.AppCue
import com.dotpopart.app.glyph.NotificationCounter
import com.dotpopart.app.ui.PreviewEngine
import com.dotpopart.app.ui.components.SlideFrom
import com.dotpopart.app.ui.components.SlideOverlay
import com.dotpopart.app.ui.screens.AboutScreen
import com.dotpopart.app.ui.screens.Drawer
import com.dotpopart.app.ui.screens.Header
import com.dotpopart.app.ui.screens.HomeScreen
import com.dotpopart.app.ui.screens.LicensesScreen
import com.dotpopart.app.ui.screens.RotationScreen
import com.dotpopart.app.ui.screens.Screen
import com.dotpopart.app.ui.screens.SettingsSheet
import com.dotpopart.app.ui.screens.SheetDraft
import com.dotpopart.app.ui.screens.AppLinkScreen
import com.dotpopart.app.ui.screens.GlyphSettingsScreen
import com.dotpopart.app.ui.theme.DotMotion
import com.dotpopart.app.ui.theme.DotTheme
import com.dotpopart.app.ui.theme.LocalDot
import com.dotpopart.engine.Geo
import com.dotpopart.engine.Inputs
import com.dotpopart.engine.ToyCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var repo: SettingsRepository
    private lateinit var hub: InputHub
    private val previewInputs = Inputs()
    private val engine = PreviewEngine(previewInputs)

    /** 画面に戻ってきたときに読み直すシステムの状態。 */
    private var batteryExempt by mutableStateOf(false)
    private var notificationAccess by mutableStateOf(false)
    private var usageAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = (application as DotPopArtApp).settings
        hub = InputHub(this)
        setContent { AppRoot() }
    }

    override fun onResume() {
        super.onResume()
        hub.start(fastRate = true)
        batteryExempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        notificationAccess = NotificationCounter.isEnabled(this)
        usageAccess = AppCue.hasAccess(this)
    }

    override fun onPause() {
        LiveDraft.current = null
        hub.stop()
        super.onPause()
    }

    @Composable
    private fun AppRoot() {
        val s by repo.settings.collectAsState(initial = null)
        val settings = s ?: return
        val light = settings.theme == ThemeMode.LIGHT
        LaunchedEffect(light) {
            val style = if (light) {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
        DotTheme(light) { App(settings) }
    }

    @Composable
    private fun App(s: AppSettings) {
        val c = LocalDot.current
        val scope = rememberCoroutineScope()
        var screen by remember { mutableStateOf(Screen.TOYS) }
        var drawerOpen by remember { mutableStateOf(false) }
        var draft by remember { mutableStateOf<SheetDraft?>(null) }
        var sheetOpen by remember { mutableStateOf(false) }
        var askNotifications by remember { mutableStateOf(false) }
        var sheetMoving by remember { mutableStateOf(false) }
        var drawerMoving by remember { mutableStateOf(false) }
        // 開発用の表示（端末切り替え、プレビューの注記）の隠し操作：タイトルを10回続けてタップ。
        // もう10回で隠す。デバッグ版でも最初は隠しておく（配布する画面と同じに見えるように）。
        var unlocked by rememberSaveable { mutableStateOf(false) }
        var taps by remember { mutableIntStateOf(0) }
        var lastTap by remember { mutableLongStateOf(0L) }

        // 隠しているときは、前に選んだ端末が残っていても実機に合わせる
        val preview = if (unlocked) s.previewDevice else 0
        val geo: Geo = if (preview == 13 || preview == 25) Geo.of(preview) else DeviceInfo.geo
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // プレビューを 10fps で進める。計算は裏のスレッドで行い、UI スレッドはコマの差し替えだけ。
        // ここでは engine.version を読まない（読むと画面全体が 0.1秒ごとに作り直される）。
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        // 設定シートで編集中のおもちゃを、サービスに背面へ描いてもらう
        LaunchedEffect(sheetOpen, draft) {
            val d = draft
            if (!sheetOpen || d == null) {
                LiveDraft.current = null
                return@LaunchedEffect
            }
            snapshotFlow { LiveDraft.Draft(d.def.id, d.params, d.brightness) }.collect { LiveDraft.current = it }
        }
        LaunchedEffect(Unit) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var last = SystemClock.elapsedRealtime()
                var next = last
                while (true) {
                    val now = SystemClock.elapsedRealtime()
                    previewInputs.epochMillis = System.currentTimeMillis()
                    previewInputs.gravityX = -hub.ax // 画面上のプレビューは画面側から見た向き
                    previewInputs.gravityY = hub.ay
                    previewInputs.batteryLevel = hub.batteryLevel
                    previewInputs.charging = hub.charging
                    previewInputs.notifications = NotificationCounter.count.get()
                    val dt = (now - last) / 1000.0
                    last = now
                    withContext(Dispatchers.Default) { engine.step(dt) }
                    engine.publish()
                    // 固定レートで刻む（処理時間ぶんのずれを貯めない）
                    next = maxOf(next + 100, SystemClock.elapsedRealtime())
                    delay(next - SystemClock.elapsedRealtime())
                }
            }
        }

        BackHandler(enabled = sheetOpen || drawerOpen || screen != Screen.TOYS) {
            when {
                sheetOpen -> sheetOpen = false
                drawerOpen -> drawerOpen = false
                screen == Screen.LICENSES -> screen = Screen.ABOUT
                else -> screen = Screen.TOYS
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
            val fullHeight = maxHeight
            Column(Modifier.fillMaxSize().padding(top = top)) {
                Header(
                    home = screen == Screen.TOYS,
                    title = screen.title,
                    leds = DeviceInfo.geo.ledCount,
                    onMenu = { drawerOpen = true },
                    onTitleTap = {
                        val now = SystemClock.elapsedRealtime()
                        taps = if (now - lastTap < 600) taps + 1 else 1
                        lastTap = now
                        if (taps >= 10) {
                            unlocked = !unlocked
                            taps = 0
                        }
                    },
                )
                val slide = with(LocalDensity.current) { 10.dp.roundToPx() }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        (fadeIn(tween(DotMotion.SCREEN_FADE_MS)) + slideInVertically(tween(DotMotion.SCREEN_MOVE_MS, easing = DotMotion.EaseOpen)) { slide })
                            .togetherWith(ExitTransition.None)
                    },
                    label = "screen",
                ) { target ->
                    Box(Modifier.fillMaxSize()) {
                        when (target) {
                            Screen.TOYS -> HomeScreen(s, geo, engine, bottom) { def ->
                                draft = SheetDraft(def, s.paramsOf(def.id), s.brightness)
                                sheetOpen = true
                            }
                            Screen.ROTATION -> RotationScreen(
                                s, geo, engine, bottom,
                                onEnabled = { scope.launch { repo.setRotationEnabled(it) } },
                                onInterval = { scope.launch { repo.setRotationInterval(it) } },
                                onOrder = { scope.launch { repo.setRotationOrder(it) } },
                                onToys = { scope.launch { repo.setRotationToys(it) } },
                            )
                            Screen.GLYPH -> GlyphSettingsScreen(
                                s, batteryExempt, bottom,
                                onOpenGlyphSettings = { openGlyphToys() },
                                onRequestBatteryExempt = { openBatteryOptimization() },
                                onButtonAction = { scope.launch { repo.setButtonAction(it) } },
                            )
                            Screen.APP_LINK -> AppLinkScreen(
                                s, geo, engine, usageAccess, bottom,
                                onToggle = { id, on -> scope.launch { repo.setAppCue(id, on) } },
                                onOpenUsageAccess = { openUsageAccess() },
                            )
                            Screen.ABOUT -> AboutScreen(s, geo, engine, bottom, onOpenUrl = { openUrl(it) }, onLicenses = { screen = Screen.LICENSES })
                            Screen.LICENSES -> LicensesScreen(bottom)
                        }
                    }
                }
            }

            SlideOverlay(
                visible = sheetOpen,
                from = SlideFrom.Bottom,
                scrimLabel = "設定を閉じる",
                onDismiss = { sheetOpen = false },
                onClosed = { draft = null },
                onMoving = { sheetMoving = it; engine.overlayMoving = sheetMoving || drawerMoving },
            ) {
                draft?.let { d ->
                    SettingsSheet(
                        draft = d,
                        geo = geo,
                        engine = engine,
                        devTools = unlocked,
                        notificationAccess = notificationAccess,
                        maxHeight = fullHeight - top - 16.dp,
                        bottomInset = bottom,
                        onDevice = { n -> scope.launch { repo.setPreviewDevice(if (n == DeviceInfo.matrixLength) 0 else n) } },
                        onRequestNotifications = { openNotificationAccess() },
                        onCancel = { sheetOpen = false },
                        onSave = {
                            scope.launch {
                                repo.applyToy(d.def.id, d.params)
                                repo.setBrightness(d.brightness)
                            }
                            sheetOpen = false
                            if (d.def.id == "aquarium" && !notificationAccess) askNotifications = true
                        },
                    )
                }
            }

            SlideOverlay(
                visible = drawerOpen,
                from = SlideFrom.Start,
                scrimLabel = "メニューを閉じる",
                onDismiss = { drawerOpen = false },
                onMoving = { drawerMoving = it; engine.overlayMoving = sheetMoving || drawerMoving },
            ) {
                Drawer(
                    s, screen, geo, engine, top, bottom,
                    onClose = { drawerOpen = false },
                    onGo = {
                        drawerOpen = false
                        screen = it
                    },
                    onLight = { on -> scope.launch { repo.setTheme(if (on) ThemeMode.LIGHT else ThemeMode.DARK) } },
                )
            }
        }

        if (askNotifications) {
            AlertDialog(
                onDismissRequest = { askNotifications = false },
                title = { Text("通知へのアクセス") },
                text = { Text("AQUARIUM は、通知が来るとエサが落ちてきます。通知が来たことだけを数え、中身は読みません。") },
                confirmButton = {
                    TextButton(onClick = {
                        askNotifications = false
                        openNotificationAccess()
                    }) { Text("設定を開く") }
                },
                dismissButton = { TextButton(onClick = { askNotifications = false }) { Text("あとで") } },
                containerColor = c.sheet,
                titleContentColor = c.text,
                textContentColor = c.text2,
            )
        }
    }

    /**
     * システムの Glyph の設定を開く。見つかった最初の画面を開く。
     * - (4a) Pro：「常に Glyph Toy に表示」でおもちゃを選ぶ画面（AodToySelectActivity）。
     *   README の ToysManagerActivity は、この端末（B4.1-260907）では開くとすぐ閉じてしまう。
     * - Phone (3)：Glyph Toys 管理画面（ToysManagerActivity、Developer Kit の README の方法）。
     * - どちらもなければ Glyph Interface の設定、それもなければ設定アプリ。
     *   Flip to Glyph の画面を直接開くアクションは、システム専用の権限が要るので使えない。
     */
    private fun openGlyphToys() {
        fun thirdparty(cls: String) = Intent().setComponent(ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.$cls"))
        val aod = thirdparty("AodToySelectActivity")
        val toys = thirdparty("ToysManagerActivity")
        val glyph = Intent(GLYPH_SETTINGS_ACTION)
        val settings = Intent(Settings.ACTION_SETTINGS)
        val candidates = if (DeviceInfo.aodOnly) listOf(aod, glyph, settings) else listOf(toys, glyph, settings)
        val target = candidates.firstOrNull { packageManager.resolveActivity(it, 0) != null } ?: return
        openSystem(target)
    }

    /**
     * 電池の最適化。対象外でなければ、対象外にするかを尋ねるシステムのダイアログを出す。
     * すでに対象外なら（ダイアログは出ないので）、システムの一覧を開いて戻せるようにする。
     */
    private fun openBatteryOptimization() {
        val exempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (exempt) {
            openSystem(list)
        } else {
            val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            runCatching { startActivity(ask) }.onFailure { openSystem(list) }
        }
    }

    private companion object {
        const val GLYPH_SETTINGS_ACTION = "android.settings.ACTION_GLYPHS_SETTINGS"
    }

    /** システムの画面を開く。同じタスクで開くので、戻るとこのアプリに戻る。 */
    private fun openSystem(intent: Intent) {
        runCatching { startActivity(intent) }
    }

    private fun openNotificationAccess() {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(this, NotificationCounter::class.java).flattenToString())
        runCatching { startActivity(detail) }.onFailure { openSystem(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    /** 使用状況へのアクセス。このアプリの詳細を開けなければ一覧を開く。 */
    private fun openUsageAccess() {
        val detail = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName"))
        runCatching { startActivity(detail) }.onFailure { openSystem(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    }

    private fun openUrl(url: String) {
        openSystem(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
