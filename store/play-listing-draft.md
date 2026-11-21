# Play 商店文字草稿（四語）

2026-11-14 草稿，依 Play 版功能撰寫（沒有 GitHub 檢查更新）。字數限制：名稱 30、簡短說明 80、完整說明 4000（用 node 算過，見最下面的字數表）。
日文、韓文是初稿，上架前最好請母語者看過。

Play 商店文字規則（照著寫了）：
- 名稱、圖示、開發者名稱不能用「LINE」、三星商標，也不能用「免費」「最好」「#1」這類字。
- 說明裡可以寫「支援 LINE 通知」，並註明非官方、與 LINE 無關。
- 不要堆關鍵字、不要放其他 App 的評價或排名、不要放太多表情符號。

---

## 繁體中文（zh-TW，預設語言）

**名稱**
```
Sidepeek：訊息側邊欄
```

**簡短說明**
```
LINE 通知依聊天室整理，從螢幕邊緣一滑就能看最近訊息。
```

**完整說明**
```
LINE 的通知常常只留下最新一則，訊息一多，前面說了什麼就看不到了。Sidepeek 把 LINE 的通知依聊天室整理成對話預覽，再加上螢幕邊緣的「Peek 條」：往內一滑，就能在側邊欄看最近的訊息。

所有處理都在手機上完成，沒有伺服器、不用註冊，沒有廣告，也沒有應用程式內購買。

■ LINE 通知整理
• 每個聊天室一則對話通知，群組和一對一分開，不會全部擠在一起
• 每則新訊息都會提醒；在通知上回覆或按已讀，通知會跟著更新或消失
• 點通知進聊天室、或直接打開 LINE 看過訊息，整理好的通知會自動收掉
• 群組裡顯示每個人的頭貼；沒頭貼的人用名字第一個字和固定顏色區分
• 可以換通知鈴聲、震動模式（也能自己錄一段節奏）、狀態列小圖示，也能自訂群組頭貼
• 來電通知不會被隱藏

■ 側邊欄與 Peek 條
• 螢幕邊緣一條細細的 Peek 條，往內滑打開側邊欄，輕點不會誤開
• 側邊欄放最近的訊息，除了 LINE，也能收 Instagram、Threads、Messenger、Teams，或手機上任何你選的 App（只有 LINE 的通知會被整理，其他 App 維持原樣）
• 網址變成按鈕，點一下開啟；點訊息直接跳到那則通知原本的位置
• Peek 條的位置、寬高、不透明度（可以調到 0% 完全看不到）、滑動靈敏度都能調，設定頁有即時預覽
• 可設定只在桌面顯示、打字時隱藏、鎖定畫面也顯示
• 也可以從快速設定按鈕、App 捷徑、桌面小工具打開
• 桌面小工具「最近訊息」不用打開側邊欄就能看，點 × 就能刪除單則

■ 支援語言
繁體中文、English、日本語、한국어，可以在 App 裡單獨切換。

■ 權限說明
• 通知存取權：讀取 LINE 和你選的 App 的通知，才能整理和放進側邊欄。
• 無障礙服務（可不開）：只用來顯示 Peek 條和側邊欄、判斷是不是在桌面或鍵盤是否打開、側邊欄開著時接收返回鍵，以及打開 LINE 時收掉整理好的通知。預設不讀取任何畫面文字；開啟前會先說明並請你同意。不開的話可以改用「顯示在其他 App 上層」。
• 讀取使用情況（可不開）：讓「只在桌面顯示 Peek 條」判斷得更準。
• 網路：只用來下載 LINE 貼圖圖片。

訊息、頭像和設定都只存在你的手機上，不會上傳。
隱私權政策：https://sidepeekapp.github.io/Sidepeek/privacy.html

Sidepeek 是開源軟體（GPL-3.0）：https://github.com/Sidepeekapp/sidepeek
本 App 為非官方的個人作品，與 LINE、LY Corporation 及其他 App 的開發商無關。
問題回報：sidepeek.app@gmail.com，或在 App 裡「說明 → 遇到問題」匯出診斷記錄。
```

---

## English (en-US)

**Title**
```
Sidepeek: Message Side Panel
```

**Short description**
```
LINE notifications sorted by chat, plus a side panel you open from the edge.
```

