// 產生 Play 主題圖片（1024×500，24 位元 PNG，無透明度）四種語言版本。
// 用法：node store/feature-graphic/render.js
// 需要：Chrome（無頭模式截圖）、ffmpeg（去掉 alpha 通道，Play 要求 24 位元 PNG）。
const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const os = require("os");

const dir = __dirname;
const html = "file:///" + path.join(dir, "feature.html").replace(/\\/g, "/");
const chrome = [
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
].find(fs.existsSync);
if (!chrome) throw new Error("找不到 Chrome 或 Edge");

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "sp-feature-"));
for (const lang of ["zh-TW", "en", "ja", "ko"]) {
  const raw = path.join(tmp, `raw-${lang}.png`);
  execFileSync(chrome, [
    "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
    `--user-data-dir=${path.join(tmp, "profile")}`, "--window-size=1024,500",
    `--screenshot=${raw}`, `${html}#${lang}`,
  ], { stdio: "ignore" });
  const out = path.join(dir, `feature-${lang}.png`);
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", raw, "-pix_fmt", "rgb24", out]);
  console.log(out);
}
fs.rmSync(tmp, { recursive: true, force: true });
