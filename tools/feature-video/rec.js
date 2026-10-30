// Records a feature video page frame by frame: node rec.js <page.html> <out-dir> [chrome.exe]
// The page exposes window.seek(t) and window.DURATION; frames are 360×640 at 2× (720×1280), 30 fps.
const path = require('path');
const { chromium } = require(process.env.PLAYWRIGHT_CORE || 'playwright-core');
(async () => {
  const [, , page, out, exe] = process.argv;
  const b = await chromium.launch({ executablePath: exe || 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const p = await b.newPage({ viewport: { width: 360, height: 640 }, deviceScaleFactor: 2 });
  await p.goto('file:///' + path.resolve(page).replace(/\\/g, '/') + '?rec');
  await p.evaluate(() => document.fonts.ready);
  await p.waitForTimeout(500);
  const FPS = 30;
  const n = Math.round(await p.evaluate(() => window.DURATION) * FPS);
  for (let i = 0; i < n; i++) {
    await p.evaluate(t => window.seek(t), i / FPS);
    await p.screenshot({ path: `${out}/f_${String(i).padStart(4, '0')}.png` });
  }
  console.log('frames', n);
  await b.close();
})();
