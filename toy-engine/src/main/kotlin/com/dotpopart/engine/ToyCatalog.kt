package com.dotpopart.engine

import com.dotpopart.engine.toys.AquariumToy
import com.dotpopart.engine.toys.BatteryToy
import com.dotpopart.engine.toys.ClockToy
import com.dotpopart.engine.toys.DodgeToy
import com.dotpopart.engine.toys.GlobeToy
import com.dotpopart.engine.toys.LifeToy
import com.dotpopart.engine.toys.MarbleToy
import com.dotpopart.engine.toys.MazeToy
import com.dotpopart.engine.toys.MergeToy
import com.dotpopart.engine.toys.MeteorToy
import com.dotpopart.engine.toys.OthelloToy
import com.dotpopart.engine.toys.PongToy
import com.dotpopart.engine.toys.SandToy
import com.dotpopart.engine.toys.SproutToy

/**
 * おもちゃ1種の定義。
 * @property en アプリ内に出す英語名
 * @property ja 日本語名（ローテーション画面と内部用）
 * @property rule 設定シートに出す説明文
 * @property controls 設定シートに出す項目（どのおもちゃも1つだけ）
 * @property fixed 固定値（保存しない）
 * @property group 一覧で少し間を空けるためのまとまり（見出しは出さない）
 * @property note プレビューの注記（例：「充電は早回し」）
 * @property action 試し操作ボタンのラベル。ないおもちゃは null
 * @property usesTilt 加速度センサーを使うか（使うおもちゃが点灯している間だけセンサーを速く動かす）
 */
class ToyDef(
    val id: String,
    val en: String,
    val ja: String,
    val rule: String,
    val group: Int,
    val controls: List<Control>,
    val fixed: Map<String, Float> = emptyMap(),
    val note: String? = null,
    val action: String? = null,
    val usesTilt: Boolean = false,
    val create: (ToyContext) -> Toy,
) {
    fun params(values: Map<String, Float> = emptyMap()) = Params(controls, fixed, values)

    /** テスト用：固定値も変えられる。 */
    internal fun paramsOverridingFixed(values: Map<String, Float>) = Params(controls, fixed, values, values)
}

private fun slider(key: String, label: String, min: Int, max: Int, step: Int, def: Int, unit: String) =
    Control.Slider(key, label, min.toFloat(), max.toFloat(), step.toFloat(), def.toFloat()) { "${Math.round(it)}$unit" }

/** 速さは「普通」で固定。 */
private val NORMAL_SPEED = mapOf("speed" to 1f)

