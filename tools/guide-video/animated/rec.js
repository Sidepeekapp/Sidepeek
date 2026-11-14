// 逐格截圖：node rec.js <guide-preview.html> <輸出資料夾> [語言 zh-TW|en|ja|ko]
// 沒裝 playwright 時會改用 playwright-core；用本機 Chrome 的話設環境變數 CHROME=<chrome.exe 路徑>。
const pw = (() => { try { return require('playwright'); } catch (e) { return require('playwright-core'); } })();
const { chromium } = pw;
(async () => {
  const [, , page, out, lang = 'zh-TW'] = process.argv;
  const b = await chromium.launch(process.env.CHROME ? { executablePath: process.env.CHROME } : {});
  const p = await b.newPage({ viewport: { width: 360, height: 813 }, deviceScaleFactor: 2 });
  const url = (page.startsWith('/') ? 'file://' : 'file:///') + page.replace(/\\/g, '/') + '#' + lang;
  await p.goto(url); await p.waitForTimeout(800);
  const scenes = await p.evaluate(() => window.__guide.scenes);
  const FPS = 30;
  for (const s of scenes) {
    await p.evaluate(x => window.__guide.solo(x), s.id); await p.waitForTimeout(150);
    const n = Math.round(s.dur * FPS);
    for (let i = 0; i < n; i++) {
      await p.evaluate(([x, y]) => window.__guide.seek(x, y), [s.id, i / FPS]);
      await p.screenshot({ path: `${out}/${s.id}_${String(i).padStart(4, '0')}.png`, clip: { x: 0, y: 0, width: 360, height: 813 } });
    }
    await p.reload(); await p.waitForTimeout(500);
    console.log(s.id, n);
  }
  await b.close();
})();
