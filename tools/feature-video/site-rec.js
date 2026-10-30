// Records one of the website's own phone demos (site/index.html) frame by frame, so the video shows
// exactly what the site plays: node site-rec.js <demo-id> <out-dir> [chrome.exe]
// Timers are driven by Playwright's fake clock and CSS transitions are stepped through the Web
// Animations API, one 1/30 s step per frame. Frames are 720×1280 (514×914 CSS px at 1.4×), the size
// of the feature videos. Writes <out-dir>/f_NNNNN.png and <out-dir>/sfx.json ([seconds, kind] pairs
// from what happened on screen: taps, panels sliding, notifications arriving).
const path = require('path'), fs = require('fs');
const { pathToFileURL } = require('url');
const { chromium } = require(process.env.PLAYWRIGHT_CORE || 'playwright-core');

// Per demo: the header above the phone, and when to stop (ms after the demo starts).
const DEMOS = {
  clearDemo: {
    tag: '用 Sidepeek v1.1.0', h: '打開 LINE，看過的通知自己收掉', sub: '預設全部收掉；進階清理只收你點進的聊天室。',
    foot: '功能介紹 #2 ・ 看過就收掉',
    // two rounds: default, then 進階清理; stop 3 s after the second toast
    done: (ev) => { const p = ev.filter(([, k]) => k === 'toast'); return p.length >= 2 ? p[1][0] + 3000 : null; },
  },
};

(async () => {
  const [, , id, out, exe] = process.argv;
  const demo = DEMOS[id];
  if (!demo) throw new Error('unknown demo ' + id);
  fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ executablePath: exe || 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const p = await b.newPage({ viewport: { width: 514, height: 914 }, deviceScaleFactor: 720 / 514 });
  await p.clock.install({ time: new Date('2026-10-27T13:24:00') });
  await p.goto(pathToFileURL(path.resolve(__dirname, '../../site/index.html')).href);
  await p.evaluate(() => document.fonts.ready);
  // From here on the page's time only moves when a frame is stepped (clock.runFor below).
  await p.clock.pauseAt(new Date('2026-10-27T13:25:00'));

  // Put the demo's phone on a stage styled like the feature videos (×514/360), hide the rest of the page.
  await p.evaluate(({ id, demo }) => {
    const s = document.createElement('style');
    s.textContent = `
      body > *:not(#rec) { display: none !important; }
      #rec { position: fixed; inset: 0; z-index: 99; color: #F4F5F8; font-family: "Noto Sans TC", sans-serif;
        background: radial-gradient(120% 70% at 20% 0%, #26244f 0%, rgba(38,36,79,0) 60%),
                    radial-gradient(90% 60% at 100% 100%, #3a1f4d 0%, rgba(58,31,77,0) 65%),
                    linear-gradient(180deg, #151833 0%, #10121f 55%, #160f22 100%); }
      #rec .tag { position: absolute; left: 31px; top: 46px; font-size: 15.5px; font-weight: 700; padding: 4px 11px; border-radius: 9px;
        background: rgba(34,197,94,.16); color: #22C55E; letter-spacing: .7px; }
      #rec .h { position: absolute; left: 31px; right: 31px; top: 84px; font-size: 29px; font-weight: 900; line-height: 1.3; }
      #rec .sub { position: absolute; left: 31px; right: 31px; top: 131px; font-size: 17px; color: #A3A8B8; line-height: 1.5; }
      #rec .ph { position: absolute; left: 87px; top: 196px; width: 340px; }
      #rec .foot { position: absolute; left: 31px; right: 31px; bottom: 24px; display: flex; justify-content: space-between; align-items: baseline; }
      #rec .logo { font-weight: 900; font-size: 20px; } #rec .logo i { font-style: normal; color: #22C55E; }
      #rec .foot small { font-size: 14px; color: #A3A8B8; }`;
    document.head.appendChild(s);
    const rec = document.createElement('div');
    rec.id = 'rec';
    rec.innerHTML = `<div class="tag">${demo.tag}</div><div class="h">${demo.h}</div><div class="sub">${demo.sub}</div>
      <div class="ph"></div><div class="foot"><div class="logo">Side<i>peek</i></div><small>${demo.foot}</small></div>`;
    document.body.appendChild(rec);
    const screen = document.getElementById(id);
    rec.querySelector('.ph').appendChild(screen.closest('.phone'));

    // Sound cues from class changes on the demo's elements (time in ms on the page's fake clock).
    window.__ev = [];
    const t0 = Date.now(), log = (k) => window.__ev.push([Date.now() - t0, k]);
    let chatClosedAt = -1e9;
    new MutationObserver((ms) => {
      const kinds = new Set();
      for (const m of ms) {
        const el = m.target, was = m.oldValue || '', now = el.className;
        const added = (c) => now.split(' ').includes(c) && !was.split(' ').includes(c);
        const removed = (c) => was.split(' ').includes(c) && !now.split(' ').includes(c);
        if (el.classList.contains('finger') && added('press')) kinds.add('tap');
        if (el.id === 'clearNew' && removed('hide')) kinds.add('notify');
        if (el.id === 'clearChat' && (added('on') || removed('on'))) kinds.add('swipe');
        if (el.id === 'clearChat' && removed('on')) chatClosedAt = Date.now();
        if (el.id === 'clearChat' && added('view')) kinds.add('swipe');
        if (el.classList.contains('ncard') && added('hide') && Date.now() - chatClosedAt < 2000) kinds.add('clear');
        if (el.classList.contains('pill') && added('on')) kinds.add('toast');
      }
      kinds.forEach(log);
    }).observe(screen, { subtree: true, attributes: true, attributeFilter: ['class'], attributeOldValue: true });
  }, { id, demo: { tag: demo.tag, h: demo.h, sub: demo.sub, foot: demo.foot } });
  await p.waitForTimeout(300);   // real time: let the IntersectionObserver see the phone

  // Steps every running CSS transition/animation to the fake clock, so each frame is exact.
  const syncAnimations = () => {
    const now = Date.now();
    for (const a of document.getAnimations()) {
      if (a.__v0 === undefined) { a.__v0 = now; a.pause(); }
      const t = now - a.__v0, end = a.effect.getComputedTiming().endTime;
      if (Number.isFinite(end) && t >= end) a.finish(); else a.currentTime = t;
    }
  };

  const shot = async (i) => p.screenshot({ path: path.join(out, `f_${String(i).padStart(5, '0')}.png`) });
  let stopAt = null;
  for (let i = 0; ; i++) {
    const t = Math.round(i * 1000 / 30);
    if (i > 0) await p.clock.runFor(t - Math.round((i - 1) * 1000 / 30));
    await p.evaluate(syncAnimations);
    await shot(i);
    if (stopAt === null) stopAt = demo.done(await p.evaluate(() => window.__ev));
    if (stopAt !== null && t >= stopAt) { console.log('frames', i + 1); break; }
    if (i > 30 * 90) throw new Error('demo never finished');
  }
  const ev = await p.evaluate(() => window.__ev);
  fs.writeFileSync(path.join(out, 'sfx.json'), JSON.stringify(ev.map(([ms, k]) => [ms / 1000, k])));
  console.log('events', JSON.stringify(ev));
  await b.close();
})();
