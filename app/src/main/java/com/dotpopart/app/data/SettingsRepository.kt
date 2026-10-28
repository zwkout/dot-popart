package com.dotpopart.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

// ファイルが壊れていたら、入れたばかりの状態からやり直す（読めないまま落ち続けないように）
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** 設定の保存と読み出し（DataStore）。サービスと画面が同じ Flow を購読する。 */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    /** 読み出しに失敗したときは既定値を流す（サービスと画面を落とさない）。 */
    val settings: Flow<AppSettings> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.e("DPA", "settings read failed", e)
            emit(emptyPreferences())
        }
        .map { it.toSettings() }
        .distinctUntilChanged()

    private fun Preferences.toSettings(): AppSettings {
        val params = HashMap<String, HashMap<String, Float>>()
        val cues = HashMap<String, Boolean>()
        for ((key, value) in asMap()) {
            val name = key.name
            if (name.startsWith(APP_CUE_PREFIX) && value is Boolean) cues[name.removePrefix(APP_CUE_PREFIX)] = value
            if (!name.startsWith(TOY_PREFIX) || value !is Float) continue
            val rest = name.removePrefix(TOY_PREFIX)
            val dot = rest.indexOf('.')
            if (dot <= 0) continue
            params.getOrPut(rest.substring(0, dot)) { HashMap() }[rest.substring(dot + 1)] = value
        }
        val d = DEFAULTS
        return AppSettings(
            activeToy = this[ACTIVE_TOY] ?: d.activeToy,
            brightness = (this[BRIGHTNESS] ?: d.brightness).coerceIn(0, 3),
            theme = ThemeMode.of(this[THEME]),
            rotationEnabled = this[ROTATION_ENABLED] ?: d.rotationEnabled,
            rotationToys = this[ROTATION_TOYS] ?: d.rotationToys,
            rotationInterval = RotationInterval.of(this[ROTATION_INTERVAL]),
            rotationOrder = RotationOrder.of(this[ROTATION_ORDER]),
            buttonAction = ButtonAction.of(this[BUTTON_ACTION]),
            appCue = cues,
            toyParams = params,
            rotationCursor = this[ROTATION_CURSOR],
            rotationLastSwitch = this[ROTATION_LAST_SWITCH] ?: d.rotationLastSwitch,
            previewDevice = this[PREVIEW_DEVICE] ?: d.previewDevice,
        )
    }

    /**
     * ✓：設定を保存して、このおもちゃを点灯中にする（ローテーション中は割り込み）。
     * 明るさも一緒に書く（別々に書くと、新しいおもちゃが古い明るさで出るコマができる）。
     */
    suspend fun applyToy(id: String, params: Map<String, Float>, brightness: Int? = null) = store.edit { p ->
        for ((k, v) in params) p[floatPreferencesKey("$TOY_PREFIX$id.$k")] = v
        p[ACTIVE_TOY] = id
        if (brightness != null) p[BRIGHTNESS] = brightness.coerceIn(0, 3)
    }

    /** ローテーションで切り替えたとき（サービスから）。 */
    suspend fun rotateTo(id: String, now: Long) = store.edit { p ->
        p[ACTIVE_TOY] = id
        p[ROTATION_CURSOR] = id
        p[ROTATION_LAST_SWITCH] = now
    }

    /** 切り替えの時刻だけを記録する（初めて動いたときなど）。 */
    suspend fun markRotationStart(now: Long) = store.edit { it[ROTATION_LAST_SWITCH] = now }

    suspend fun setBrightness(level: Int) = store.edit { it[BRIGHTNESS] = level.coerceIn(0, 3) }
    suspend fun setTheme(theme: ThemeMode) = store.edit { it[THEME] = theme.key }
    suspend fun setRotationEnabled(on: Boolean) = store.edit { it[ROTATION_ENABLED] = on }
    /** ローテーションに入れる・外す。読んで書くのを1回の書き込みの中で行う（続けて押しても前の変更を消さない）。 */
    suspend fun toggleRotationToy(id: String) = store.edit {
        val now = it[ROTATION_TOYS] ?: DEFAULTS.rotationToys
        it[ROTATION_TOYS] = if (id in now) now - id else now + id
    }
    suspend fun setRotationInterval(v: RotationInterval) = store.edit { it[ROTATION_INTERVAL] = v.key }
    suspend fun setRotationOrder(v: RotationOrder) = store.edit { it[ROTATION_ORDER] = v.key }
    suspend fun setButtonAction(v: ButtonAction) = store.edit { it[BUTTON_ACTION] = v.key }
    suspend fun setAppCue(id: String, on: Boolean) = store.edit { it[booleanPreferencesKey("$APP_CUE_PREFIX$id")] = on }
    suspend fun setPreviewDevice(n: Int) = store.edit { it[PREVIEW_DEVICE] = n }

    private companion object {
        const val TOY_PREFIX = "toy."
        const val APP_CUE_PREFIX = "appCue."
        val ACTIVE_TOY = stringPreferencesKey("activeToy")
        val BRIGHTNESS = intPreferencesKey("brightness")
        val THEME = stringPreferencesKey("theme")
        val ROTATION_ENABLED = booleanPreferencesKey("rotation.enabled")
        val ROTATION_TOYS = stringSetPreferencesKey("rotation.toys")
        val ROTATION_INTERVAL = stringPreferencesKey("rotation.interval")
        val ROTATION_ORDER = stringPreferencesKey("rotation.order")
        val BUTTON_ACTION = stringPreferencesKey("ops.buttonAction")
        val ROTATION_CURSOR = stringPreferencesKey("rotation.cursor")
        val ROTATION_LAST_SWITCH = longPreferencesKey("rotation.lastSwitch")
        val PREVIEW_DEVICE = intPreferencesKey("debug.previewDevice")
        val DEFAULTS = AppSettings()
    }
}
