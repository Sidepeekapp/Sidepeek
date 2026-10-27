# Sidepeek 交接筆記（給下一位協助者）

Android App「Sidepeek」：LINE 通知依聊天室整理（只接管 LINE），加上螢幕邊緣「Peek 條」往內滑打開的側邊欄（可收任何使用者勾選的 App）。
全部在手機上處理，無伺服器。GitHub：github.com/jaydenluuuu-sys/sidepeek（GPL-3.0；「Sidepeek」名稱與圖示不在授權內）。

## 基本
- 套件名 `app.sidepeek`，minSdk 26，compileSdk/targetSdk 36，AGP 8.11.2、Gradle 8.13、Kotlin 1.9.24。
- 顯示版本固定 **1.0**（使用者要求）；versionCode 目前 6，之後發新版要遞增。
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

## 10/27 下午（versionCode 4）
- 側邊欄標題列：「最近訊息」後面接灰色則數，右邊只留低調的「清除」；拿掉「關閉」（往外滑、點外面、返回鍵、
  標題列雙擊都能關）。
- 「清除」不跳確認：卡片依序飛出後出現「已清除 N 則・復原」5 秒；期間按鈕隱藏。復原期結束（或側邊欄關閉）才
  `finalizeClear`：刪圖片／頭像／聊天室頭貼（`MessageStore.purgeFiles`）、清診斷記錄、更新 LINE 整理通知。
  單則滑掉也改用 `purgeFiles`（連頭像一起清）。
- 小修：`OpenChatActivity` 改 exported=false（捷徑以本 App 身分啟動，應不受影響，需實機確認）；`hiddenAt`
  加鎖；意見回饋與診斷記錄顯示「1.0 (versionCode)」。
- CI：`.github/workflows/build.yml` 每次推 main 建置未簽名 APK＋單元測試，放草稿 Release「CI build <sha>」，
  附 apksigner.jar；舊的 CI 草稿會自動刪除。失敗時記錄放「CI failed <sha>」草稿。

## 10/27 versionCode 5
- 效能：`MessageStore.loadBitmap` 加 LruCache（鍵含路徑＋修改時間＋大小，檔案改寫或刪除不會用到舊圖）；
  側邊欄收到連續的訊息更新時，120ms 內只重建一次列表。
- 省電：無障礙模式每秒一次的前景檢查只在「只在桌面顯示 Peek 條」開著且螢幕亮著時才跑，否則自己停，
  下一個無障礙事件（亮螢幕、切 App）再接上。
- 簽名：GitHub Secrets 已設好（SIGNING_KEYSTORE_BASE64 分三段＋密碼＋別名），CI 會直接產出簽好的
  sidepeek-1.0-buildN.apk，並核對憑證指紋。

## 10/27 versionCode 6
- 設定精靈：側邊欄四步（打開側邊欄／試開服務／受限設定／再開一次）都有「稍後再設定側邊欄」→ 直接到電池那步。
  `Prefs.KEY_A11Y_LATER` 記住，主畫面狀態卡顯示「繼續設定側邊欄」（`resumeA11ySetup` 重開精靈）；服務開好自動清掉。
  通話中（AudioManager MODE_IN_CALL / IN_COMMUNICATION）在無障礙相關步驟顯示「Android 通話中不允許…」。
- 診斷記錄：平常 `record()` 只寫一行事件；按「開始收集完整記錄」後 24 小時（`Diagnostics.full()`）才寫完整
  extras 與含文字的細節（`Diagnostics.sensitive()`）。
- 長網址顯示：`LinkExtractor.shortLabel` / `shortenInText`（instagram.com/reel/…），通知與側邊欄泡泡用短版；
  連結按鈕與「開啟連結」仍開完整網址。單元測試已補。
- 檢查更新：`UpdateChecker` 打開 App 時最多每 20 小時查一次 GitHub releases/latest，APK 檔名的 buildN 大於
  versionCode 就在狀態卡提示；「遇到問題」裡可關閉。
- 一鍵發版：Actions → Build APK → Run workflow，填版本號（例如 1.0.3）就會用簽好的 APK 發布正式 Release
  `vX`，檔名 sidepeek-vX-buildN.apk（UpdateChecker 靠 buildN 判斷），更新說明取自上一個 v* tag 之後的 commit。

## 待確認 / 可做
- 上述三項尚未在實機長時間驗證；若有問題請使用者匯出「遇到問題 → 匯出診斷記錄」。
- 群組通知「有點擠」是 One UI 的 MessagingStyle 排版，App 無法調整行距；可考慮減少顯示則數。
- 之後：Google Play 上架（12 人 14 天封閉測試；招募表單已在 README）、GitHub Actions 自動發版、App 內檢查更新。
