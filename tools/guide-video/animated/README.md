# 設定引導示範動畫（第 1、4、5、6 步）

`guide.html` 用網頁畫出 One UI 的設定畫面並依時間軸播放；`rec.js` 逐格截圖，再用 ffmpeg 編成 App 用的影片
（`app/src/main/res/raw/guide_step{1,4,5,6}.mp4`，720×1626、30fps，與 GuideVideoView 的 480:1084 比例相同）。

```bash
# guide.html 是 Artifact 格式（沒有 <html>/<body>），先包一層再錄
{ echo '<!doctype html><html><head><meta charset="utf-8"></head><body>'; cat guide.html; echo '</body></html>'; } > /tmp/guide-preview.html
mkdir -p /tmp/guide-rec && node rec.js /tmp/guide-preview.html /tmp/guide-rec     # 需要 Playwright（Chromium）
for s in step1 step4 step5 step6; do
  ffmpeg -y -framerate 30 -i /tmp/guide-rec/${s}_%04d.png -vf "scale=720:1626:flags=lanczos" \
    -c:v libx264 -preset veryfast -crf 23 -pix_fmt yuv420p -movflags +faststart -an guide_${s}.mp4
done
```

系統畫面的文字照三星 One UI 實際用詞；其他 App 一律畫成灰色區塊，不顯示真實清單。
