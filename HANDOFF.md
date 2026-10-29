# Sidepeek 交接筆記（給下一位協助者）

Android App「Sidepeek」：LINE 通知依聊天室整理（只接管 LINE），加上螢幕邊緣「Peek 條」往內滑打開的側邊欄（可收任何使用者勾選的 App）。
全部在手機上處理，無伺服器。GitHub：github.com/Sidepeekapp/sidepeek（GPL-3.0；「Sidepeek」名稱與圖示不在授權內）。

## 基本
- 套件名 `app.sidepeek`，minSdk 26，compileSdk/targetSdk 36，AGP 8.11.2、Gradle 8.13、Kotlin 1.9.24。
- 版本名稱跟著發布版本走（目前 1.0.4，前一版 1.0.3）；versionCode 目前 11，每次發新版都要遞增。發版時 Run workflow 填的版本號要和 versionName 一致。
- 正式版：根目錄放 `keystore.properties`（不進版控，簽章金鑰在使用者手上）→ `./gradlew :app:assembleRelease` / `:app:bundleRelease`。R8 開啟。
- 聯絡信箱 sidepeek.app@gmail.com（App 內「意見回饋」會自動帶手機資訊）。
- 測試：`./gradlew :app:testDebugUnitTest`（LinkExtractorTest、ProfileLinksTest、ConversationTitlesTest、UpdateCheckerTest）。

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
- 本機可直接編簽好的正式版（不必等 CI）：根目錄 `keystore.properties` + `sidepeek-release.jks`，`./gradlew :app:testDebugUnitTest :app:assembleRelease`。
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
- 更新檢查比對 tag 版號，舊版（1.0.3、1.0.4）會直接提示 v1.1.0。發版 tag 用 `v1.1.0`，檔名 `sidepeek-v1.1.0-build12.apk`。
