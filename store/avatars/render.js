// 示範人物頭像（Tom 企鵝、Ann 貓、Mia 狐狸、「專案」群組火箭）的唯一原稿是這個資料夾的 *.svg。
// 用到它們的地方：
//   ① 主題圖片 store/feature-graphic/feature.html 直接讀 ../avatars/*.svg（改完要重跑那邊的 render.js）
//   ② 測試版截圖示範資料 ScreenshotDemo.kt 讀 PNG：這支程式把 SVG 轉成
//      app/src/debug/res/drawable-nodpi/demo_avatar_*.png（192×192，方形底色，App 自己裁成圓形）
// 用法：node store/avatars/render.js
// 需要：Chrome（無頭模式截圖）、ffmpeg。
const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const os = require("os");

const dir = __dirname;
const outDir = path.join(dir, "../../app/src/debug/res/drawable-nodpi");
const chrome = [
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
].find(fs.existsSync);
if (!chrome) throw new Error("找不到 Chrome 或 Edge");

fs.mkdirSync(outDir, { recursive: true });
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "sp-avatar-"));
for (const name of ["tom", "ann", "mia", "group"]) {
  const svg = "file:///" + path.join(dir, `${name}.svg`).replace(/\\/g, "/");
  const raw = path.join(tmp, `${name}.png`);
  execFileSync(chrome, [
    "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
    `--user-data-dir=${path.join(tmp, "profile")}`, "--window-size=192,192",
    `--screenshot=${raw}`, svg,
  ], { stdio: "ignore" });
  const out = path.join(outDir, `demo_avatar_${name}.png`);
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", raw, "-pix_fmt", "rgb24", out]);
  console.log(out);
}
fs.rmSync(tmp, { recursive: true, force: true });