**Full description**
```
LINE notifications often keep only the latest message, so earlier ones are easy to miss when a chat gets busy. Sidepeek organizes LINE notifications into one conversation per chat, and adds a slim "Peek bar" at the screen edge: swipe in to open a side panel with your recent messages.

Everything happens on your phone. No server, no sign-up, no ads, no in-app purchases.

■ Organized LINE notifications
• One conversation notification per chat; groups and one-on-one chats stay separate
• Every new message still alerts you; reply or mark as read right from the notification
• Notifications clear themselves once you've read the chat, from the notification or by opening LINE
• Group members show their own profile pictures; people without one get their initial in a fixed color
• Choose the notification sound, vibration pattern (or record your own rhythm) and status bar icon, and set custom group pictures
• Incoming call notifications are never hidden

■ Side panel and Peek bar
• Swipe in from the slim Peek bar to open the side panel; a tap won't open it by accident
• Besides LINE, the side panel can collect Instagram, Threads, Messenger, Teams or any app you pick (only LINE notifications are reorganized; other apps stay as they are)
• Links become buttons; tap a message to jump to where its notification leads
• Adjust the Peek bar's position, size, opacity (down to 0%, fully invisible) and swipe sensitivity, with a live preview
• Show it only on the home screen, hide it while typing, or show it on the lock screen too
• Open the panel from a Quick Settings tile, an app shortcut or a home screen widget
• The "Recent messages" home screen widget shows them at a glance; tap × to delete one

■ Languages
English, Traditional Chinese, Japanese and Korean, switchable inside the app.

■ Permissions
• Notification access: to read notifications from LINE and the apps you choose, so they can be organized and shown in the side panel.
• Accessibility service (optional): only to show the Peek bar and side panel, tell whether you're on the home screen or typing, receive the Back key while the panel is open, and clear organized notifications when you open LINE. By default it reads no text on the screen, and you are asked to agree before turning it on. Without it, a "display over other apps" fallback is available.
• Usage access (optional): makes "Show only on home screen" more accurate.
• Internet: only to download LINE sticker images.

Messages, profile pictures and settings stay on your phone and are never uploaded.
Privacy policy: https://sidepeekapp.github.io/Sidepeek/privacy.html

Sidepeek is open source (GPL-3.0): https://github.com/Sidepeekapp/sidepeek
This is an unofficial, independent app and is not affiliated with LINE, LY Corporation or the developers of any other app.
Feedback: sidepeek.app@gmail.com, or export a diagnostic log from Help → Having trouble? in the app.
```

---

## 日本語（ja-JP）

**名前**
```
Sidepeek：メッセージサイドパネル
```

**簡単な説明**
```
LINE の通知をトークごとに整理。画面の端からスワイプして最近のメッセージを確認。
```

**詳しい説明**
```
LINE の通知は最新の 1 件しか残らないことが多く、メッセージが続くと前の内容が見えなくなります。Sidepeek は LINE の通知をトークごとの会話にまとめ、画面の端に細い「Peek バー」を表示します。内側にスワイプすると、サイドパネルで最近のメッセージを確認できます。

処理はすべてスマホの中で行われます。サーバーなし、登録不要、広告なし、アプリ内課金なし。

■ LINE 通知の整理
• トークごとに 1 つの会話通知。グループと個別トークは分けて表示
• 新しいメッセージごとに通知。通知から返信・既読にすると通知も更新・消去
• 通知からトークを開くか、LINE を開いて読むと、整理した通知は自動で消えます
• グループではメンバーごとのプロフィール画像を表示。画像がない人は名前の頭文字と決まった色で表示
• 通知音・バイブのパターン（自分でリズムを録音することも可能）・ステータスバーのアイコンを変更、グループ画像のカスタマイズも可能
• 着信の通知は隠しません

■ サイドパネルと Peek バー
• 細い Peek バーから内側にスワイプしてサイドパネルを開きます。タップだけでは開かないので誤操作しにくい設計
• LINE のほか、Instagram、Threads、Messenger、Teams など、選んだアプリの通知もサイドパネルに表示（整理するのは LINE の通知だけで、ほかのアプリはそのまま）
• URL はボタンに。メッセージをタップすると、通知の本来の移動先を開きます
• Peek バーの位置・サイズ・不透明度（0% で完全に見えなくすることも可能）・スワイプ感度を調整でき、設定画面でリアルタイムにプレビュー
• ホーム画面だけで表示、入力中は隠す、ロック画面にも表示、を選べます
• クイック設定タイル、アプリのショートカット、ホーム画面のウィジェットからも開けます
• ホーム画面のウィジェット「最近のメッセージ」で一覧でき、× で 1 件ずつ削除できます

■ 対応言語
日本語、English、繁體中文、한국어。アプリ内で切り替えられます。

■ 権限について
• 通知へのアクセス：LINE と選んだアプリの通知を読み取り、整理してサイドパネルに表示するため。
• ユーザー補助サービス（任意）：Peek バーとサイドパネルの表示、ホーム画面かどうか・キーボードが開いているかの判定、パネル表示中の戻るキーの受け取り、LINE を開いたときに整理した通知を消すためだけに使います。初期設定では画面の文字を一切読み取りません。オンにする前に説明を表示し、同意を求めます。使わない場合は「他のアプリの上に重ねて表示」で代用できます。
• 使用状況へのアクセス（任意）：「ホーム画面だけで表示」の判定をより正確にするため。
• インターネット：LINE スタンプ画像のダウンロードだけに使います。

メッセージ、プロフィール画像、設定はスマホの中だけに保存され、アップロードされません。
プライバシーポリシー：https://sidepeekapp.github.io/Sidepeek/privacy.html

Sidepeek はオープンソース（GPL-3.0）です：https://github.com/Sidepeekapp/sidepeek
本アプリは非公式の個人開発アプリで、LINE、LY Corporation、その他のアプリの開発元とは関係ありません。
お問い合わせ：sidepeek.app@gmail.com（アプリの「ヘルプ → 困ったときは」から診断ログを書き出せます）
```

