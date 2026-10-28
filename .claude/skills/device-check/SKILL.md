---
name: device-check
description: Dot=PopArt のデバッグ版を USB でつないだ Nothing Phone に入れ、adb でおもちゃ・明るさ・試験パターン・アプリ連動を切り替え、背面の LED に届いたコマを文字の絵にして確かめる。「実機で確かめて」「端末で見て」「LED にどう出てるか見て」と頼まれたときに使う。
---

# 実機での確認

背面の LED に何が出ているかは、Glyph サービスが logcat に出すコマから読み取れる。端末の操作は adb だけで行い、画面は見ない。

## 0. 端末をつなぐ

adb は PATH に入っていない。Bash からは `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb"` で呼ぶ（以下 `adb` と書く）。

`adb devices` に端末が出なければ、ここで止めて、USB でつないで USB デバッグを許可してもらうよう頼む。

## 1. 入れる

```bash
./gradlew :app:installDebug
adb shell input keyevent KEYCODE_WAKEUP
```

アプリを更新するとプロセスが止まり、次に画面がつくまでおもちゃのサービスはつながらない。入れたあとは必ず画面をつける。

Nothing の設定の Glyph インターフェースで「Dot=PopArt」が選ばれていないと何も出ない。初めての端末なら、利用者に確かめる。

## 2. 端末の置き方を頼む

画面を上にして置いている間は、サービスがコマの計算も送信も止める。**伏せて置くか、手に持ってもらう**。

## 3. 切り替える

```bash
# おもちゃ（id は ToyCatalog：clock battery pong othello life maze globe meteor dodge merge marble sand sprout aquarium）と明るさ（0〜3）
adb shell am broadcast -a com.dotpopart.app.debug.SET_TOY -p com.dotpopart.app --es toy clock --ei brightness 2
# 明るさの下限を測る縦線（実効値）。消すときは off
adb shell am broadcast -a com.dotpopart.app.debug.SET_TOY -p com.dotpopart.app --es pattern 16,32,48,64,96,128
# アプリ連動のアニメーション（cueApp は AppCueTargets の id。省くと vwfndr）。on／off／auto（元に戻す）
adb shell am broadcast -a com.dotpopart.app.debug.SET_TOY -p com.dotpopart.app --es cue on --es cueApp maps
# 伏せたまま、画面を上にして置いたときと同じに止める（電池の減り方の基準を測るとき）。on／off
adb shell am broadcast -a com.dotpopart.app.debug.SET_TOY -p com.dotpopart.app --es pause on
```

確認が終わったら、試験パターンは `off`、アプリ連動は `auto`、止めるのは `off` に戻す。

## 4. LED に届いたコマを見る

```bash
adb logcat -c
# 数秒待ってから
adb logcat -d -v epoch -s NtGlyphServiceImpl:D > <scratchpad>/frames.txt
python tools/device-check/render_frames.py <scratchpad>/frames.txt 10
```

最後の数字は「何コマおきに出すか」。各コマの見出しに、点いているマスの数（`lit`）と最大値（`max`）が出る。値は送った値にシステムの倍率をかけたあとのもの（0〜4095）。

`render_frames.py` は 13×13（Phone (4a) Pro）の形しか知らない。Phone (3)（25×25）は実機で確かめていない。

コマが1つも出ないときは、置き方（2）、画面がついたか（1）、Glyph インターフェースの選択（1）の順に疑う。
