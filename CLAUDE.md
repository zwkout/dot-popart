# Dot=PopArt

Nothing Phone の背面の Glyph Matrix で動く、14種の小さなおもちゃのアプリ（非公式・非商用）。

## ビルドとテスト

- `app/libs/glyph-matrix-sdk-2.0.aar` がないとビルドが止まる（再配布禁止なのでリポジトリに入れない）。
- まとめて確かめるとき：`./gradlew :toy-engine:test :app:testDebugUnitTest :app:assembleDebug`
- おもちゃのロジックだけ変えたときは `./gradlew :toy-engine:test` で足りる。

## 構成と境界

- `toy-engine/`：おもちゃのロジック。純粋な Kotlin で、Android に依存させない。新しいおもちゃは `Toy` を継承して「init → step → draw」で書き、`ToyCatalog` に登録する。
- `app/`：Compose の画面、Glyph Toy サービス（`glyph/`）、明るさの変換（`LedMapper`）、設定（DataStore）。

## 仕様の置き場所

- 仕様の正は `docs/spec.md`。経緯は `docs/design.md`、参照実装は `docs/prototype/toys.js`。
- `docs/` は gitignore されていて、この PC にだけある。コミットしない。
- 見た目の最新版は claude.ai のデザインキャンバス「Dot=PopArt」。

## 実機で決めた制約（変える前に design.md を読む）

- 描くのはおもちゃの層（`setMatrixFrame`）だけ。アプリ層は使わない。サービスはつないだ直後に `closeAppMatrix()` を呼ぶ。
- 送るのは最大 10fps。同じコマは送らず、1秒に1回だけ再送する。
- 明るさは `LedMapper` の帯で決める。実効 64 未満はムラが出るか見えない。「暗く点灯する部分」は PONG WARS の暗い陣地（0.14）にそろえる。
- 重力の向き：背面のマトリクスでは (ax, ay)、画面のプレビューでは (−ax, ay)。

## 実機での確認

デバッグ版では、adb からおもちゃ・明るさ・試験パターン・アプリ連動を切り替えられる。コマンドは `app/src/debug/java/com/dotpopart/app/DebugReceiver.kt` の先頭のコメントにある。

## やらないこと

- ネットワーク通信を足さない。通知は件数だけを数え、中身は読まない。
- `.aar`、署名の鍵（`*.jks`、`keystore.properties`）、`docs/` をコミットしない。
- Nothing の公式に見える名前やロゴを使わない。

## 書き方

- コミットメッセージは英語の短い命令形（例：`Trim the small gray descriptions across the app`）。
- コードのコメントと README は平易な日本語。UI の日本語でも、おもちゃは「Toys」と呼ぶ。
