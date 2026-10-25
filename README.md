# Sidepeek — for LINE

> 非官方的個人 Android App，與 LINE、LY Corporation 無關。Sidepeek 原名「訊息預覽」。

把 LINE 疊成一團的通知，依聊天室整理成對話預覽；從螢幕邊緣的 Peek 條一拉，就能在側邊欄看最近的訊息。所有處理都在手機上完成，沒有後端伺服器。

- 支援 Android 8.0 以上
- 主要以 Samsung One UI＋手勢導航、右手單手操作來設計
- 最新版本：**v1.0**（[下載 APK](../../releases/latest)）

---

## 功能

- **依聊天室分開**：群組和一對一各自一則通知，不會全部擠在一起。
- **用 Sidepeek 接管 LINE**（總開關）：每個聊天室整合成一則通知，隱藏 LINE 原本的訊息通知；**來電不會被隱藏**。
- **通知行為對齊 LINE 原生**：每則新訊息都會提醒；在通知上回覆或按已讀後，通知會跟著更新或消失；把通知滑掉後，下一則只顯示新訊息，不會把舊的帶回來。
- **側邊欄**：放最近的訊息，卡片可以滑掉，往內滑就關閉。
- **自己挑 App**：預設收 LINE、IG、Threads、Messenger、Teams，也可以從手機上的任何 App 挑選要收進側邊欄的（只有 LINE 的通知會被整理，其他 App 維持原樣）。
- **網址變按鈕**：點一下開啟，長按複製。
- **點了直接跳過去**：點訊息會打開那則通知原本的目標（例如 Threads 的那則貼文、Messenger 的那個聊天室）；在 Threads／IG 點頭像會打開對方的個人頁面。
- **貼圖**：抓得到就顯示圖片，抓不到就顯示佔位圖。
- **鎖定畫面**：鎖定時也能顯示 Peek 條、打開面板看訊息（可在設定關掉）。

## Peek 條（邊緣把手）

- 一條半透明的磨砂膠條，預設在**右側、約螢幕 3/4 高度**，剛好是右手拇指好按的位置。
- **從 Peek 條往內滑**就能打開側邊欄；輕點不會打開，避免碰到邊緣就誤觸。
- **長按 Peek 條就能拖曳**，上下調到你順手的位置。
- 會顯示在其他 App 上層；鍵盤打開時自動淡出。
- Peek 條是 Sidepeek 自己的，**不是**三星的側拉標籤／側螢幕面板（Edge Panel）。Android 和三星不開放第三方 App 掛進系統的側螢幕面板，所以只能這樣做。

## 安裝與第一次設定

1. 到 [Releases](../../releases/latest) 下載 `sidepeek-v1.0.apk` 並安裝（可能要先允許「安裝未知應用程式」）。
   - **用過「訊息預覽」的話**：Sidepeek 是它改名後的版本，套件名稱不同，會裝成另一個 App。請先解除安裝舊的「訊息預覽」，再裝 Sidepeek，照引導重設一次即可。
2. 打開 App，照著**設定引導**一步一步按下去，大約 8 步。每一步都會直接帶你到系統裡正確的那一頁，主要包括：
   - **通知存取權**：讓 App 讀得到 LINE 的通知。
   - **協助工具（側邊欄服務）**：見下方說明。
   - **顯示在其他 App 上層**：只有沒開協助工具時才需要。
   - **停用電池最佳化**：避免背景被系統關掉。

### 開協助工具時跳出「受限設定」怎麼辦？

Android 13 以上，從 APK 安裝的 App 預設不能開協助工具。請照這個順序：

1. 先到 **設定 → 協助工具 → 已安裝的應用程式 → Sidepeek 側邊欄**，試著開啟。
2. 如果被擋住、顯示「受限設定」，再到 **設定 → 應用程式 → Sidepeek**，按右上角 **⋮ → 允許受限設定**。
3. 回到協助工具，重新開啟就可以了。

> 請先試開協助工具，再去 App 資訊。直接先進 App 資訊，「允許受限設定」可能不會出現。

### 協助工具會讀我的畫面嗎？

不會。它只用來判斷你是不是在主畫面、鍵盤有沒有打開，好決定 Peek 條要不要顯示、放在哪裡，**不會讀取畫面上的文字**。

## 建議設定（Samsung）

- **電池**：設定 → 應用程式 → Sidepeek → 電池 → **不受限制**。
- **永不休眠的應用程式**：設定 → 電池 → 背景使用限制 → 把「Sidepeek」加進去。
- 想讓某個聊天室安靜：在 LINE 裡關掉該聊天室的提醒，或長按通知設成靜音。
- 同一則 LINE 訊息響兩次的話：到系統的 LINE 通知頁，把「聲音和震動」關掉（「允許通知」保持開啟）。平常不用關，Sidepeek 藏起 LINE 的通知時，聲音也會一起停掉。

## 隱私

- 所有處理都在手機上完成：沒有伺服器、沒有帳號、沒有廣告或分析工具。
- 只透過 Android 的通知存取權讀取你選的 App 的通知，資料留在手機裡；唯一的連網是下載 LINE 的公開貼圖圖片。
- 完整內容見 [隱私權政策](docs/privacy.md)（App 內首頁最下方也有）。

## 更新紀錄

### v1.0
「訊息預覽」改名為 Sidepeek 後的第一版，原本的功能全部保留，另外：

- **新名稱、新圖示**：綠底對話框圖示，App 主色改成綠色（刻意跟 LINE 的綠不同色）。
- **修正網址誤判**：名稱長得像網址的使用者（例如 Threads／IG 的帳號）不會再出現網址按鈕；同一個網址只會出現一顆按鈕。
- **說明圖全部重畫**，文字對齊實際設定步驟。
- **常見問題改成卡片排版**，新增「為什麼通知欄的整理只有 LINE」。
- 「送測試通知」改成群組對話範例。
- **點頭像開個人頁、點訊息開那則貼文**；修正 Threads 同一個讚出現兩次、每則都顯示同一個頭像。
- **邊緣把手改名「Peek 條」**，跟三星的側拉標籤分清楚。
- **Peek 條改成只有往內滑才打開**，輕點不再打開，減少誤觸；距離邊緣預設 0dp。
- **側邊欄改成聊天室的對話泡泡樣式**：頭像、聊天室名稱和時間在上，訊息在泡泡裡。
- **側邊欄可以收任何 App**：預設 LINE、IG、Threads、Messenger、Teams，其他 App 在設定裡搜尋、勾選即可加入。

## 自行編譯

```bash
./gradlew :app:assembleDebug
```

---

## English

**Sidepeek for LINE** is an unofficial personal Android app (not affiliated with LINE or LY Corporation). It regroups stacked LINE notifications into per-chat previews and adds an edge handle (the “Peek bar”) that opens a side panel with recent messages. Everything runs on-device; no server, no analytics. Requires Android 8.0+. Designed for Samsung One UI with gesture navigation.

Setup: install the APK from Releases, open the app, and follow the in-app setup guide. On Android 13+, if the accessibility service is blocked as a "restricted setting", try enabling it first, then go to App info → ⋮ → Allow restricted settings, and enable it again. The accessibility service only detects the home screen and keyboard state. It never reads on-screen text.