object ToyCatalog {
    /**
     * 並び順（一覧・ギャラリー・ローテーションすべて共通）：役に立つもの2つを先頭に、
     * そのあとは PONG WARS を筆頭に、だんだんくだらなくなっていく順。
     */
    val all: List<ToyDef> = listOf(
        // 役に立つ
        ToyDef(
            "clock", "CLOCK", "時計", "リングが秒を刻むデジタル時計", 0,
            listOf(Control.Choice("h12", "表示", listOf("24時間", "12時間"))),
            fixed = mapOf("ring" to 40f),
        ) { ClockToy(it) },
        ToyDef(
            "battery", "BATTERY", "バッテリー", "残量が20%を切るとリングが点滅", 0,
            listOf(Control.Choice("digits", "表示", listOf("リングのみ", "リング＋数字"), defaultIndex = 1)),
            fixed = mapOf("warn" to 20f),
        ) { BatteryToy(it) },
        // 勝手に動く
        ToyDef(
            "pong", "PONG WARS", "ping pongバトル", "明と暗が陣地を塗り合うブロック崩し", 1,
            listOf(slider("balls", "ボールの数（各陣営）", 1, 5, 1, 2, "個")),
            fixed = NORMAL_SPEED,
        ) { PongToy(it) },
        ToyDef(
            "othello", "OTHELLO", "ランダムオセロ", "丸い盤面でランダムオセロ", 1,
            listOf(Control.Choice("style", "打ち方", listOf("ランダム", "貪欲"))),
            fixed = NORMAL_SPEED,
        ) { OthelloToy(it) },
        ToyDef(
            "life", "LIFE", "ライフゲーム", "コンウェイのライフゲーム", 1,
            listOf(slider("density", "初期密度", 10, 60, 5, 35, "%")),
            fixed = NORMAL_SPEED,
        ) { LifeToy(it) },
        ToyDef(
            "maze", "MAZE", "迷路", "迷路が掘られ、1ドットが手探りで解く", 1,
            listOf(Control.Choice("gen", "迷路を掘る様子", listOf("見せる", "見せない"))),
            fixed = NORMAL_SPEED,
        ) { MazeToy(it) },
        // 景色
        ToyDef(
            "globe", "GLOBE", "地球", "手のひらの中で、今日も地球が回っている", 2,
            listOf(Control.Choice("spin", "自転", listOf("実時間", "ゆっくり", "速い"), defaultIndex = 1)),
            fixed = mapOf("night" to 0f),
        ) { GlobeToy(it) },
        ToyDef(
            "meteor", "METEOR", "流れ星", "たまにだけ流れ星が流れる", 2,
            listOf(Control.Choice("stars", "星の数", listOf("少ない", "多い"), defaultIndex = 1)),
            fixed = mapOf("per" to 10f),
            note = "頻度を上げて表示中",
            action = "流れ星を呼ぶ",
        ) { MeteorToy(it) },
        // 傾けて遊ぶ
        ToyDef(
            "dodge", "DODGE", "障害物よけ", "傾けて車をよける", 3,
            listOf(speedControl()),
            fixed = mapOf("traffic" to 1f),
            note = "傾けないと自動運転",
            usesTilt = true,
        ) { DodgeToy(it) },
        ToyDef(
            "merge", "MERGE", "傾けて2048", "傾けると光が流れて、混ざり合う", 3,
            listOf(Control.Choice("sens", "傾きの感度", listOf("鈍い", "普通", "敏感"), defaultIndex = 1)),
            note = "プレビューは自動プレイ",
            usesTilt = true,
        ) { MergeToy(it) },
        ToyDef(
            "marble", "MARBLE", "転がるビー玉", "傾けると転がり、縁に当たると震える", 3,
            listOf(Control.Choice("vib", "バイブ", listOf("オフ", "弱", "強"), defaultIndex = 1)),
            fixed = mapOf("bounce" to 0.5f),
            action = "揺らす",
            usesTilt = true,
        ) { MarbleToy(it) },
        ToyDef(
            "sand", "SAND", "砂", "流れ落ちる砂", 3,
            listOf(Control.Choice("mode", "量の意味", listOf("半分", "今日の残り時間"))),
            fixed = mapOf("amount" to 50f),
            usesTilt = true,
        ) { SandToy(it) },
        // 小さな生き物
        ToyDef(
            "sprout", "SPROUT", "植物", "充電中だけ育ち、満ちると花が咲く", 4,
            listOf(Control.Choice("shape", "花の形", listOf("丸", "星"))),
            fixed = mapOf("bloom" to 100f),
            note = "充電は早回し",
            action = "抜き挿し",
        ) { SproutToy(it) },
        ToyDef(
            "aquarium", "AQUARIUM", "水槽", "通知が来るとエサが落ちてくる", 4,
            listOf(slider("fish", "魚の数", 1, 3, 1, 2, "匹")),
            fixed = mapOf("food" to 0f),
            action = "通知を送る",
        ) { AquariumToy(it) },
    )

    private val byId = all.associateBy { it.id }

    operator fun get(id: String): ToyDef? = byId[id]

    /** 点灯中のおもちゃの既定値。 */
    val default: ToyDef get() = byId.getValue("pong")
}
