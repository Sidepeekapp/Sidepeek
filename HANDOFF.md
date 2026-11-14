# Sidepeek 交接筆記（給下一位協助者）

Android App「Sidepeek」：LINE 通知依聊天室整理（只接管 LINE），加上螢幕邊緣「Peek 條」往內滑打開的側邊欄（可收任何使用者勾選的 App）。
全部在手機上處理，無伺服器。GitHub：github.com/Sidepeekapp/sidepeek（GPL-3.0；「Sidepeek」名稱與圖示不在授權內）。

## ⭐ 目前進度（2026-11-14 交接，先讀這段）

**狀態**：分支 `feature/v1.2-reliability-widget`，下面「下一版」那一大段的改動已 commit 成 `54a5874`，語言頁提示、github／play flavor、Play 版關掉檢查更新已 commit 成 `ee5a99b`，Play 上架草稿（`store/`）也已 commit；全部都在本機，**還沒 push**。版本號還是 1.1.1／versionCode 13，升 1.2.0／14 還沒得到使用者同意。改動前的備份都在 repo 外的 `D:\projects\sidepeek-backups\<名稱>-日期\`。

**等使用者回覆／決定的事**
1. 新的通透液態玻璃分頁列（`GlassTabBar`）在手機上是否 OK、跟使用者的 Launcher 比夠不夠像；字看不清楚的話可加陰影。
2. 「Peek 條樣式」頁的手機＋放大鏡預覽（`SettingPreview`）是否 OK。
3. ~~語言頁提示~~ 已改（11/13，未實機看過）：`lang_hint` 只寫「設定步驟裡的操作示範影片目前只有中文」（四語）。之後若把 `res/raw/guide_*.mp4` 做成多語言，就把這句和 activity_main.xml 那個 TextView 一起拿掉。備份在 `D:\projects\sidepeek-backups\before-lang-hint-20261113\`。
4. Google Play 上架：使用者正在註冊 Play Console 個人帳號（非交易商、開發者名稱用 Sidepeek）。新個人帳號要先 12 人封閉測試 14 天。Play App Signing 建議上傳現有金鑰，GitHub 版才能直接更新。
   - **11/13 權限檢查結果**：①`UpdateChecker`（提示去 GitHub 下載 APK）可能違反「只能經 Play 更新」→ **已改（11/14，做法 A）**：`BuildConfig.SELF_UPDATE_CHECK`（github true／play false），`UpdateChecker.enabled()` 在 Play 版永遠 false（不連 GitHub、首頁不出現「前往下載新版」）；設定清單 `settingsItems` 濾掉 `cardOther`「檢查更新」，`setupSettingsPages` 把卡片設 GONE。使用者知道別的 App 用 Google In-App Updates，選擇先不加（做法 B 以後再說）。隱私權政策（四語 `strings_privacy.xml`、`docs/privacy.md`）改成「只有從 GitHub 下載的版本會查詢，Play 版由商店更新」，順便修正關閉位置為「設定 → 檢查更新」，最後更新日改 2026/11/14；網站要 push 後才會更新。備份在 `D:\projects\sidepeek-backups\before-play-update-off-20261114\`。②電池直接請求 → **已改**（見下）。③無障礙要填聲明＋影片（揭露畫面 → 同意 → 不同意與如何再叫出 → 核心功能）；要先解釋 `flagRequestFilterKeyEvents` 只攔返回鍵、`canRetrieveWindowContent` 只為進階清理讀 LINE 聊天室名稱和判斷前景。④`EdgePanelService` specialUse 前景服務要填聲明＋影片，保留（不想開無障礙的替代方案）。⑤沒問題：`<queries>` LAUNCHER（不是 QUERY_ALL_PACKAGES）、使用情況、上層顯示、targetSdk 36；資料安全可填「不收集、不分享」（要隱私權政策網址）；商店標題和圖示不能用 LINE。
   - **github／play 兩個 flavor（11/13，未實機測試）**：`app/build.gradle.kts` 加 `store` 維度。`BuildConfig.DIRECT_BATTERY_REQUEST`：github＝true（照舊直接跳系統視窗），play＝false（`MainActivity.requestBatteryExemption` 打開 App 資訊頁＋Toast `toast_battery_app_info`「在這頁點「電池」，選「不受限制」」）。`src/play/AndroidManifest.xml` 用 `tools:node="remove"` 拿掉 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`（合併後 manifest 已確認）。CI（build.yml）改成 `testGithubDebugUnitTest`、`assembleGithubRelease`、`apk/github/release/`、`app-github-release-unsigned.apk`，還沒在 GitHub 上跑過。README 的建置指令（`assembleDebug`／`bundleRelease`／`assembleRelease` 現在會兩個 flavor 都編）還沒改。之後 Play 版要關的東西（例如 UpdateChecker）照這個模式加 buildConfigField。備份在 `D:\projects\sidepeek-backups\before-play-battery-20261113\`。
   - **上架草稿（11/14）**：`store/play-console-draft.md`＝無障礙 API 聲明、specialUse 前景服務聲明、資料安全（填「不收集、不分享」）、兩支影片的拍攝步驟、其他 Console 欄位，要貼的文字用英文。`store/play-listing-draft.md`＝四語名稱／簡短說明／完整說明（字數都在 30／80／4000 內，node 算過）、圖片規格、6 張截圖計畫（只能用假資料）。日、韓文是初稿，最好請母語者看過。名稱、圖示不能有 LINE、三星商標或 LINE 綠。
   - 還沒做：主題圖片（1024×500）和截圖、錄兩支聲明影片並上傳 YouTube「不公開」、網站 privacy.html 要 push 到 main 才會更新。
5. LINE 未接來電轉發（語音、視訊）還沒實機驗證；回覆測試者 Alina 的信由使用者自己寄。
6. 網站、README 還沒同步多語言、常見問題新內容。

**工作規則（使用者明確要求，務必遵守）**
- 用繁體中文回覆。使用者常用手機遠端操作：每次結尾要完整說明做了什麼、什麼沒驗證，最後一個問題加編號選項。
- 「澄清不是同意」：使用者只是提問或描述時，先提案、等明確的「好／1」才改程式。
- **push 到 GitHub 前一定要先問**；commit 也要使用者要求才做。commit 的 author 和 committer 都用 `Sidepeekapp <sidepeek.app@gmail.com>`，不要加 Co-Authored-By。
- 不要把使用者本名、公司資訊放進程式碼或公開內容。
- 不用 LINE 綠 #06C755 或 LINE 對話框造型；品牌綠 #16A34A。用詞「Peek 條」「側邊欄」。
- 簽章密碼由使用者自己填進 `keystore.properties`，不要印出、寫進 commit 或傳出去。
- 不用 python；用 node、sed 或編輯工具。改動前先備份到 `D:\projects\sidepeek-backups\`。
- 不代寄信，只給草稿。改完要建置並把 APK 傳給使用者測（`./gradlew assembleGithubDebug`；要測 Play 版行為用 `assemblePlayDebug`）。
- 使用者的手機：Samsung SM-S9480、One UI、Android 17。

**多語言注意**：字串在 `values/`（英文預設）、`values-zh-rTW/`（不能用 `values-zh`，繁中手機會退回英文）、`values-ja/`、`values-ko/`，各四個 `strings*.xml`。新增文字要四種語言都加，`node tools/i18n-check.js` 檢查是否齊全。

## 基本
- 套件名 `app.sidepeek`，minSdk 26，compileSdk/targetSdk 36，AGP 8.11.2、Gradle 8.13、Kotlin 1.9.24。
- 版本名稱跟著發布版本走（目前 1.1.1，前一版 1.1.0）；versionCode 目前 13，每次發新版都要遞增。發版時 Run workflow 填的版本號要和 versionName 一致。
- 正式版：根目錄放 `keystore.properties`（不進版控，簽章金鑰在使用者手上）→ `./gradlew :app:assembleGithubRelease`（GitHub 用 APK）／ `:app:bundlePlayRelease`（Play 上架 .aab）。R8 開啟。
- 聯絡信箱 sidepeek.app@gmail.com（App 內「意見回饋」會自動帶手機資訊）。
- 測試：`./gradlew :app:testGithubDebugUnitTest`（LinkExtractorTest、ProfileLinksTest、ConversationTitlesTest、UpdateCheckerTest）。

## 主要檔案
- `LineListenerService.kt`：讀通知、去重、LINE 接管（重發整理後通知並隱藏原通知）。
- `MessageStore.kt`：訊息儲存（最多 200 則）、去重規則、`repeat` 次數（同一通知重發）。
- `Notifier.kt`：整理後通知（MessagingStyle、捷徑、群組頭貼）。
- `PanelController.kt` / `PanelAccessibilityService.kt`：Peek 條與側邊欄（協助工具 overlay）。
- `MainActivity.kt`：主畫面、設定引導（8 步，第 1/4/5/6 步是 res/raw 示範影片，由 tools/guide-video/animated 產生）、各品牌背景說明（`OemGuide.kt`）。
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

## 10/27 晚上 v1.0.4（versionCode 11）
- 合併外部 PR #1（bingxyz）：LINE 通知沒有 conversationTitle 時，改用 android.subText 當聊天室名稱（ConversationTitles.kt），
  subText 與 title 不同就視為群組；MessageStore.updateChatTitle 會把同一 chatKey 的舊紀錄改成正確名稱。
  使用者實際遇到的是 LINE 社群（OpenChat）：一般群組有 conversationTitle，社群沒有，側邊欄顯示成發言者。
- 待實機確認：一對一聊天含自己傳的訊息時會不會被判成群組；LINE 錢包等一般通知若有 subText 會不會被當成名稱。
- 發版說明改成取 README「### v版本號」那一段（相對連結換成完整網址），找不到才列 commit 標題；發新版前先在 README 寫好這段。

## 10/28 v1.0.5（versionCode 12）
- Peek 條不透明度最低改成 0%（`Prefs.OPACITY_MIN`）：看不到、截圖不會拍到，但視窗仍可觸控，往內滑照樣打開；按下時 bar 會短暫變不透明當回饋。
  Android 沒有讓 overlay 只在截圖時隱藏的 API（FLAG_SECURE 會變黑塊或擋截圖、截圖偵測只限 Activity 且是事後通知），所以用 0% 解決。
- 本機可直接編簽好的正式版（不必等 CI）：根目錄 `keystore.properties` + `sidepeek-release.jks`，`./gradlew :app:testGithubDebugUnitTest :app:assembleGithubRelease`。
- 側邊欄列表原本只建最新 50 則（標題卻顯示全部則數）：改成開啟時 50 則（`LIST_PAGE`），捲到離底部約一個畫面時
  `loadMoreMessages()` 往後接 50 則，底部另有「再載入 N 則」可點；新訊息觸發的 refreshList 保留已載入數量。
  「清除」的飛出動畫最多錯開 15 張（`CLEAR_STAGGER_MAX`），其餘一起飛出。
- 設定精靈第 1 步：原生 Android（華碩 Zenfone 9 Android 14、Pixel）連通知存取也受「受限制的設定」管（三星只擋無障礙）。
  開過通知存取設定（`Prefs.KEY_LISTENER_TRY_OPENED_V1`）回來仍未授權時，第 1 步改顯示受限設定說明＋「到 App 資訊解除限制」
  （第二個按鈕）＋新動畫 `res/raw/guide_step1r.mp4`（tools/guide-video/animated 的 step1r，原生 Android 畫面）。
  也提醒關掉「暫停未使用的應用程式活動」。無障礙步驟提示加上「捷徑不用開」（使用者開了會出現系統的協助工具浮動按鈕）。
- 點頭像開個人頁：Threads 新寫法「handle 已追蹤你／已轉發」原本比不到，`ProfileLinks.LEADING` 允許動詞前的「已」；
  Threads 連結改用 threads.com（threads.net 會轉址）。測試已補。
- Peek 條觸控範圍：原本左右各加 18dp，inset 0 時 36dp 全在畫面內側，會擋到 LINE 貼圖面板。改成外側最多 18dp（只用 inset 的空間）、
  內側 `innerTouchPad()` 10dp。
- 音量下鍵（a11y onKeyEvent，不攔截）→ `PanelController.hideHandleForScreenshot()` 立刻隱藏 2.5 秒（`Prefs.KEY_HIDE_ON_VOLUME`，預設開）。
  手掌滑動等截圖方式偵測不到，0% 不透明度是保底。
- 打開 LINE 清通知：a11y 收到 LINE 的 Activity 切換（進 LINE、列表→聊天室）時 `Notifier.clearLineChats()`：
  所有 LINE 整理通知 markRead + cancel（`Prefs.KEY_CLEAR_ON_LINE_OPEN`，預設開）。原因：LINE 原通知已被我們收掉，LINE 讀完時沒東西可取消，
  我們收不到已讀訊號。只清通知，不讀 LINE 畫面內容；沒開側邊欄服務就不會動作。
- 無障礙說明（strings a11y_description、A11yDisclosure）與隱私權政策（PrivacyActivity、docs/privacy.md，10/28）都加上音量鍵與打開 LINE 清通知。
  已同意過的使用者不會再看到同意畫面（KEY_A11Y_CONSENT_V1 沒改）。
- 滑掉的通知 30 秒後被補發回來（S26U Android 17 回報，診斷記錄證實 DISMISS 後 RECOVER health-30s 反覆出現）：
  Android 16+ 把我們「一聊天室一組」的通知疊成一疊，整疊滑掉時子通知的 reason 應是 REASON_GROUP_SUMMARY_CANCELED（推論，新的 OWN removed 記錄可證實），
  原本只認 CLICK/CANCEL/CANCEL_ALL 才 markRead，聊天室仍算未讀 → `recoverOrphans` 補發，而 `unreadAfterWatermark` 又 fallback 最後一則。
  修正：GROUP_SUMMARY_CANCELED 也算使用者滑掉；`recoverOrphans` 只補 watermark 之後的未讀；自己通知被移除時記 `OWN removed reason=…`。
- Threads 多帳號：私訊的 conversationTitle 是「收到訊息的帳號」，側邊欄標題變成自己的帳號、寄件人才是對方。
  `conversationTitleOf` 在非 LINE 的通知中，conversationTitle 等於自己（MessagingStyle user / EXTRA_SELF_DISPLAY_NAME）
  或明確標示 isGroupConversation=false 時忽略它，標題改用最後一則訊息的 person 名稱；chatKey 也不再用這個標題。
  推論自截圖，尚無完整記錄；診斷記錄會記 `account-label title dropped`，請用「完整記錄」收一則確認。
- 通知震動太弱（Threads 回報，想跟原生 LINE 一樣明顯；Samsung 類別頁只有震動開關、不能選模式）：
  通知類別建立後震動模式就改不了，所以 `CHANNEL_MSG` 換成新 id `line_preview_messages_v4`，震動 `0,330,160,100,80,140`（一長兩短，從 LINE 26.16 類別讀到的數值，診斷記錄 `vib=`）。
  v2、v3 只出現在測試版。第一次建立時複製舊類別（依序找 v3、v2、`line_preview_messages`）的重要性、震動開關、聲音、圖示標記、鎖定畫面設定，再刪掉舊類別（設定裡會顯示「已刪除 1 個類別」）。
  LINE 自己的震動模式還不知道：`LINE ch=… vib=…` 會記下來，診斷記錄開頭也會印 Sidepeek 類別的震動狀態，之後可依實際記錄調整。

## 10/29 進階清理（實驗性）＋版面整理（仍是 v1.0.5，versionCode 12，尚未實機測試）
- `Prefs.KEY_SMART_CLEAR`＋`KEY_SMART_CLEAR_CONSENT_V1`：預設關閉；每次打開都跳 `A11yDisclosure.smartClear` 同意框。
- `PanelAccessibilityService.smartClear`：LINE 畫面切換後 400／1000／2000ms 讀 active window 上方 15% 的 text／contentDescription（只限 LINE 套件），交給 `ChatTitleMatch.match` 跟 `Notifier.activeLineChatTitles` 比對，唯一符合才 `clearLineChat`；不符合就不清。記錄只寫 matched／no match，完整記錄才寫文字。
- `ChatTitleMatch`：NFKC、空白、去掉「(5)」人數、「…」截斷用開頭比對（至少 4 字）；有單元測試。
- 只在 `ChatHistoryActivity`（LINE 26.16 聊天室畫面，記錄確認）比對；其他畫面記 `smart clear skipped`。聊天室間若是同一個 activity 內直接切換，目前不會重新比對。
- 截圖：三星 One UI（Android 17）會扣住音量下約 150ms 等電源鍵，截圖組合鍵時不會傳給無障礙服務（記錄只有單按那次 `KEY volume-down lag=152ms`），所以電源＋音量下截圖時藏不掉 Peek 條。
  → 「按音量下鍵時暫時隱藏 Peek 條」整個拿掉（v1.0.5 還沒發布，沒有舊設定要處理）。要截圖不含 Peek 條，可以考慮快速設定按鈕＋`performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)`，使用者先不要。
- 版面：新增「LINE 通知清理」卡片（swClearOnLine 從「側邊欄：顯示方式」移過來）；進階／常見問題／遇到問題改成 `CollapseCard` 卡片，`lblSupport` 標題。
- 隱私文字（A11yDisclosure、a11y_description、PrivacyActivity、docs/privacy.md、README）已改成「預設不讀取；進階清理例外」，最後更新 2026/10/29。上架前要更新 Play Console 無障礙聲明。
- 滑動靈敏度：`Prefs.KEY_SWIPE_SENS` 1..5（預設 3），對應往內滑 48／36／24／16／10dp 才打開（原本固定 24dp）；`PanelController` 在 ACTION_DOWN 讀取。設定頁在「Peek 條外觀」不透明度下面，「Peek 條恢復預設」會一起重設。
- **設定頁分頁**：activity_main.xml 根節點改成 LinearLayout（ScrollView `scroll` ＋ BottomNavigationView `bottomNav`）。內容分成 `pageHome`／`pageSettings`／`pageHelp`，由 MainActivity.setupTabs()/showPages() 切換，分頁存在 savedInstanceState（`main.tab`）。設定精靈進行中時（!wizardDone 且沒有 showAll）隱藏分頁列、固定在首頁。
- **狀態卡＋主開關**：主開關（swEnabled、swEdge、a11yBox、測試按鈕）搬進狀態卡的 `mainSwitches`，精靈中隱藏。`statusDot` 顏色：灰＝已關閉或精靈中、橘＝還有建議設定、綠＝一切正常；`statusSub` 是一行補充說明。
- **其他**：選項是 RadioGroup 套 `SegGroup`/`Seg` 樣式（程式不變）；滑桿標籤 `SeekRow`，bindSeek 把 label 文字以「：」切成名稱／數值兩格；常見問題卡片 `android:tag="faq"`，setupFaqAccordion() 逐題展開。


## 10/29 版號改成 v1.1.0
- 原本規劃的 v1.0.5 沒有發布，因為新功能和介面改版較多，改名為 **v1.1.0**（versionName 1.1.0，versionCode 仍是 12）。上面標成 v1.0.5 的內容都屬於 v1.1.0。
- 更新檢查比對 tag 版號，舊版（1.0.3、1.0.4）會直接提示 v1.1.0。發版用 Actions → Build APK → Run workflow 填 `1.1.0`，會建 tag `v1.1.0`、檔名 `sidepeek-v1.1.0.apk`，更新說明取自 README 的「

## 11/1 v1.1.1（versionCode 13，已發布）
以下修正都已合併到 main 並發布。除了貼圖文字列，都已實機測過。單元測試和 debug 建置已通過。
- **貼圖和圖片通知補文字**（取自 PR #6 的第一部分）：SystemUI 只畫 data message 的圖片，所以文字另外加一列，`（圖片）` 佔位文字除外。PR #6 的共用群組和摘要通知沒有收：S26 Ultra 上逐則滑掉都沒有連帶清掉其他聊天室，PR 已由作者關閉。發布時還沒有實機測試結果，要確認文字有顯示、數字沒有多算。
- **LINE 聊天室被 snooze 24 小時**（PR #4）：同一聊天室共用一個通知 key，snooze 後新訊息會被系統吞掉。修法：拿掉 snooze，改成再取消一次，取消不掉就交給後續補掃。舊版留下的 snooze：`releaseOldSnoozes()` 在連上通知存取後執行一次，把 LINE 的 snooze 改成 1 秒後到期；一般 listener 沒有 unsnooze 可用，這是依 AOSP 原始碼推得的做法，S26 Ultra 實測紀錄有 `SNOOZE released 1`，沒有副作用。確認方式：看紀錄的 `SNOOZE released`，卡住的聊天室要恢復收到新訊息；恢復時舊通知可能再響一次。
- **貼圖彈出通知閃一下就消失**：貼圖下載後用 `setSilent(true)` 更新，系統會把彈出中的通知收回。修法：貼圖更新改用 `refreshChatSilently(keepHeadsUp = true)`，只設 `setOnlyAlertOnce`。其他靜音路徑不變。實測：連傳兩次同一張貼圖，彈出通知要留著且不重複響鈴；看紀錄的 `alertOnce=true`。
- **同一批傳多張貼圖只有第一張**：LINE 只發一則通知，Sidepeek 收不到其他張，不用修。
- 下面兩項的實測：關掉側邊欄後打開 LINE，通知要被清掉；「只在桌面顯示 Peek 條」開啟時，進 LINE 要隱藏，回桌面要出現，在桌面收到 LINE 通知時不能閃掉。
- **側邊欄開關不應該影響 LINE 通知清理**：無障礙服務開著、但 App 裡的側邊欄開關關掉時，「打開 LINE 時清掉通知」和進階清理都不會動作。原因是 `PanelAccessibilityService.kt:97` 的 `val c = controller ?: return`，關掉側邊欄時 `controller` 是 null，後面的 `onLineScreen` 就不會執行。修法：偵測 LINE 畫面這段改成不依賴 `controller`，只有 Peek 條、側邊欄相關的處理才需要它。無障礙服務仍然是清理的前提。
- **「只在桌面顯示 Peek 條」時，Peek 條在 LINE 裡不會消失**（從 1.0 就有，跟清理無關；其他 App 正常）：`PanelController.kt:175` 的 `IGNORED_FG` 把 LINE、LINE Lite 列為不算離開桌面，`onForegroundApp`（`:291`）遇到 LINE 直接 return，`foregroundPkg` 還停在桌面；`:391` 的讀取使用情況路徑也一樣。修法：把 LINE 從 `IGNORED_FG` 拿掉。當初加的原因是怕 LINE 的頂部通知或 App 內通知搶走前景判斷，修完要實測：在桌面收到 LINE 通知時，Peek 條不能閃掉；進 LINE 時要隱藏。

## 下一版（開發中，分支 `feature/v1.2-reliability-widget`，尚未實機測試）
- **第一次取消沒成功不是真的失敗**：診斷記錄 36 次 `cancel did not stick` 有 35 次下一步就是 `already gone`，另 1 次再取消一次，所以 `cancelNotification` 只是還沒執行完。這解釋了 v1.1.0 為什麼常走到 snooze，重試邏輯不用改。
- **長震寫進說明**：App 和網站都改成到 LINE 的應用程式資訊 → 通知 → 通知類別 → 訊息提醒，選「靜音」。不能關總開關「聲音和震動」，實測來電會不跳；也不能關「訊息提醒」的「允許通知」，否則 Sidepeek 收不到訊息。沒有放首頁提醒，因為 LINE 震動通常開著，幾乎人人都會看到，長震和 LINE 震動有關也仍是推論。
- **常見問題補上 60 秒不彈**：App 和網站都有。
- **固定捷徑「開啟側邊欄」**：`res/xml/shortcuts.xml` 接 `OpenPanelActivity`，三星側螢幕面板的捷徑清單可以選。`targetPackage` 寫死 `app.sidepeek`；debug 版另有 `src/debug/res/xml/shortcuts.xml` 指向 `app.sidepeek.test`（原本 debug 版的捷徑指到正式版的不公開 Activity，點了沒反應）。實測：加進側螢幕面板後點一下會開側邊欄。首頁「側邊欄」開關關著時，從捷徑、快速設定磚開側邊欄不會帶出 Peek 條，關閉約 0.7 秒後會停掉服務。
- **桌面小工具「最近訊息」**：`MessagesWidget.kt`，內容和側邊欄相同（共用 `PanelRows`：同樣的來源篩選、排序、頭像、時間），固定深色，有 App 標籤、發話者、氣泡、圖片、貼圖占位和「共 N 則」，字級與行數跟著側邊欄設定。點一列開該訊息，點頭像開個人頁面，點標題開側邊欄。沒有連結按鈕和「清除」。內容會直接顯示在桌面，只有用戶自己加小工具才會出現。實測：淺色與深色桌面、調整大小、程序被殺後重新載入、點訊息開對聊天室。
- **設定三個分頁重新分類**：首頁只放狀態與安裝（備用方式「顯示在其他 App 上層」移進側邊欄服務的區塊，只在服務沒開時出現）；設定依序是通知外觀（含群組頭貼）、LINE 通知清理（從首頁移來）、側邊欄：要收哪些 App、側邊欄：外觀、側邊欄：Peek 條（「只在桌面／打字時隱藏／鎖定畫面」和使用情況權限從已刪除的「側邊欄：顯示方式」移來）、其他開啟方式（快速設定、App 捷徑、小工具）、其他（自動檢查新版本，從說明移來）；說明的小提示改成常見問題的題目，「遇到問題」只留回報與診斷記錄。
- **Peek 條只在直向顯示**：`applyVisibility` 多一個條件，螢幕寬大於高就隱藏，轉向時收 `ACTION_CONFIGURATION_CHANGED` 重新判斷。側邊欄本身不受影響，橫向時從捷徑或快速設定磚仍可開。順帶避開了橫向時 Peek 條位置沿用直向像素、可能掉到畫面外的問題。
- **從快速設定磚、捷徑打開側邊欄會馬上關掉**：側邊欄開關關著時最明顯。記錄是 `BACK/nav fallback: foreground changed to <桌面> while open -> close`，開啟後 0.62～0.70 秒觸發：中繼的 `OpenPanelActivity` 結束、通知欄收起後桌面才回到前景，超過 600ms 保護時間就被當成使用者離開。修法：`PanelController.open()`（Peek 條以外的開啟方式）之後 2 秒內不做這個判斷，切到 Sidepeek 自己也不算離開。
- **側邊欄關著時，看不見的 Peek 條不能被拉開**：使用者回報（v1.1.1）側邊欄關掉、用快速設定磚開過一次後，「Peek 條消失，但摸原本的位置還拉得出來」。v1.1.1 用磚打開會啟動服務、Peek 條不受開關控制；開發版已改成開關關著時 Peek 條不顯示、關閉側邊欄 0.7 秒後停掉服務。再加兩道保護：Peek 條的觸控在按下時檢查「該顯示」且開關打開，否則不處理並補上不可觸控；在 App 裡切換「側邊欄」開關會立刻重新判斷 Peek 條。
- **側螢幕面板捷徑會讓背景跳回桌面**（維持現狀，不修）：從 App 捷徑（`ACTION_VIEW`）啟動透明中繼 `OpenPanelActivity` 時，側邊欄一出現背景就已經是桌面，是在啟動那一刻決定的。試過 `moveTaskToBack(true)` 和延後 1 秒才 `finish()`，實機記錄都確認有執行，但都沒有差別，已還原成立刻 `finish()`。從側螢幕面板開一般 App（時鐘）再按返回會回到原本的 App，所以差別在「App 捷徑」或「透明」，未確認是哪一個。之後使用者實測 Duolingo 的 App 捷徑從側螢幕面板開、往回滑會回到原本的 App，所以不是「App 捷徑」本身的問題。接著試了不透明主題（另做一個不透明的中繼給捷徑用），記錄確認有用到，背景一樣在啟動時就變成桌面，也看不到黑色閃一下，已還原。沒試的做法：拿掉 `taskAffinity=""`（同一個工作）、允許出現在最近使用（評估機會不大，沒做）；側邊欄打開後用無障礙服務記下的前一個 App 再把它叫回來（會閃桌面，少數 App 會回首頁）。設定「其他開啟方式」的說明建議改用快速設定磚（會留在原本的 App）。
- **自訂群組頭貼**：設定 → 通知外觀 →「設定群組頭貼」（`GroupPicsActivity.kt`）列出收過訊息的 LINE 群組，點一個 →「選擇圖片」／「恢復預設」（`GroupPicActivity.kt`，系統相片選擇器，不需權限），也有「全部恢復預設」。原本做在側邊欄長按，使用者覺得不直觀，已拿掉。圖片取中間正方形、最大 256px，存在 `files/groupav/`，只留在手機上。只換通知的群組頭貼（大圖示、對話捷徑、MessagingStyle 對話圖示）；側邊欄和小工具的群組列顯示的是發話者頭貼，不受影響。換完會靜音重發還掛著的通知，可能讓 Windows「手機連結」再通知一次。通知右下角的 App 徽章由系統讀 App 本身圖示（AOSP `AppIconProvider.kt` 的 `loadUnbadgedIcon`），無法更換。
- **底部分頁改成玻璃分頁列**（未實機測試）：`GlassTabBar.kt` 取代 BottomNavigationView（刪掉 `menu/main_tabs.xml`、`bg_bottom_nav.xml`、`color/nav_item.xml`），照使用者提供的 Liquid Glass 參考設計改寫成原生 View：浮動膠囊、按鈕 74×54dp、白 12% 玻璃、135° 亮邊、陰影 0 8 32、選中膠囊用彈簧（stiffness 400、damping 30）滑動並在 450ms 內拉長到 1.15 倍、按下縮到 0.92；系統關閉動畫時直接跳到結果。Android 不能模糊背後內容，`blur` 只保留參數；沒有游標，光澤固定在上緣中間（25%）。淺色模式玻璃 min(60%, 12%+23%)、深色文字。根版面改成 FrameLayout，分頁列浮在內容上，`keepTabsAboveNavBar` 改成調整分頁列的下邊距（玻璃離手勢列 24dp）並給 ScrollView 底部留白。
- **分頁列底部漸層、設定與常見問題整理**（未實機測試）：分頁列後面加 `tabScrim` 漸層（透明 → 85% → 頁面底色），內容捲到底部附近淡出，不會跟分頁文字疊在一起。設定分頁的側邊欄順序改成「要收哪些 App → 外觀 → Peek 條 → 其他開啟方式」；「側邊欄：外觀」最上面放預覽按鈕，分成「大小與位置／文字／樣式」三組（新樣式 `SubGroup`），恢復預設放最後。常見問題 13 題併成 7 題、分「側邊欄與 Peek 條／通知／收不到訊息」三組；「收不到訊息？」的答案由 `MainActivity` 組（各 App 通知＋依手機品牌的電池設定，沿用 `tipBackground`）。網站的常見問題還沒同步。整理前的版本備份在 repo 外的 `D:\projects\sidepeek-backups\settings-before-reorg-20261108`（activity_main.xml、MainActivity.kt、GlassTabBar.kt 和當時的測試版 APK）。
- **玻璃分頁列真的模糊**（未實機測試）：`GlassTabBar` 在 Android 12+ 把背後的頁面內容（`backdrop` 圖層：ScrollView 的內容，剪在 ScrollView 範圍內）重畫進 RenderNode，套 RenderEffect 模糊 20dp＋飽和度 180%，剪成玻璃形狀顯示；更舊的版本維持原本的半透明玻璃。捲動、版面變動時重畫分頁列。底部漸層減淡（50%→75%），而且不畫進模糊裡。選中的分頁改成品牌綠（深色 #4ADE80、淺色 #15803D），膠囊加綠色底；沒選中的 60%。曾試過頁面背景加綠色光暈（`GlowBackdrop`），卡片是半透明的，整頁被染綠，使用者覺得不好看，已拿掉。改之前的版本備份在 `D:\projects\sidepeek-backups\before-glass-glow-20261108\`。
- **設定頁再整理、圓角按壓效果**（未實機測試）：可收合卡片的標題列按下時原本是直角、沒貼齊卡片的反白；`CollapseHeader` 用負邊距蓋過卡片左右內距，卡片 `clipToOutline`，反白填滿並跟著圓角（常見問題卡片也加 `clipToOutline`）。設定頁分三組（`GroupLabel`「通知／側邊欄／其他」），卡片標題拿掉「側邊欄：」，「其他」卡片改叫「檢查更新」；App 內引用舊標題的文字一併改（網站還沒改）。「通知外觀」分「樣式／鈴聲／群組頭貼」；「Peek 條」只留一張示意圖，分「位置／外觀／操作／什麼時候顯示」，恢復預設放最後；幾段說明縮短。改之前的版本備份在 `D:\projects\sidepeek-backups\before-settings-tidy-20261108\`。
- **Peek 條卡片精簡**（未實機測試）：滑桿名稱拿掉「Peek 條」，數值只留數字（不透明度不再加「（看不到）」，靈敏度只顯示低～高）；「操作」改名「滑動」；說明縮成一行；「什麼時候顯示」的三個開關改成標題＋灰色小字（`setTitleSummary`），不再另外插說明，直向說明移到最後；`SeekRow` 上間距 14→10dp（全 App）。改之前的版本備份在 `D:\projects\sidepeek-backups\before-peek-card-tidy-20261108\`。

- **液態玻璃分頁列、設定即時預覽**（未實機測試）：Android 13 以上分頁列改用 AGSL 著色器（`GlassTabBar.LIQUID_AGSL`）：玻璃邊緣折射、少量色散、模糊降到 1.5dp、底色幾乎透明；選中的分頁改成透明透鏡，放大底下內容、邊緣帶綠，可以按住左右拖動切換（經過分頁會輕震）。著色器失敗時自動退回 Android 12 的毛玻璃。新增 `SettingPreview`（`tag="handle"`／`"panel"`）：「Peek 條」卡片頂端是螢幕邊緣特寫，跟著寬高、不透明度、距離邊緣、左右邊即時變（0% 用虛線框表示），改靈敏度、展開卡片或點圖會播手指滑過虛線打開側邊欄的動畫，取代 `img_guide_handle_drag` 圖（圖檔還在，沒刪）；側邊欄「外觀」卡片頂端是整支手機縮圖，跟著寬高、位置、圓角、文字大小、行數、背景變暗變。設定小標題加圖示（`ic_sg_*`，`SubGroup` 樣式統一著綠色）。「出現的位置」括號說明移到下方小字；「背景變暗」改成標題＋小字；「只在桌面顯示」小字縮短。改之前的版本備份在 `D:\projects\sidepeek-backups\before-liquid-glass-20261108\`。
- **側邊欄外觀改用真的縮小側邊欄預覽**（未實機測試）：拖寬高等滑桿時真的側邊欄會跳出來擋住設定頁，所以拿掉了（`bindSeek` 的 `livePreview`、位置/文字/背景變暗的 `previewPanel()` 都移除，`PanelHost.preview` 目前沒人呼叫）。`PanelController.openPanel` 裡建畫面的部分抽成 `buildPanel()`，新增 `buildMiniature()` 用同一套畫面、最新 8 則訊息、不開視窗；新的 `PanelMiniature` 在卡片頂端用整支手機大小排版再縮小，任何設定一變（60ms 合併）就重建，觸控全部讓給設定頁。`SettingPreview` 只剩 Peek 條特寫。「在手機上預覽」按鈕保留。實機錄影後修正：原本每次變動都把重建往後延，拖動中縮圖不動、放手才跳，改成拖動中每 120ms 重建一次，寬高、位置、圓角則每一格直接改卡片大小；縮圖背景改成淺色漸層桌布，深色側邊欄的邊和圓角才看得出來。改之前的版本備份在 `D:\projects\sidepeek-backups\before-real-panel-preview-20261108\`。
- **LINE 未接來電保留原通知、補檢查不再吃掉新訊息的提醒**（未實機測試；使用者回報「LINE 未接來電不會顯示於通知欄」）：診斷記錄顯示 LINE 的未接來電是 category=msg、頻道 NewMessages、tag `NOTIFICATION_TAG_MISSED_CALL`，之後的未接會更新同一則。原本被當訊息藏起並轉發成「LINE未接來電」聊天室，但 (1) 來電通知排的 post+2s 補檢查剛好同時看到它，靜音轉發，真正的 post 再被當重複略過；(2) 第二、三通是同內容更新，被略過、原通知卻照藏，完全沒提醒。修法：`isMissedCall()`（LINE 且 tag 含 MISSED_CALL）列入 `keepReason`、`handle()` 直接略過，交給 LINE 自己顯示；`sweep()` 跳過 2 秒內剛出現的通知（`SWEEP_FRESH_MS`），讓 onNotificationPosted 正常（有聲）處理，記錄多 `fresh-skipped`。改之前的版本備份在 `D:\projects\sidepeek-backups\before-missed-call-fix-20261108\`。
- **側邊欄縮圖往下滑會停在上方**（未實機測試；使用者說外觀設定太長，滑到下面看不到縮圖）：`PanelMiniature` 用 ViewTreeObserver 的捲動／版面監聽算位置，滑過時用 translationY 貼在 ScrollView 頂端（避開狀態列，留 8dp），前 100dp 從 1 縮到 0.67（約 200dp，pivot 上緣中間），到 `grpPanelLook` 底部就被推走。貼住時 translationZ=6dp 讓它畫在後面的設定之上，畫不透明底（視窗底色＋卡片灰），並吃掉點擊，避免動到底下的滑桿；拖動仍會捲動頁面。提示文字補「往下滑時，它會停在畫面上方。」。改之前的版本備份在 `D:\projects\sidepeek-backups\before-sticky-preview-20261108\`。
- **側邊欄外觀／Peek 條設定縮短**（未實機測試）：黏住邏輯抽成 `StickyPreview`，`PanelMiniature`（縮到 0.67）和 `SettingPreview`（Peek 條示意圖，縮到 0.75）共用。兩張卡改成一列一項（`SeekLine`：`LineName` 固定 76dp｜`SeekInline`｜`SeekValue`；選項用 `SegGroupInline`），拿掉卡內分組小標；`bindSeek` 的數值格改找同一列最後一個非 label 的 TextView，滑桿文字縮短（寬度/高度只寫 %、「每則最多」）。很少調的收進「更多設定 ▾」（`setupMoreToggle`）：側邊欄的圓角、背景變暗；Peek 條的距離邊緣（含返回手勢提示）、不透明度。「在手機上預覽」和「恢復預設」並排。「什麼時候顯示」三個開關＋使用情況權限移到新卡 `cardHandleWhen`（sections 新增「Peek 條什麼時候顯示」）。刪掉示意圖已看得出的提示（0% 也能滑開、靠下單手好按）。`ic_sg_*` 圖示目前只剩其他地方可能用到。改之前的版本備份在 `D:\projects\sidepeek-backups\before-settings-compact-20261108\`。
- **未接來電照舊收進側邊欄**（未實機測試；接續上面的未接來電修正）：`handle()` 不再對未接來電直接 return，照常存進 MessageStore（側邊欄「LINE未接來電」），但不呼叫 `Notifier.postChat`，原通知也照舊不藏（`keepReason`）。這種聊天室記在 prefs `missed_call_chats`，`recoverOrphans` 會跳過，避免 30 秒健康檢查替它補發 Sidepeek 通知。LINE 為第二、三通未接重發同一則（內容常一樣）時，若 postTime 比上次晚 5 秒以上，用 `MessageStore.bumpRepeat` 在側邊欄那張卡上加次數。
- **未接來電改回由 Sidepeek 轉發**（未實機測試；取代上面「保留 LINE 原通知」「只收進側邊欄」兩項的做法）：LINE 的未接來電在「訊息提醒」類別，使用者照常見問題把它設成靜音後，交給 LINE 就不會響，所以改回轉發。`keepReason` 不再保留未接來電（照常藏起），`handle()` 照一般訊息轉發；同一則（tag 含 MISSED_CALL）內容沒變的重發，只要 postTime 比上次（`lastMissedPost`）新就視為新的一通（LINE 發幾次就轉發幾次；同一次通知被 asap／post／補檢查重複看到時 postTime 不變，不會重複響）：`bumpRepeat` 加次數並用 `postChat` 重新發一次（會響、會跳出）。拿掉 `missed_call_chats` 與 `recoverOrphans` 的跳過。補檢查跳過 2 秒內通知的修正（`SWEEP_FRESH_MS`）保留，第一通不會再被靜音發出。1.1.1 的問題就是這兩個漏洞：第一通撞上補檢查被靜音發出，後面幾通被當重複略過、原通知卻照藏。
- **App 內常見問題重新排序**（網站、README 還沒改，等發版一起）：分成「常遇到的狀況」（收不到訊息？／通知響兩次，或震動變成長震？／通知暫時不跳？／通知內容不完整？／新增「LINE 的通知要怎麼設？」）和「認識 Sidepeek」（示意圖移到這組；為什麼要開無障礙／Peek 條什麼時候出現／跟三星側螢幕面板的關係／為什麼整理只有 LINE）。原本的「通知暫時不跳，或響兩次、變成長震？」拆成兩題；「響兩次」那題改成沒遇到就不用設靜音，並說明未接來電由 Sidepeek 發通知、設靜音也會響。Peek 條那題的開關位置改指「Peek 條什麼時候顯示」卡片。「常見問題」仍預設展開。改之前的版面備份在 `D:\projects\sidepeek-backups\before-faq-reorder-20261108\`。
- **狀態列小圖示可自訂**（未實機測試）：設定 → 通知外觀 →「狀態列小圖示」，一排五格（`MainActivity.setupNotifIconPicker`）：8 個內建（`NotifIcons.CHOICES`：Sidepeek 預設＋`ic_stat_chat/forum/bell/mail/heart/star/bolt`，Material Icons 形狀，Apache 2.0）和「自訂」。自訂用系統相片選擇器選圖，`NotifIcons.makeSilhouette` 轉成 96px 白色剪影存在 `files/notif_icon.png`（圖本身有透明就用透明，否則以四角平均色當背景、跟背景差越多越不透明；全空或全滿就拒絕），格子裡直接顯示剪影；選了自訂後再點一次可換圖。pref `notif_icon`。`Notifier` 三處 `setSmallIcon` 改用 `NotifIcons.current(ctx)`（`IconCompat`，自訂缺檔時退回預設），換了會 `refreshAllSilently`。側邊欄服務的常駐通知仍用固定圖示。通知右下角的 App 徽章是系統讀 App 圖示，換不了。改之前的版本備份在 `D:\projects\sidepeek-backups\before-notif-icon-20261108\`。
- **說明頁「認識 Sidepeek」獨立成卡片**（未實機測試）：介紹性的四題和三星側拉標籤示意圖從「常見問題」移到新卡 `cardAbout`（sections 新增「認識 Sidepeek」，預設收起；精靈進行中跟常見問題一樣隱藏），`setupFaqAccordion` 兩張卡都套用。「常見問題」只剩遇到狀況的五題，拿掉「常遇到的狀況」小標。改之前的版本備份在 `D:\projects\sidepeek-backups\before-about-card-20261108\`。
- **狀態旁的「為什麼？」連結**（未實機測試）：新樣式 `WhyLink`（品牌綠、問號圖示）。首頁側邊欄服務沒開時（`whyA11y`）、「讓它更穩定」卡（`whySuggest`）、LINE 通知清理缺無障礙時（`whyLineClear`）、Peek 條缺讀取使用情況時（`whyUsage`）。點了 `openFaqQuestion()`：切到說明分頁、展開「常見問題」或「認識 Sidepeek」、展開那一題並捲過去；用題目文字比對，題目改字時要一起改 `setupWhyLinks`，找不到會寫進診斷記錄。改之前的版本備份在 `D:\projects\sidepeek-backups\before-faq-links-20261108\`。
- **設定分頁改成「清單＋子頁面」**（未實機測試）：`settingsList` 由 `setupSettingsPages()` 依 `settingsItems` 產生（`layout/settings_row.xml`、`settings_group_label.xml`），分組「通知／側邊欄／其他」各一張卡，每列有圖示、標題、目前設定摘要（從畫面上的開關、選項、滑桿數值讀出來，回到清單和 onResume 時更新）、箭頭。點一列 `openSettingsPage()`：隱藏清單和其他卡片，顯示返回列＋標題，該卡片的標題列隱藏、內容展開。返回鍵（只在設定分頁的子頁面時攔截）、左上箭頭、再點一次「設定」分頁都回清單；旋轉螢幕會記住開著的子頁面（`main.settingsPage`）。卡片本身的版面沒動，舊的分組小標在清單模式隱藏。標題改名：外觀→側邊欄外觀、Peek 條→Peek 條樣式、Peek 條什麼時候顯示→Peek 條顯示時機、其他開啟方式→其他打開側邊欄的方法（常見問題裡提到的名稱也一起改了）。新圖示 `ic_set_clean/apps/open/update`、`ic_arrow_back`、`ic_chevron_right`（Material Icons）。改之前的版本備份在 `D:\projects\sidepeek-backups\before-settings-subpages-20261108\`。
- **原創圖示、預覽頁一頁放得下**（未實機測試）：(1) 設定清單 8 個（`ic_set_look/clean/apps/panel/peek/when/open/update`）和分頁列 3 個（`ic_tab_home/settings/help`）換成自己畫的線條圖示，設計原稿在 `tools/icons/sidepeek-icons.js`，`node tools/icons/preview.js` 產生深淺色預覽 HTML。(2) 「側邊欄外觀」「Peek 條樣式」子頁面不再用浮動預覽：刪除 `StickyPreview.kt`，預覽高度由 `MainActivity.fitSettingsPreview()` 算（畫面高 − 頁面其他內容，縮圖 180–460dp、Peek 條特寫 120–300dp；版面每次變動都重算，差 2px 內不動），螢幕太矮才會捲動。「更多設定」收合拿掉（`setupMoreToggle`、`MoreToggle` 樣式刪除），圓角、背景變暗、距離邊緣、不透明度直接顯示；拿掉縮圖下的提示、靈敏度和返回手勢兩句提示（改寫成常見問題新題「常不小心打開側邊欄，或誤觸返回手勢？」），Peek 條提示縮短。改之前的版本備份在 `D:\projects\sidepeek-backups\before-onepage-icons-20261108\`。
- **子頁面隱藏分頁列、恢復預設不再跑回舊值**（未實機測試）：(1) 設定子頁面打開時底部分頁列隱藏（`syncTabBar()`，refresh 也改用它），頁面底部不再被蓋住，分頁列的底部留白跟著變 0，預覽高度重算；從子頁面點「為什麼？」跳到說明頁時分頁列會再出現。(2) 「恢復預設」原本用 `recreate()`，系統會把滑桿和選項的舊位置還原回來，畫面顯示舊值但設定已經是預設，所以縮圖跟滑桿對不上（使用者看到像舊版的側邊欄）；選項群組還原時甚至會把舊值寫回去。改成清掉設定後重新執行 `setupPanelAppearance()`／`setupHandleSettings()`，並顯示「已恢復預設」。
- **側邊欄外觀改成左右兩欄**（未實機測試；使用者手繪構想）：上半部 `panelTopRow`：左 42% 是 `PanelMiniature`，右 58% 是寬度、高度、位置（新樣式 `StackHead`／`StackName`：名稱和數值一行，滑桿或選項在下一行用整欄寬）。`PanelMiniature` 改成手機外框同時符合寬和高、置中。`fitSettingsPreview()` 在兩欄時以整列高度計算、預覽不小於右欄，`settingsPreview()` 也會往下一層找。下面剩文字、每則最多、圓角、背景變暗和兩個按鈕。改之前的版本備份在 `D:\projects\sidepeek-backups\before-panel-twocol-20261108\`。
- **設定頁用示意動畫取代長說明**（未實機測試；使用者覺得設定文字太多，之後要出英日韓文）：新增 `DemoView`，用程式畫的小手機循環動畫（88×156dp，`android:tag` 指定場景），畫面裡沒有任何文字，所以換語言不用另做一份；跟深淺色走，點一下重播，只在看得到時動，系統關閉動畫時顯示一格靜止畫面。場景：`qs`（下拉快速設定點綠色方塊開側邊欄）、`shortcut`（長按圖示拖出捷徑）、`widget`（長按桌面加小工具）、`clear_tap`／`clear_line`／`clear_smart`（三種清通知方式）、`when_home`／`when_ime`／`when_lock`（會讀目前的開關和 Peek 條左右邊，開關一切就從頭播，顯示這個設定的效果）。版面：新樣式 `DemoRow`（左動畫、右 `DemoCol`）、`DemoTitle`、`DemoText`。「其他打開側邊欄的方法」三段各一列，快速設定按鈕改叫「加到快速設定」，三星側螢幕面板的說明收進「詳細說明」，拿掉「首頁側邊欄開關關著時不會出現 Peek 條」那句；「LINE 通知清理」三點各一列，兩個開關移進對應的列並改成標題＋小字，拿掉「第 2、3 點要開無障礙」的說明（開關小字和沒開時的警告已有）；「Peek 條顯示時機」三個開關各配一個動畫。通知外觀（小圖示、鈴聲）、要收哪些 App、檢查更新的長說明縮成一句，其餘收進新的「詳細說明 ▾」（`DetailsToggle` 會展開緊接在後的 `DetailsBody`，`MainActivity.setupDetailsToggles` 開機時掃一次）。改之前的版本備份在 `D:\projects\sidepeek-backups\before-anim-demos-20261108\`。
- **LINE 通知清理改成仿真示意動畫**（未實機測試；使用者看不懂上一版的灰色小手機動畫，參考 `tools/feature-video` 的功能介紹影片，要求程式畫也要做到那個程度）：新增 `ClearDemoView`（後來改名 `StepDemoView`，見下一項），頁面頂端一整塊：左邊仿真手機（通知欄、桌面、LINE 聊天列表、聊天室，配色照 feature2.html，固定深色），右邊三個步驟（點通知進聊天室／直接打開 LINE／進階清理）輪播，進行中的步驟展開說明並有進度條，結果出現綠色膠囊。開關關著的步驟標「（已關閉）」。點步驟可以跳過去，點手機重播；系統關閉動畫時停在結果畫面。所有文字（步驟、膠囊、示範聊天室名稱和訊息）放在 strings.xml 的 `demo_*`、`clear_step*`，翻譯時一起翻。兩個開關回到全寬，接在動畫下面。這頁原本的三個 `DemoView` 列拿掉；其他兩頁還是舊的 `DemoView`，等使用者看過再決定。
- **三頁示意動畫統一成仿真風格**（未實機測試）：`ClearDemoView` 改名擴充成 `StepDemoView`（`android:tag` = `clear`／`open`／`when`），三頁共用同一套手機、步驟清單、進度條和結果膠囊。`open`：快速設定（在相簿裡拉下快速設定，點綠色 Sidepeek 方塊，側邊欄滑出）、App 捷徑（長按圖示，選單裡「開啟側邊欄」拖到桌面，點一下打開）、桌面小工具（長按空白處，小工具，選「最近訊息」，出現在桌面）。`when`：只在桌面顯示、打字時隱藏、鎖定畫面也顯示，照目前的開關演，膠囊文字跟著開關變（`when_step*_pill_on/off`），開關一切換就播那一段；關著的步驟標「（已關閉）」。Peek 條和側邊欄畫在使用者設定的左右邊。「其他打開側邊欄的方法」快速設定按鈕恢復原本文字放在動畫下方，三星說明收在「詳細說明」；「Peek 條顯示時機」三個開關回到全寬放在動畫下方。舊的 `DemoView.kt` 和 `Demo*` 樣式刪除。改之前的版本備份在 `D:\projects\sidepeek-backups\before-stepdemo-20261108\`。
- **多語言：繁中、英、日、韓＋App 內語言設定**（未實機測試）：版面和程式碼裡的文字全部搬進字串資源，預設 `values/` 是英文（系統是其他語言時顯示英文），中文在 `values-zh-rTW/`（不能用 `values-zh`：Android 把不帶地區的 zh 當成簡體，繁中手機會退回英文），另有 `values-ja/`、`values-ko/`；每個資料夾四個檔：`strings.xml`（原有＋示意動畫）、`strings_layout.xml`（版面，名稱 `ui_*`，由 scratch 腳本自動從 layout 搬出）、`strings_code.xml`（程式碼）、`strings_privacy.xml`（隱私權政策改成 string-array，`PrivacyActivity` 讀 `privacy_section_1..9`，第一項是標題）。`node tools/i18n-check.js` 檢查每種語言是否齊全、佔位符一致。設定清單「其他」新增「語言」子頁面（`cardLanguage`、`setupLanguage()`、圖示 `ic_set_language`）：跟著手機系統／繁體中文／English／日本語／한국어，用 `AppCompatDelegate.setApplicationLocales`；`res/xml/locales_config.xml` 讓 Android 13 以上的系統「應用程式語言」也列出這四種；Android 12 以下由 manifest 的 `AppLocalesMetadataHolderService`（autoStoreLocales）記住，但服務和通知在 12 以下仍跟系統語言。靠文字運作的地方改掉：「為什麼？」連結改用常見問題題目的字串資源比對；`bindSeek` 以「：」或「:」切名稱和數值；設定摘要的通知數量取數字、通知樣式去掉「（」或「 (」之後；`OemGuide.guide(ctx)`、`NotifIcons.Choice.label` 改成資源 id；`A11yDisclosure` 段落改成資源 id；`Notifier.createMsgChannel`、`collapsedSummary` 多帶 ctx；`sections` 改 `by lazy`。刻意不翻：`LineListenerService`／`StickerLoader` 比對 LINE 文字的「傳送了貼圖」、`Notifier` 判斷回覆／已讀按鈕的字、`ProfileLinks`／`ChatTitleMatch` 的規則、`Diagnostics` 匯出內容（給開發者看）、沒名稱群組的「群」字頭像。存下來的訊息會保留收到當時語言的「我」「（圖片）」。操作示範影片和部分示意圖（`res/raw/guide_*.mp4`、`img_*`）仍是中文，語言頁有提示。日韓文是初稿，隱私權政策和無障礙說明上架前最好請母語者看過；網站、README 還沒做多語言。改之前的版本備份在 `D:\projects\sidepeek-backups\before-i18n-20261109\`。
- **側邊欄標題不再被截斷**（未實機測試；英文「Recent messages」在 50% 寬被切成「Recent m…」）：側邊欄標題改用較短的 `panel_title`（Messages／メッセージ／메시지／最近訊息），放不下時自動縮小字級（最小 12sp）；沒有訊息時「清除」改成 GONE，不再佔位。示意動畫的三句長膠囊（英文、日文、韓文）改短。
- **首頁「怎麼用」三格圖改成程式畫、標題列字標特效**（未實機測試）：`StepsOverviewDrawable` 取代 `img_steps_overview`（首頁 `imgStepsOverview` 和設定精靈最後一步），照原圖重畫：疊起來的對話通知、邊緣的 Peek 條、滑開的側邊欄，文字全部從 strings.xml 的 `how_*` 讀（四種語言各有示範群組、人名、訊息），標題放不下自動縮小、說明最多三行。圖檔 `img_steps_overview.webp` 還在，沒刪。標題列改成 `BrandTitleView`（`supportActionBar.customView`）：「Side」標題列文字色、「peek」品牌綠漸層（淺色模式的綠色標題列上改淺薄荷綠），測試版的「Test」小字淡色；打開 App 時一道光從左掃到右，之後每 12 秒一次，點一下也會掃，系統關閉動畫時不掃。還是中文的圖：`img_faq_header`（認識 Sidepeek）、`img_step_sidebar`（精靈）和 `res/raw/guide_*.mp4`。改之前的版本備份在 `D:\projects\sidepeek-backups\before-how-brand-20261109\`。
- **首頁「怎麼用」改成動畫、另外兩張中文圖重畫**（未實機測試；使用者覺得三格圖的小字太小）：首頁的三格圖換成 `StepDemoView tag="how"`（跟設定頁同一套）：① LINE 一則一則跳出三張通知，收成一張 Sidepeek 對話通知 ② 桌面邊緣淡入 Peek 條並亮一下 ③ 手指從 Peek 條往內滑，側邊欄滑出；右邊三個步驟用正常字級，結果膠囊 `how_pill1..3`。`StepsOverviewDrawable` 只剩設定精靈最後一步在用，標題 15、說明 10（設計座標），圖高 213→236。新增 `Illustrations.kt`：共用底 `IllustrationDrawable`，`EdgeHandlesDrawable` 取代「認識 Sidepeek」的 `img_faq_header`（三星側拉標籤 vs Peek 條，`edge_*`），`SidebarStepDrawable` 取代精靈「打開側邊欄」的 `img_step_sidebar`（側邊欄列出三則訊息、Peek 條、往內滑箭頭，`side_*`）。三個 webp 圖檔還在，沒刪。仍是中文的只剩 `res/raw/guide_*.mp4` 示範影片。改之前的版本備份在 `D:\projects\sidepeek-backups\before-how-anim-20261109\`。
- **首頁最後一段被分頁列擋住**（未實機測試）：從設定子頁面回來時，分頁列由隱藏變回顯示不一定改變大小，`keepTabsAboveNavBar` 的 `update()` 沒被觸發，ScrollView 底部留白停在 0。改成 ScrollView 每次版面完成（OnGlobalLayout）也呼叫 `update()`，沒變就不動。
- **首頁動畫第 1、2 段重做**（未實機測試；使用者指出舊版把 LINE 演成一則一則分開列出，跟 iPhone 一樣，沒演出 LINE 會一直重疊訊息）：`sceneMerge` 改成拉下通知欄 → LINE 的通知一則一則蓋在前一則上面（舊的壓在底下只露出邊，看不到內容）→ 那一疊換成 Sidepeek 對話通知，往下展開、三則訊息逐行出現；下面另一個聊天室的通知也從 LINE 換成 Sidepeek。`scenePeek` 改成 Peek 條從螢幕外滑進邊緣，再閃兩圈綠光。
- **首頁一頁放得下、動畫上可以捲動**（未實機測試；使用者錄影：首頁預設位置最下面那段被分頁列蓋住，手指在動畫上滑不動）：首頁的 `StepDemoView(how)` 手機寬 168→146dp，拿掉動畫下面的說明段落（字串 `ui_home_5` 留著沒用），整頁約少 90dp，在使用者手機上不用捲就不會被分頁列蓋住。`StepDemoView.onTouchEvent` 改成只有沒移動超過 touch slop 才算點一下，一拖動就放手給 ScrollView 捲動。
- **分段按鈕被切掉、英文按鈕全大寫**（未實機測試；英文「Conversation (recommended)」折兩行時第一格底部被切）：`SegGroup`／`SegGroupInline` 加 `baselineAligned=false`（原本橫向 LinearLayout 會對齊第一行基線，把兩行的那格往下推），`Seg` 高度改 match_parent，一排等高。`Btn`／`BtnOutline`／`BtnText` 加 `textAllCaps=false`，英文不再自動轉大寫。
- **Peek 條樣式預覽改成手機＋放大鏡**（未實機測試；使用者覺得原本的邊緣特寫像平板）：`SettingPreview` 重寫：左邊一支直立手機（照本機螢幕比例，Peek 條照真實比例畫，太細時至少 1.5dp；位置用 `Prefs.handleY`，沒存過時是 75% 高），右邊圓形放大鏡（0.9px/dp 的邊緣特寫：返回手勢範圍、Peek 條、要滑過的虛線、手指滑動和側邊欄滑出動畫），手機上的 Peek 條外圈和放大鏡用綠線連起來。Peek 條在左邊時兩者左右對調。改之前的版本備份在 `D:projectssidepeek-backupsefore-magnifier-20261111`。
- **分頁列玻璃改成 Launcher 那種液態玻璃**（未實機測試；使用者希望跟 Launcher 專案一樣）：讀了使用者給的 dotfold APK 裡的著色器文字比對（像是 Kyant AndroidLiquidGlass／Backdrop 的，檔頭授權說法不明，所以沒有複製，用 Sidepeek 原本的 `LIQUID_AGSL` 自己改）：邊緣法線往中心外側傾斜（`DOME` 0.35，玻璃像微微鼓起）、七色色散（`SPREAD` 0.22，取代紅藍兩點）、鮮豔度淺色 1.35／深色 1.5，拖動透鏡時邊緣折射略加強。新增 `RIM_AGSL`：依光線角度（左上 -135°）亮起的邊緣高光，取代整條分頁列和透鏡原本固定的漸層亮邊（透鏡下方保留淡綠色）。著色器失敗時寫進診斷記錄（GLASS … shader failed）並退回原本做法。參數只是近似，要使用者對照 Launcher 調。改之前的版本備份在 `D:projectssidepeek-backupsefore-launcher-glass-20261112`。
- **分頁列改成通透玻璃**（未實機測試；使用者給 Launcher 截圖要「通透的感覺」）：液態玻璃不再模糊（`LIQUID_BLUR_DP` 1.5→0），底色淺色 0.14→0.05、深色 0.03→0，拿掉內側發光、頂部光暈、上下亮線和厚度漸層（這些只留給 Android 12 以下的毛玻璃），只剩折射和依角度的邊緣高光（`drawRim`）。邊緣折射加寬加深（`BEZEL_DP` 16→24、`REFRACT_DP` 14→22）。
## 觀察項（先不修）
- **貼圖通知數字顯示 2**：紀錄的 `unread=1`，但畫面顯示 2。數字超過 10 時也停在 10（通知只帶 10 則），所以 One UI 很可能是算 MessagingStyle 的項目數，不是 `setNumber`。曾試過「貼圖有快取時第一次就帶圖發送」，原因不確定，沒有收進 v1.1.1。
- **詳細通知有時不跳**：00:38:16 抓到一次，推斷是補掃（`sweep`，+0.5、+2、+8 秒）用 `silent = true` 搶先處理到剛進來的新訊息，系統判定 `suppressive group alert behavior` 並收回彈出。只有這一次，使用者實測文字都有跳，效果也難驗證，所以先不修；日後有文字通知被收回的紀錄再處理，修法方向是補掃遇到剛發出的訊息照一般提醒，不要改用 `alertOnce`，否則新訊息不會響。另一個已證實的原因是系統行為：00:54:50 碰到彈出通知（`panel_open_peek`）後，之後 Sidepeek 的通知都被判定 `PEEK suppressed: package snoozed`，三張貼圖都沒彈出，這不是 Sidepeek 能控制的。23:08、23:58 的案例是 `silent=false`，很可能也是這個，但紀錄已沒有了。查法：接 adb 後，失敗幾分鐘內匯出 `dumpsys activity service com.android.systemui/.SystemUIService buffers`，看 `VisualInterruptionDecisionProvider` 和 `NotifHeadsUpLog`。
- **LINE 取消後重發同一則訊息，2 秒倒數可能不會撤銷**：`LineListenerService.kt:378` 在 LINE 取消通知後開始 2 秒倒數，撤銷在 `:639`，但重複訊息會在 `:587`、`:588`、`:591`、`:635` 先返回。理論上 Sidepeek 通知會被誤清，聊天室也會被標成已讀。目前沒有使用者回報；兩份診斷紀錄共 372 次 LINE 取消訊息通知，其中 40 次在 2 秒內又發通知，但沒有一次是同一則重發。等紀錄出現 `REMOVED_BY_APP` 後 2 秒內接 `POSTED`、再接 `skip seen`，或有人回報通知被誤清時再處理。
