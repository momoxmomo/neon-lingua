# 開発者向け:ビルド方法とコードの構成

YouTube(ライブ放送を含む)の英語音声を端末内で文字起こし(Vosk)して日本語に翻訳し、
サイバーパンク風の字幕で表示するAndroidアプリです。Android 10以降で動きます。

- 文字起こし:Vosk(端末内・無料)
- 翻訳:まず ML Kit(端末内・無料)で訳し、続いて Gemini → DeepL の順に、より自然な口語訳へ差し替えます
  (どちらのAPIも設定画面でオン/オフできます。両方オフのときは ML Kit の訳だけを使います)
- 表示:原文が半角カタカナのノイズを経て訳文に変わる「解読」演出。新しい行が上に来ます

## 使い方
1. アプリを開いて、次のどちらかを押す
   - 「▶ 開始(字幕ウィンドウ)」:YouTubeアプリの上に字幕ウィンドウを重ねて表示する
   - 「▶ 開始(アプリ内YouTube)」:上にYouTube、下に字幕を並べた1画面で使う
2. 求められた権限(他のアプリの上に重ねて表示・マイク・通知・画面共有)を許可する
3. YouTubeで動画やライブを再生すると、字幕が表示される
4. 字幕の下にある操作バーで、文字サイズ(A− / A＋)、設定(⚙)、最小化(▁)、終了(■)ができる
   - 下の「つまみ」を上下にドラッグすると、ウィンドウの高さを変えられる
   - 最小化した字幕は、小さなチップをタップすると元に戻る

翻訳モデル(約30MB)は初回だけダウンロードするので、そのときはネット接続が必要です。

## 詳細設定(⚙ 設定)
- 表示:テーマ(サイバーパンク / レトロ端末 / マトリックス / 攻殻機動隊)、文字サイズ、字幕ウィンドウの高さ、走査線ノイズのオン/オフ
- 翻訳API:Gemini と DeepL それぞれの「使う/使わない」とAPIキー
  (キーは最初は空欄。入力欄の下のリンクから無料で取得できる。入力したキーはアプリを更新しても残る)
- 字幕の色:色を変える場所をプルダウンで選んでから、色をタップする

## 公開用APKの作り方(リリース版)
1. Android Studio の Build > Select Build Variant… で、app を「release」にする
2. Build > Generate App Bundles or APKs > Generate APKs
3. デスクトップに `NeonLingua-v(バージョン).apk` ができる(公開用の署名鍵で署名済み)
4. 確認用に戻すときは、Build Variant を「debug」に戻す(デスクトップの `LiveTranslator.apk` になる)

更新版を出すときは、app/build.gradle.kts の versionCode を1つ増やし、versionName を変える。
署名鍵は `signing` フォルダにある。これをなくすと更新版を出せないので、必ず別の場所にもバックアップする。

## ほかの端末へのインストール
APKをスマホにコピーしてインストールする。
「他のアプリの上に重ねて表示」が許可できないときは、アプリ情報の右上「⋮」から「制限付き設定を許可」を選ぶ。

## ビルド方法(Android Studio)
1. 初回だけ、このフォルダで get-model.ps1(Windows)または get-model.sh(Linux)を実行し、音声認識モデルを配置する
2. Android Studioでこのフォルダを開く
3. メニューの Build > Generate App Bundles or APKs > Generate APKs
4. できたAPKは自動でデスクトップに `LiveTranslator.apk` としてコピーされる
5. USBでつないだ端末に直接入れるときは、実行ボタン(▶)を押す

GitHub Actions(.github/workflows/build.yml)でも自動ビルドできます。

## ファイルの役割(app/src/main/java/com/example/livetranslator)
| ファイル | 役割 |
|---|---|
| MainActivity.kt | 最初の画面(開始・終了・詳細設定) |
| SettingsActivity.kt | 詳細設定画面 |
| PlayerActivity.kt | アプリ内YouTubeモード(上にYouTube、下に字幕) |
| CaptionService.kt | 音声の取り込み・文字起こし・翻訳・字幕ウィンドウの表示 |
| CyberDecode.kt | 原文が訳文に変わる「解読」演出 |
| NoiseView.kt | 変換中の行に重ねる走査線ノイズ |
| CyberUi.kt | 各画面で共通のボタン・スライダー・プルダウンなど |
| Theme.kt | テーマと字幕の色 |
| Prefs.kt | 設定の保存場所と項目名 |
| GeminiClient.kt / DeepLClient.kt | 各翻訳APIの呼び出し |

## 注意
APIキーはアプリ内(端末)にだけ保存され、ソースコードやAPKには含まれません。
アプリを上書き更新すると入力済みの設定は引き継がれますが、アンインストールすると消えます。
上書き更新するには、前と同じPC(同じ署名)でビルドしたAPKを使ってください。
