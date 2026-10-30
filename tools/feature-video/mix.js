// Adds operation sound effects to a recorded feature video: node mix.js <page.html | cues.json> <in.mp4> <out.mp4> [chrome.exe]
// Reads window.SFX ([seconds, kind] pairs) from the page, or { sfx, duration } from a JSON file;
// every sound is synthesized by ffmpeg, no audio files are used.
const path = require('path'), fs = require('fs'), os = require('os');
const { execFileSync } = require('child_process');
const { chromium } = require(process.env.PLAYWRIGHT_CORE || 'playwright-core');

// kind → [ffmpeg source filter, extra filters]; t is seconds from the sound's start.
const SOUNDS = {
  tap: ["aevalsrc='0.45*sin(2*PI*2200*t)*exp(-90*t)+0.3*sin(2*PI*1100*t)*exp(-60*t)':s=44100:d=0.1", ""],
  tick: ["aevalsrc='0.16*sin(2*PI*3000*t)*exp(-150*t)':s=44100:d=0.05", ""],
  swipe: ["anoisesrc=d=0.35:c=pink:a=0.35:r=44100", ",bandpass=f=1800:w=1600,afade=t=in:d=0.08,afade=t=out:st=0.1:d=0.25"],
  pop: ["aevalsrc='0.3*sin(2*PI*(500+900*t)*t)*exp(-28*t)':s=44100:d=0.15", ""],
  shutter: ["anoisesrc=d=0.22:c=white:a=0.5:r=44100", ",highpass=f=1500,volume='exp(-70*t)+gte(t,0.1)*exp(-70*(t-0.1))':eval=frame"],
  ding: ["aevalsrc='0.32*sin(2*PI*1319*t)*exp(-6*t)*lt(t,0.3)+0.32*sin(2*PI*988*(t-0.28))*exp(-4*(t-0.28))*gte(t,0.28)':s=44100:d=1.2", ""],
  buzz: ["aevalsrc='0.22*sin(2*PI*150*t)*gt(sin(2*PI*5*t),-0.3)':s=44100:d=0.75", ",lowpass=f=400"],
};

(async () => {
  const [, , page, input, output, exe] = process.argv;
  let sfx, dur;
  if (page.endsWith('.json')) {
    ({ sfx, duration: dur } = JSON.parse(fs.readFileSync(page, 'utf8')));
  } else {
    const b = await chromium.launch({ executablePath: exe || 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
    const p = await b.newPage();
    await p.goto('file:///' + path.resolve(page).replace(/\\/g, '/') + '?rec');
    [sfx, dur] = await p.evaluate(() => [window.SFX || [], window.DURATION]);
    await b.close();
  }

  const chains = sfx.map(([t, kind], i) => {
    const [src, fx] = SOUNDS[kind];
    const ms = Math.round(t * 1000);
    return `${src}${fx},adelay=${ms}:all=1[a${i}]`;
  });
  const graph = chains.join(';\n') + `;\n${sfx.map((_, i) => `[a${i}]`).join('')}amix=inputs=${sfx.length}:normalize=0,apad=whole_dur=${dur}[aout]`;
  const script = path.join(os.tmpdir(), 'sidepeek-sfx.txt');
  fs.writeFileSync(script, graph);
  execFileSync('ffmpeg', ['-loglevel', 'error', '-y', '-i', input, '-filter_complex_script', script,
    '-map', '0:v', '-map', '[aout]', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '128k', '-movflags', '+faststart', output], { stdio: 'inherit' });
  console.log('sfx', sfx.length, '→', output);
})();
