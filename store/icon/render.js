// 產生 Play 商店圖示 icon-512.png（512×512，32 位元 PNG）。
// 用法：node store/icon/render.js
// 需要：Chrome（無頭模式截圖）、ffmpeg（轉成 Play 要求的 32 位元 RGBA PNG）。
// 圖案來源是 icon.svg（照 App 的自適應圖示轉的）；改 App 圖示時要一起改 icon.svg。
const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const os = require("os");

const dir = __dirname;
const svg = "file:///" + path.join(dir, "icon.svg").replace(/\\/g, "/");
const chrome = [
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
].find(fs.existsSync);
if (!chrome) throw new Error("找不到 Chrome 或 Edge");

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "sp-icon-"));
const raw = path.join(tmp, "raw.png");
execFileSync(chrome, [
  "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
  `--user-data-dir=${path.join(tmp, "profile")}`, "--window-size=512,512",
  "--default-background-color=00000000", `--screenshot=${raw}`, svg,
], { stdio: "ignore" });
const out = path.join(dir, "icon-512.png");
execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", raw, "-pix_fmt", "rgba", out]);
fs.rmSync(tmp, { recursive: true, force: true });
console.log(out);
