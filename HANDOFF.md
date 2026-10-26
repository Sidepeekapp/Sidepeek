# Sidepeek 交接筆記（給下一位協助者）

Android App「Sidepeek」：LINE 通知依聊天室整理（只接管 LINE），加上螢幕邊緣「Peek 條」往內滑打開的側邊欄（可收任何使用者勾選的 App）。
全部在手機上處理，無伺服器。GitHub：github.com/jaydenluuuu-sys/sidepeek（GPL-3.0；「Sidepeek」名稱與圖示不在授權內）。

## 基本
- 套件名 `app.sidepeek`，minSdk 26，compileSdk/targetSdk 36，AGP 8.11.2、Gradle 8.13、Kotlin 1.9.24。
- 顯示版本固定 **1.0**（使用者要求）；versionCode 目前 3，之後發新版要遞增。
- 正式版：根目錄放 `keystore.properties`（不進版控，簽章金鑰在使用者手上）→ `./gradlew :app:assembleRelease` / `:app:bundleRelease`。R8 開啟。
- 聯絡信箱 sidepeek.app@gmail.com（App 內「意見回饋」會自動帶手機資訊）。
- 測試：`./gradlew :app:testDebugUnitTest`（LinkExtractorTest、ProfileLinksTest）。

## 主要檔案
- `LineListenerService.kt`：讀通知、去重、LINE 接管（重發整理後通知並隱藏原通知）。
- `MessageStore.kt`：訊息儲存（最多 200 則）、去重規則、`repeat` 次數（同一通知重發）。
- `Notifier.kt`：整理後通知（MessagingStyle、捷徑、群組頭貼）。
- `PanelController.kt` / `PanelAccessibilityService.kt`：Peek 條與側邊欄（協助工具 overlay）。
- `MainActivity.kt`：主畫面、設定引導（8 步，第 1/4/5/6 步是 res/raw 示範影片）、各品牌背景說明（`OemGuide.kt`）。
- `A11yDisclosure.kt`：開協助工具前的顯著揭露同意畫面（Play 政策要求）。
- `PrivacyActivity.kt`、`docs/privacy.md`：隱私權政策。
- `tools/guide-art/gen.py`（說明圖）、`tools/guide-video/make.py`（示範影片剪輯）。

## 使用者偏好
- 繁體中文溝通；改完直接給簽好的 APK。
- 不要把使用者本名、公司資訊放進程式碼或公開內容。
- 不做 LINE 商標仿冒外觀（不用 LINE 綠 #06C755、不用 LINE 對話框造型）。品牌綠 #16A34A。
- 說「Peek 條」（邊緣把手）、「側邊欄」；三星的叫「側拉標籤」「側螢幕面板」。

## 最近修的（今天 10/26）
- bubble（com.dearu.bubble.starship）每則訊息都用同一個通知 key 重發「有新消息。」，只有 `when` 變：
  在「skip unchanged update」那關依 `when` 是否前進 ≥5 秒累加 `repeat`；也比對已儲存的最新卡片。
  側邊欄在泡泡下方顯示綠色「共 N 則」（原本放在時間後面，雙位數時被擠掉）。
- Peek 條自己消失：協助工具模式下只用 getWindows() 判斷鍵盤（handle 視窗 insets 會殘留「鍵盤開著」）。
- 群組通知的標題頭貼原本用最新發言者的照片 → 改成綠底白字的群組字首圖示。

## 10/27 修的（v1.0.2，versionCode 3，尚未實機測試）
- 不能發通知時不再藏 LINE 通知：`Notifier.canRepost()`（App 通知、「LINE 訊息預覽」類別、類別群組都要開著）不成立時，
  `hideWanted()` 回傳 false，LINE 原通知保留；主畫面狀態卡顯示警告＋「打開通知設定」。恢復後由 30 秒健康檢查補發。
- 鎖定畫面：「鎖定畫面也顯示 Peek 條」預設改為關閉；側邊欄在鎖定時一律不開，開著此選項時往內滑會開
  `UnlockPanelActivity` 要求解鎖，解鎖後才走 `OpenPanelActivity` 打開側邊欄。側邊欄視窗拿掉 FLAG_SHOW_WHEN_LOCKED，
  開著時若變成鎖定會自動關閉。
- 貼圖下載：只連 `*.line-scdn.net`、`*.line-cdn.net`、`*.line.naver.jp` 的 https；不掃訊息文字／名稱欄位；
  轉址最多 3 次且必須仍是 LINE 網域；單張上限 2 MB；解碼超過 1024px 會縮小；User-Agent 改為 Sidepeek。
  隱私權政策同步更新（10/27）。

## 待確認 / 可做
- 上述三項尚未在實機長時間驗證；若有問題請使用者匯出「遇到問題 → 匯出診斷記錄」。
- 群組通知「有點擠」是 One UI 的 MessagingStyle 排版，App 無法調整行距；可考慮減少顯示則數。
- 之後：Google Play 上架（12 人 14 天封閉測試；招募表單已在 README）、GitHub Actions 自動發版、App 內檢查更新。
