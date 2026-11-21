# Play Console 聲明草稿（Sidepeek Play 版）

2026-11-14 草稿。依 Play 版（`play` flavor）的行為撰寫：沒有直接電池豁免請求、沒有 GitHub 檢查更新。
表單裡要貼的文字用英文（審查員看英文最穩），中文是給自己看的說明。
如果之後改了無障礙用途（例如新增讀取畫面內容的功能），Play 要求**重新送一次無障礙聲明**。

隱私權政策網址：`https://sidepeekapp.github.io/Sidepeek/privacy.html`
（網站版要 push 到 main 之後才會更新成 11/14 的內容，送審前先確認網頁上的日期是 2026年11月14日。）

---

## 一、無障礙 API 聲明

位置：Play Console → 政策與計畫 → 應用程式內容 → 無障礙 API（Accessibility API）。

| 欄位 | 填法 |
|---|---|
| 是不是無障礙工具（isAccessibilityTool） | **否**（manifest 是 `false`） |
| 使用原因 | 勾 **App functionality**（應用程式功能），其他都不勾 |
| 是否透過無障礙功能收集或分享個人／敏感資料 | **否**。Play 的「收集」是指資料傳出裝置；Sidepeek 讀到的前景 App、鍵盤狀態、返回鍵、（進階清理開啟時）LINE 標題列文字都只在手機上即時使用，不儲存、不上傳。 |
| 揭露畫面影片 | `https://youtu.be/YJk_wDQy_so`（11/19 上傳，不公開；檔案 `store/videos/video1-accessibility.mp4`） |

**用途說明（貼這段）：**

```
Sidepeek shows a small handle ("Peek bar") at the screen edge; swiping inward opens a side panel with recent message previews. The AccessibilityService is used only for this side panel feature:

1. Drawing the Peek bar and side panel as an accessibility overlay (TYPE_ACCESSIBILITY_OVERLAY), so no persistent foreground notification is needed.
2. Reading which app's window is in front (package name only) to support the optional "Show only on home screen" setting, and to detect when the user opens LINE so that the LINE notifications Sidepeek organized can be cleared (they are now read in LINE).
3. Detecting whether a keyboard window is on screen, to temporarily hide the Peek bar while typing.
4. Filtering key events only to catch the Back key while the side panel is open, so Back closes the panel. All other keys are passed through untouched.
5. Optional "Advanced clearing" (off by default, separate in-app consent every time it is turned on): reads only the title bar text at the top of LINE's chat screen and compares it on the device with chat names already shown in Sidepeek's notifications, so only that chat's notification is cleared.

The service never performs gestures or actions on behalf of the user (canPerformGestures=false), never reads chat content or other apps' screen content, and never stores, uploads or shares any data obtained through the API. All processing happens on the device in real time. Notification organizing works without the AccessibilityService; users who decline can use a "display over other apps" fallback instead.
```

審查員可能問的兩點（上面已寫進去，被追問時用這兩句回）：
- `flagRequestFilterKeyEvents`：只攔返回鍵，而且只在側邊欄開著時（`PanelAccessibilityService.onKeyEvent`）。
- `canRetrieveWindowContent`：判斷前景 App、鍵盤視窗需要它；讀文字只有進階清理，而且只限 LINE 最上方標題列。

---

## 二、前景服務聲明（specialUse）

位置：應用程式內容 → 前景服務權限（Foreground service permissions）。

| 欄位 | 填法 |
|---|---|
| 前景服務類型 | **Special use**（只有這一個） |
| 用到的功能 | 側邊欄的備用方式（使用者不想開無障礙時，用「顯示在其他 App 上層」） |
| 影片 | `https://youtu.be/_0th-HA7668`（11/19 上傳，不公開；檔案 `store/videos/video2-foreground-service.mp4`） |

**功能說明（貼這段）：**

```
EdgePanelService is a fallback for users who do not want to enable the AccessibilityService. It keeps the Peek bar (a small handle at the screen edge, drawn with SYSTEM_ALERT_WINDOW / TYPE_APPLICATION_OVERLAY) available over other apps, and opens the side panel with recent message previews when the user swipes inward from the handle. The service only runs while the user has turned on the side panel and chosen this fallback; a visible notification ("Message side panel running") is shown the whole time and has an action to open the panel. It stops as soon as the user turns the side panel off or enables the accessibility service instead.
```

**如果被延後或中斷，對使用者的影響（貼這段）：**

```
The Peek bar disappears from the screen edge and the user can no longer open the side panel by swiping, which is the feature the user explicitly turned on. The task is user-initiated and user-visible, cannot be deferred to a later time (the handle must be on screen while the user is using other apps), and none of the other foreground service types (e.g. dataSync, mediaPlayback, connectedDevice) describe an always-available edge overlay.
```

---

## 三、資料安全表單（Data safety）

| 問題 | 填法 | 原因 |
|---|---|---|
| 是否收集或分享任何必填的使用者資料類型？ | **否** | 訊息、頭像、圖片、設定都只存在手機上。Play 的定義：只在裝置上處理、不傳出去的不算收集。 |
| （填「否」之後不會再問加密、刪除要求） | — | |
| 帳號 | 不需要帳號，不用填刪除帳號網址 | |

