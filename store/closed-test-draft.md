# 封閉測試草稿（Sidepeek Play 版 1.2.0）

新的個人開發者帳號要有 **至少 12 位測試人員、連續 14 天都留在封閉測試裡**，才能申請正式版。
所以越早開始越好；商店截圖、聲明影片可以之後再補，但送審前 Console 的必填欄位都要先填好。

## 一、上傳前要決定：App 簽署金鑰

第一次上傳 .aab 時 Play Console 會問簽署方式，**選了之後就不能改**。

- **建議：使用現有的金鑰**（「Use a different key／使用其他金鑰」→ 照畫面用 PEPK 工具匯出 `sidepeek-release.jks`）。
  這樣 Play 版和 GitHub 版用同一個憑證（SHA-256 `da649b21…f07909f`），已經裝 GitHub 版的人可以直接從 Play 更新，以後也能互相覆蓋。
- 讓 Google 產生新金鑰：Play 版和 GitHub 版簽章不同，裝過 GitHub 版的人要先**解除安裝**（設定和訊息會不見）才能裝 Play 版。

PEPK 要輸入金鑰密碼：請自己在電腦上操作，不要把密碼傳給任何人。

## 二、Console 上的位置

測試與發布 → 測試 → **封閉測試** → 建立測試群組（例如「Sidepeek testers」）：

1. **測試人員**：選「電子郵件清單」，新增一份清單，把表單收到的 Gmail 一行一個貼上（或上傳 CSV）。
   「意見回饋網址或電子郵件」填 `sidepeek.app@gmail.com`。
2. 存檔後頁面下方會出現 **加入測試的連結**（opt-in URL，像 `https://play.google.com/apps/testing/app.sidepeek`），等等貼進邀請訊息。
3. **建立新版本** → 上傳 `sidepeek-1.2.0-play.aab` → 版本名稱填 `1.2.0` → 貼下面的版本說明 → 儲存 → 送審。
4. 國家／地區：至少勾台灣；要讓日韓或其他地方的人參加，就一起勾。

第一次審查可能要幾天。審過之後，測試人員打開連結才裝得到。

## 三、版本說明（Release notes）

每種語言最多 500 字元；整段貼進「版本說明」欄，Console 會依標籤分語言。

```
<zh-TW>
感謝參加 Sidepeek 測試！
・LINE 通知依聊天室整理，一次看到前面的訊息
・從螢幕邊緣的 Peek 條往內滑，打開側邊欄看最近訊息
・支援繁中、英文、日文、韓文（設定 → 其他 → 語言）
・設定步驟附操作示範影片

請照首頁的設定步驟開好權限，正常使用 LINE 就好。
遇到問題：到「說明」分頁按「寄信給開發者」。
請保留安裝並留在測試至少 14 天，謝謝！
</zh-TW>
<en-US>
Thanks for testing Sidepeek!
• LINE notifications grouped by chat, so earlier messages stay visible
• Swipe in from the Peek bar on the screen edge to open the side panel
• English, Traditional Chinese, Japanese and Korean (Settings → Other → Language)
• Demo videos in the setup steps

Follow the setup steps on the Home tab, then use LINE as usual.
Found a problem? Open the Help tab and tap "Email the developer".
Please keep the app and stay in the test for at least 14 days.
</en-US>
<ja-JP>
Sidepeekのテストにご参加いただきありがとうございます！
・LINEの通知をトークごとにまとめ、前のメッセージも見られます
・画面端のPeekバーから内側にスワイプしてサイドパネルを開けます
・日本語、英語、繁体字中国語、韓国語に対応（設定 → その他 → 言語）
・設定手順に操作のデモ動画付き

ホームの設定手順に沿って権限をオンにし、普段どおりLINEを使ってください。
問題があれば「ヘルプ」タブの「開発者にメール」から送ってください。
14日以上、アプリを入れたままテストに参加してください。
</ja-JP>
<ko-KR>
Sidepeek 테스트에 참여해 주셔서 감사합니다!
• LINE 알림을 채팅방별로 정리해 이전 메시지도 볼 수 있어요
• 화면 가장자리의 Peek 바에서 안쪽으로 밀면 사이드 패널이 열려요
• 한국어, 영어, 일본어, 중국어(번체) 지원(설정 → 기타 → 언어)
• 설정 단계에 데모 영상 제공

홈의 설정 단계대로 권한을 켠 뒤 평소처럼 LINE을 사용해 주세요.
문제가 있으면 '도움말' 탭에서 '개발자에게 메일 보내기'를 눌러 주세요.
14일 이상 앱을 설치한 채 테스트에 남아 주세요.
</ko-KR>
```

> 分頁、按鈕、選單名稱已對過四種語言的 strings（tab_help、ui_btn_feedback、grp_other、set_language）。
> 字數（node 算）：中 187、英 479、日 254、韓 275；英文接近 500 上限，要加字先刪別的。

## 四、寄給報名者的邀請（中文）

用你自己的信箱或群組寄；Play 不會替你寄通知信。`【加入測試連結】` 換成第二節拿到的網址。

```
主旨：Sidepeek 測試開始了！（約 3 分鐘完成）

你好，感謝你填表報名 Sidepeek 測試！Google Play 測試版已經開放，麻煩照下面做：

1. 用手機打開這個連結，登入你報名時填的 Gmail，按「成為測試人員」：
   【加入測試連結】
2. 同一頁會出現「在 Google Play 下載」，點進去安裝 Sidepeek。
   如果顯示找不到，請等幾分鐘再試，或確認 Play 商店登入的是同一個 Gmail。
3. 打開 Sidepeek，照首頁的設定步驟把權限開好，之後正常使用 LINE 就可以。

很重要：Google 規定要有 12 位測試者連續 14 天留在測試裡，Sidepeek 才能正式上架。
請在 14 天內不要按「退出測試計畫」，也盡量不要解除安裝。

已經裝了 GitHub 版的人：Play 商店會直接更新成測試版，設定和訊息都會保留。

遇到問題或有建議：在 App 的「說明」分頁按「寄信給開發者」，或直接回這封信。

謝謝你的幫忙！
Sidepeek
```

> 如果第一節選的是「讓 Google 產生新金鑰」，把「已經裝了 GitHub 版的人」那段改成：
> 「已經裝了 GitHub 版的人：請先解除安裝 GitHub 版（設定和訊息會清掉），再從 Play 商店安裝。」

## 五、送審前還要準備的（見 `play-console-draft.md`、`play-listing-draft.md`）

- 商店資訊：名稱、簡短說明、完整說明、**圖示 `store/icon/icon-512.png`**、主題圖片、手機截圖至少 2 張
- 應用程式內容：隱私權政策網址、App 存取權、廣告（無）、內容分級、目標年齡層、資料安全
- 無障礙 API 聲明、前景服務（specialUse）聲明，各要一支 YouTube「不公開」影片
