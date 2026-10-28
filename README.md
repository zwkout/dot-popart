# Dot=PopArt

Nothing Phone の背面にある Glyph Matrix（丸い LED ドットマトリクス）で動く、14種の小さな「おもちゃ」を集めた Android アプリ。

A collection of 14 tiny toys for the Glyph Matrix on the back of Nothing Phone (4a) Pro / Phone (3). Unofficial and non-commercial.

## おもちゃ

| まとまり | おもちゃ |
|---|---|
| 役に立つ | CLOCK（時計）、BATTERY（バッテリー） |
| 勝手に動く | PONG WARS、OTHELLO、LIFE（ライフゲーム）、MAZE（迷路） |
| 景色 | GLOBE（地球）、METEOR（流れ星） |
| 傾けて遊ぶ | DODGE（障害物よけ）、MERGE（傾けて2048）、MARBLE（ビー玉）、SAND（砂） |
| 小さな生き物 | SPROUT（植物）、AQUARIUM（水槽） |

- 「Dot=PopArt」という1つの Glyph Toy として登録し、中でおもちゃを切り替える。ローテーション（15分／1時間／3時間／伏せるたび）にも対応する。
- アプリの画面では、おもちゃを選び、設定し、ローテーションを組む。
- ネットワーク通信はしない。通知は件数だけを数え、中身は読まない。

## 対応端末

| 端末 | マトリクス | 状態 |
|---|---|---|
| Nothing Phone (4a) Pro | 13×13（137 LED） | 実機で確認済み（「常に Glyph Toy に表示」で動く） |
| Nothing Phone (3) | 25×25（489 LED） | 実機では未確認 |

## ビルド

必要なもの：Android Studio（同梱の JDK）、Android SDK（compileSdk 37）。

1. **Glyph Matrix SDK を置く。** SDK は利用条件で再配布が禁止されているので、このリポジトリには含めていない。[GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit) から `glyph-matrix-sdk-2.0.aar` を取ってきて、`app/libs/` に置く。
2. ビルドとテスト：

   ```bash
   ./gradlew :toy-engine:test :app:testDebugUnitTest :app:assembleDebug
   ```

3. 端末に入れたら、Nothing の設定の Glyph インターフェースで「Dot=PopArt」を選ぶ（(4a) Pro は「常に Glyph Toy に表示」）。アプリのメニューの「Glyph設定」から開ける。

製品版（`assembleRelease`）の署名は、リポジトリの外に置いた鍵で行う。鍵とパスワードはリポジトリに入れない。

## 構成

```
toy-engine/   おもちゃのロジック（純粋な Kotlin。Android に依存しない）と JVM のテスト
app/          Compose の画面、Glyph Toy サービス、LED の明るさの変換、設定の保存（DataStore）
tools/        世界地図のデータを作るスクリプト（Natural Earth から）
```

## ライセンス

このアプリのソースは [Apache License 2.0](LICENSE)。

同梱しているもの：

- 書体 Doto、IBM Plex Sans JP、IBM Plex Mono：SIL Open Font License 1.1（本文は `app/src/main/assets/licenses/`）
- 世界地図：[Natural Earth](https://www.naturalearthdata.com/)（パブリックドメイン）から作った地図データ

含めていないもの：

- Glyph Matrix SDK：Nothing Technology Limited の [Glyph SDK End User License Agreement](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/blob/main/LICENSE.md) に従う。

## 非商用・非公式

このアプリは非商用の個人制作です。有料での配布や広告など、商用での利用はしません（Glyph Matrix SDK の利用条件では、商用の利用に Nothing の事前の書面による許可が必要です）。

Nothing、Glyph は Nothing Technology Limited の商標です。このアプリは Nothing の公式アプリではなく、Nothing とは関係ありません。

Unofficial, fan-made and non-commercial. Not affiliated with or endorsed by Nothing Technology Limited.
