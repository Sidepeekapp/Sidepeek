// 產生 Play 商店手機截圖（1080×1920 PNG，24 位元無透明度）。
// 用法：node store/screenshots/render.js [語言…]（不給就做 raw/ 底下所有語言）
// 輸入：store/screenshots/raw/<語言>/0N-*.jpg（實機截圖，N＝1～6 對應 frame.html 的標語）
// 輸出：store/screenshots/<語言>/0N.png
// 需要：Chrome（無頭模式截圖）、ffmpeg（去掉 alpha）。
const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const os = require("os");

const dir = __dirname;
const html = "file:///" + path.join(dir, "frame.html").replace(/\\/g, "/");
const chrome = [
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
].find(fs.existsSync);
if (!chrome) throw new Error("找不到 Chrome 或 Edge");

const langs = process.argv.slice(2).length ? process.argv.slice(2)
  : fs.readdirSync(path.join(dir, "raw")).filter(d => fs.statSync(path.join(dir, "raw", d)).isDirectory());
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "sp-shots-"));
for (const lang of langs) {
  const outDir = path.join(dir, lang);
  fs.mkdirSync(outDir, { recursive: true });
  for (const f of fs.readdirSync(path.join(dir, "raw", lang)).filter(f => /^0[1-6]-.*\.(jpe?g|png)$/i.test(f)).sort()) {
    const n = +f.slice(0, 2);
    const raw = path.join(tmp, `${lang}-${n}.png`);
    execFileSync(chrome, [
      "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
      "--allow-file-access-from-files", `--user-data-dir=${path.join(tmp, "profile")}`,
      "--window-size=1080,1920", "--virtual-time-budget=3000", `--screenshot=${raw}`,
      `${html}#lang=${lang}&n=${n}&file=raw/${lang}/${f}`,
    ], { stdio: "ignore" });
    const out = path.join(outDir, `0${n}.png`);
    execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", raw, "-pix_fmt", "rgb24", out]);
    console.log(out);
  }
}
fs.rmSync(tmp, { recursive: true, force: true });
