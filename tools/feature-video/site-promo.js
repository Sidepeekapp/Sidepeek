// Builds a promo video from a site demo recording (site-rec.js) wrapped in a feature video's own
// title and end scenes (rec.js frames), then adds the operation sounds:
//   node site-promo.js <demo-dir> <feature-frames-dir> <title-end-sec> <end-start-sec> <out.mp4>
// Title = feature frames [0, title-end), end card = feature frames [end-start, last]; each joint is a
// 0.35 s cross-fade, like the scenes inside the feature videos. Sound cues come from <demo-dir>/sfx.json.
const path = require('path'), fs = require('fs'), os = require('os');
const { execFileSync } = require('child_process');

const FPS = 30, XF = 0.35;
// What happened in the demo → which synthesized sound (mix.js SOUNDS).
const SOUND_FOR = { tap: 'tap', swipe: 'swipe', notify: 'ding', clear: 'pop', toast: 'tick' };

const [, , demoDir, featDir, titleEnd, endStart, out] = process.argv;
const frames = (dir) => fs.readdirSync(dir).filter((f) => f.endsWith('.png')).sort();
const demoN = frames(demoDir).length, featN = frames(featDir).length;
const titleN = Math.round(+titleEnd * FPS), endFrom = Math.round(+endStart * FPS), endN = featN - endFrom;
const sec = (n) => n / FPS;

const silent = path.join(os.tmpdir(), 'sidepeek-promo-silent.mp4');
const o1 = sec(titleN) - XF, o2 = o1 + sec(demoN) - XF;
execFileSync('ffmpeg', ['-loglevel', 'error', '-y',
  '-framerate', FPS, '-start_number', 0, '-i', path.join(featDir, 'f_%04d.png'),
  '-framerate', FPS, '-start_number', 0, '-i', path.join(demoDir, 'f_%05d.png'),
  '-framerate', FPS, '-start_number', endFrom, '-i', path.join(featDir, 'f_%04d.png'),
  '-filter_complex',
  `[0]trim=end_frame=${titleN},setpts=PTS-STARTPTS,scale=720:1280,setsar=1[t];` +
  `[1]scale=720:1280,setsar=1[d];[2]setpts=PTS-STARTPTS,scale=720:1280,setsar=1[e];` +
  `[t][d]xfade=transition=fade:duration=${XF}:offset=${o1.toFixed(3)}[td];` +
  `[td][e]xfade=transition=fade:duration=${XF}:offset=${o2.toFixed(3)},format=yuv420p[v]`,
  '-map', '[v]', '-c:v', 'libx264', '-crf', '18', '-movflags', '+faststart', silent], { stdio: 'inherit' });

const duration = o2 + sec(endN);
const sfx = JSON.parse(fs.readFileSync(path.join(demoDir, 'sfx.json'), 'utf8'))
  .map(([t, k]) => [+(t + o1).toFixed(3), SOUND_FOR[k]]).filter(([, k]) => k);
const cues = path.join(os.tmpdir(), 'sidepeek-promo-cues.json');
fs.writeFileSync(cues, JSON.stringify({ sfx, duration }));
execFileSync('node', [path.join(__dirname, 'mix.js'), cues, silent, out], { stdio: 'inherit' });
console.log('duration', duration.toFixed(2), 's →', out);