送出前自己再確認一次的網路行為（都不算「收集」）：
- **下載 LINE 貼圖**：只送出貼圖編號，連 LINE 自己的網域，不帶訊息內容或帳號資料。
- **意見回饋**：開啟使用者自己的郵件 App，由使用者按寄出，Sidepeek 本身不傳送。
- **匯出診斷記錄**：用系統分享選單，由使用者自己決定分享到哪裡。
- **檢查新版本**：Play 版沒有。

---

## 四、影片拍攝步驟

共同準備：
- 用 **Play 版**錄（`assemblePlayDebug` 的 APK 標題會有小字「Test」，可以接受；之後有 Play 正式版更好）。
- **全新安裝**，或先到「設定 → 應用程式 → Sidepeek → 儲存空間 → 清除資料」，這樣同意畫面才會出現。
- 手機語言設成**英文**，審查員比較好懂。
- 用系統內建螢幕錄影；字幕可以後製加英文，或直接在影片說明裡寫。
- 上傳 YouTube，選「不公開」（Unlisted），**不要選「私人」**，審查員會看不到。

### 影片 1：無障礙（約 2～3 分鐘）

| # | 畫面 | 英文字幕 |
|---|---|---|
| 1 | 從桌面打開 Sidepeek | Opening Sidepeek |
| 2 | 照設定步驟走到「Try the side panel service」那步，按「Open Accessibility settings」 | The disclosure appears before the user is sent to Accessibility settings |
| 3 | 揭露畫面出現，**慢慢捲**，每一段都停一下讓字看得清楚 | Prominent disclosure: what is read, what it is used for, what it never does |
| 4 | 按 **Disagree**，出現 Toast | User declines: notification organizing still works |
| 5 | 再按一次同一個按鈕，揭露畫面又出現 | The disclosure can be shown again from the same button |
| 6 | 按 **Agree and continue** → 系統無障礙設定 → Installed apps → Sidepeek side panel → 打開 → 系統確認視窗按允許 | User consents and turns on the service |
| 7 | 回桌面，螢幕邊緣出現 Peek 條，往內滑打開側邊欄，列出訊息 | Core feature: Peek bar and side panel (accessibility overlay) |
| 8 | 按返回鍵，側邊欄關閉 | Back key closes the panel (only key that is filtered) |
| 9 | 打開「Show only on home screen」，切到別的 App，Peek 條消失；回桌面又出現 | Foreground app (package name only) is used for “Show only on home screen” |
| 10 | 點輸入框叫出鍵盤，Peek 條暫時隱藏 | Keyboard window detection hides the Peek bar while typing |
| 11 | 讓 LINE 收到一則訊息 → 通知欄有 Sidepeek 整理後的通知 → 打開 LINE → 通知被清掉 | Opening LINE clears the notifications Sidepeek organized |
| 12 | Settings → LINE notification clearing → 打開「Advanced clearing (experimental)」，出現第二個同意畫面，慢慢捲，按同意 | Advanced clearing: separate consent; reads only LINE’s title bar, on device |
| 13 | （可選）設定 → Accessibility → 關掉服務 | The user can turn it off any time |

### 影片 2：前景服務（約 1 分鐘）

| # | 畫面 | 英文字幕 |
|---|---|---|
| 1 | 先在系統設定關掉 Sidepeek 的無障礙服務 | Accessibility service off |
| 2 | Sidepeek 首頁按「Allow display over other apps (fallback)」→ 允許 | User chooses the overlay fallback |
| 3 | 下拉通知欄，看到「Message side panel running」常駐通知 | The foreground service notification is always visible |
| 4 | 切到其他 App，Peek 條在邊緣，往內滑打開側邊欄 | The overlay stays available over other apps |
| 5 | 首頁關掉「Side panel」開關，常駐通知和 Peek 條都消失 | The service stops when the user turns the feature off |

---

## 五、其他會問到的欄位

| 項目 | 建議 |
|---|---|
| 應用程式存取權（App access） | 全部功能不用登入。補充說明：`Requires LINE installed and receiving messages to see notification organizing. The side panel can show notifications from any app the user selects.` |
| 廣告 | 不含廣告 |
| 目標年齡層 | 13 歲以上（或 18 以上；不要勾兒童，會多一堆要求） |
| 內容分級問卷 | 類別選「實用工具／生產力／通訊或其他」；沒有暴力、色情等內容。「使用者能否互動或交換內容」：否，Sidepeek 本身不提供聊天，只整理其他 App 的通知 |
| 新聞、健康、金融、政府 App | 都選否 |
| 商店名稱／圖示 | 不能出現「LINE」字樣或 LINE 綠；說明裡寫「支援 LINE 通知」可以 |

---

## 還沒做

- 四種語言的商店文字已寫在 `play-listing-draft.md`；主題圖片在 `feature-graphic/`，手機截圖還沒做。
- 實際錄影和上傳。
- 網站 `privacy.html` 要 push 到 main 後才會更新。
