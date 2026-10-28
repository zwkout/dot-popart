# Dot=PopArt

Nothing Phone の背面の Glyph Matrix で動く、14種の小さなおもちゃのアプリ（非公式・非商用）。

## ビルドとテスト

- `app/libs/glyph-matrix-sdk-2.0.aar` がないとビルドが止まる（再配布禁止なのでリポジトリに入れない）。
- まとめて確かめるとき：`./gradlew :toy-engine:test :app:testDebugUnitTest :app:assembleDebug`
- おもちゃのロジックだけ変えたときは `./gradlew :toy-engine:test` で足りる。
- 実機で確かめるときは `device-check` スキルを使う。

## 仕様の置き場所

- 仕様の正は `docs/spec.md`。経緯は `docs/design.md`、参照実装は `docs/prototype/toys.js`。
- `docs/` は gitignore されていて、この PC にだけある（ワークツリーには `.worktreeinclude` でコピーされる）。コミットしない。
- 見た目の最新版は claude.ai のデザインキャンバス「Dot=PopArt」。

## 実機で決めたこと

Glyph の描き方、明るさ、センサーの向きは、実機で測って決めた。理由はコードのコメント（`DotPopArtToyService`、`LedMapper`、`InputHub`）と `docs/design.md` にある。変える前に読む。特に、アプリ層は使わずおもちゃの層だけに描く。

## やらないこと

- ネットワーク通信を足さない。通知は件数だけを数え、中身は読まない。
- Nothing の公式に見える名前やロゴを使わない。

## 書き方

- コミットメッセージは英語の短い命令形（例：`Trim the small gray descriptions across the app`）。
- コードのコメントと README は平易な日本語。UI の日本語でも、おもちゃは「Toys」と呼ぶ。
