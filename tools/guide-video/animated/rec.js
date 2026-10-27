const { chromium } = require('playwright');
(async () => {
  const [, , page, out] = process.argv;
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 360, height: 813 }, deviceScaleFactor: 2 });
  await p.goto('file://' + page); await p.waitForTimeout(800);
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