---

## 한국어（ko-KR）

**앱 이름**
```
Sidepeek: 메시지 사이드 패널
```

**간단한 설명**
```
LINE 알림을 채팅방별로 정리하고, 화면 가장자리에서 밀어 최근 메시지를 확인하세요.
```

**자세한 설명**
```
LINE 알림은 최신 메시지 하나만 남는 경우가 많아, 메시지가 쌓이면 앞의 내용을 놓치기 쉽습니다. Sidepeek은 LINE 알림을 채팅방별 대화로 정리하고, 화면 가장자리에 얇은 'Peek 바'를 띄웁니다. 안쪽으로 밀면 사이드 패널에서 최근 메시지를 볼 수 있습니다.

모든 처리는 휴대폰 안에서 이루어집니다. 서버 없음, 가입 없음, 광고 없음, 인앱 결제 없음.

■ LINE 알림 정리
• 채팅방마다 하나의 대화 알림. 그룹과 1:1 채팅은 따로 표시
• 새 메시지마다 알림. 알림에서 답장하거나 읽음 처리하면 알림도 함께 갱신·삭제
• 알림으로 채팅방에 들어가거나 LINE을 열어 읽으면, 정리된 알림이 자동으로 사라집니다
• 그룹에서는 멤버마다 프로필 사진을 표시. 사진이 없는 사람은 이름 첫 글자와 고정된 색으로 구분
• 알림음, 진동 패턴(리듬 직접 녹음 가능), 상태 표시줄 아이콘 변경, 그룹 사진 직접 지정 가능
• 전화 알림은 숨기지 않습니다

■ 사이드 패널과 Peek 바
• 얇은 Peek 바에서 안쪽으로 밀어 사이드 패널을 엽니다. 탭만으로는 열리지 않아 실수로 열릴 일이 적습니다
• LINE 외에도 Instagram, Threads, Messenger, Teams 등 선택한 앱의 알림을 사이드 패널에 모을 수 있습니다(정리하는 것은 LINE 알림뿐이고, 다른 앱은 그대로)
• 링크는 버튼으로 표시. 메시지를 탭하면 알림이 원래 가리키는 곳으로 이동
• Peek 바의 위치, 크기, 불투명도(0%로 완전히 안 보이게도 가능), 스와이프 감도를 조절하고 설정 화면에서 바로 미리보기
• 홈 화면에서만 표시, 입력 중 숨기기, 잠금 화면에도 표시를 선택할 수 있습니다
• 빠른 설정 타일, 앱 바로가기, 홈 화면 위젯으로도 열 수 있습니다
• 홈 화면 위젯 '최근 메시지'에서 바로 확인하고, ×로 하나씩 삭제할 수 있습니다

■ 지원 언어
한국어, English, 繁體中文, 日本語. 앱 안에서 바꿀 수 있습니다.

■ 권한 안내
• 알림 접근: LINE과 선택한 앱의 알림을 읽어 정리하고 사이드 패널에 표시하기 위해 필요합니다.
• 접근성 서비스(선택): Peek 바와 사이드 패널 표시, 홈 화면인지·키보드가 열려 있는지 판단, 패널이 열려 있을 때 뒤로 가기 키 받기, LINE을 열 때 정리된 알림 지우기에만 사용합니다. 기본적으로 화면의 글자는 전혀 읽지 않으며, 켜기 전에 설명을 보여 주고 동의를 받습니다. 사용하지 않으면 '다른 앱 위에 표시'로 대신할 수 있습니다.
• 사용 정보 접근(선택): '홈 화면에서만 표시'를 더 정확하게 판단하기 위해 사용합니다.
• 인터넷: LINE 스티커 이미지를 내려받을 때만 사용합니다.

메시지, 프로필 사진, 설정은 휴대폰에만 저장되며 업로드되지 않습니다.
개인정보처리방침: https://sidepeekapp.github.io/Sidepeek/privacy.html

Sidepeek은 오픈소스(GPL-3.0)입니다: https://github.com/Sidepeekapp/sidepeek
이 앱은 비공식 개인 앱으로, LINE, LY Corporation 및 다른 앱의 개발사와 관련이 없습니다.
문의: sidepeek.app@gmail.com (앱의 '도움말 → 문제가 있나요'에서 진단 기록을 내보낼 수 있습니다)
```

