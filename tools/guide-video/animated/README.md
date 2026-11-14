# 設定引導示範動畫（第 1、4、5、6 步，及第 1 步被擋時的 step1r）

`guide.html` 用網頁畫出 One UI 的設定畫面並依時間軸播放；`rec.js` 逐格截圖，再用 ffmpeg 編成 App 用的影片
（`app/src/main/res/raw/guide_step{1,1r,4,5,6}.mp4`，720×1626、30fps，與 GuideVideoView 的 480:1084 比例相同）。

```bash
# guide.html 是 Artifact 格式（沒有 <html>/<body>），先包一層再錄
{ echo '<!doctype html><html><head><meta charset="utf-8"></head><body>'; cat guide.html; echo '</body></html>'; } > /tmp/guide-preview.html
mkdir -p /tmp/guide-rec && node rec.js /tmp/guide-preview.html /tmp/guide-rec     # 需要 Playwright（Chromium）
for s in step1 step1r step4 step5 step6; do
  ffmpeg -y -framerate 30 -i /tmp/guide-rec/${s}_%04d.png -vf "scale=720:1626:flags=lanczos" \
    -c:v libx264 -preset veryfast -crf 23 -pix_fmt yuv420p -movflags +faststart -an guide_${s}.mp4
done
```

系統畫面的文字照三星 One UI 實際用詞；其他 App 一律畫成灰色區塊，不顯示真實清單。

## 四種語言（2026-11-14 起）

文字都在 `guide.html` 最上面的 `GUIDE_TEXT`，用網址 `#zh-TW`／`#en`／`#ja`／`#ko` 切換；`rec.js` 第三個參數是語言。
影片放的位置（Android 依 App 語言自動挑）：

| 語言 | 資料夾 |
|---|---|
| 英文（預設，跟 `values/` 一樣） | `res/raw/` |
| 繁體中文 | `res/raw-zh-rTW/` |
| 日文 | `res/raw-ja/` |
| 韓文 | `res/raw-ko/` |

2026-11-15 已照 Galaxy（One UI 9）四種語言的實機錄影核對第 1、4、5、6 步的用詞
（無障礙清單多了 TalkBack 列、「⋮」選單只剩一項、通知存取頁沒有說明列）。
還沒在實機看到、仍是推測的：中文和日文的「允許通知存取」視窗、日文和韓文的「存取權已遭拒絕」視窗、日文的電池頁。
`step1r` 是原生 Android 畫面，沒有核對。
四種語言都在 Windows 上用本機 Chrome 錄。

Windows 上的錄法（不用 python）：

```bash
# playwright-core 裝在 repo 外的暫存資料夾即可
NODE_PATH=<暫存>/node_modules CHROME="C:/Program Files/Google/Chrome/Application/chrome.exe" \
  node rec.js <包好的 guide-preview.html> <輸出資料夾> en
```

`step1r`（第 1 步通知存取被「受限制的設定」擋住時）畫的是原生 Android（華碩、Pixel）的畫面與用詞，不是 One UI。
沒裝 Playwright 的話，可以只裝 `playwright-core`，在 `chromium.launch` 傳 `executablePath` 指到本機的 Chrome。