---

## 圖片素材

| 素材 | 規格 | 內容建議 |
|---|---|---|
| App 圖示 | 512×512 PNG，32 位元含透明 | 用現在的 Sidepeek 圖示（`mipmap` 原稿放大，不要直接放大小圖） |
| 主題圖片（Feature graphic） | 1024×500 JPG 或 24 位元 PNG，**必填** | 左邊名稱「Sidepeek」＋一句話（各語言一張，或只放名稱不放字），右邊手機露出 Peek 條和側邊欄；品牌綠 #16A34A，不要用 LINE 綠 |
| 手機截圖 | 至少 2 張、最多 8 張；9:16 直向，邊長 320–3840px（建議 1080×1920 以上） | 見下面清單 |

### 截圖（11/15 已完成，四語各 6 張）

**成品**：`store/screenshots/<zh-TW|en|ja|ko>/01.png～06.png`（1080×1920），照順序上傳到各語言的「手機螢幕截圖」。實機原圖在 `store/screenshots/raw/`，版型 `frame.html`（四語標語寫在裡面），`node store/screenshots/render.js` 重新產生。手機原圖 1440×3120 超過 Play「長邊不能大於短邊兩倍」，所以一定要用成品。

**示範資料**：測試版（截圖版 `./gradlew assemblePlayDebug -Pshots`）長按圖示 →「放入截圖示範資料」，假名字 Tom、Ann、Mia、「專案」群組，動物頭像（`store/avatars/`，跟主題圖片同一份）。**手機系統語言**要切成該語言、Sidepeek 語言選「跟隨系統」再拍，不然通知時間、時鐘、小工具標題會是別的語言。

| # | 畫面 | 標語（中／英，日韓見 frame.html） |
|---|---|---|
| 1 | 通知欄：「專案」群組展開＋Tom、Ann、Mia 三則一對一 | 每個聊天室一則通知／One notification per chat |
| 2 | 桌面打開側邊欄 | 從邊緣一滑，看最近訊息／Swipe in to read recent messages |
| 3 | 桌面，Peek 條不透明度 100%、寬 10dp；後製加放大圈和「從這裡往內滑」 | 細細一條，不擋畫面／Slim and out of the way |
| 4 | 設定 → 側邊欄外觀（寬度 63%） | 大小、位置都能調／Size and position, your way |
| 5 | 設定 → Peek 條樣式 | 粗細、長短、透明度都能調／Adjust width, length and opacity |
| 6 | 桌面小工具「最近訊息」（每則有 ×） | 桌面小工具也能看／Also on a home screen widget |

截圖時記得：拍下一張前先清掉上一張的截圖通知、等截圖工具列消失；狀態列（電信商、通知圖示）後製會裁掉。

---

## 字數（node 計算，Play 上限：名稱 30／簡短 80／完整 4000）

```
繁體中文（zh-TW，預設語言） title 14 /30  short 29 /80  full 1208 /4000
English (en-US) title 28 /30  short 76 /80  full 2906 /4000
日本語（ja-JP） title 20 /30  short 42 /80  full 1532 /4000
한국어（ko-KR） title 20 /30  short 48 /80  full 1632 /4000
```
