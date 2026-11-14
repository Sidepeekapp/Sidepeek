// Checks the translations: every string / array in values/ (English, the default) must exist in
// each values-xx folder with the same format placeholders (%1$s, %2$d…) and the same number of
// array items, and no folder may have names the default doesn't.
// Usage: node tools/i18n-check.js
const fs = require('fs'), path = require('path');
const res = path.join(__dirname, '../app/src/main/res');
function load(dir) {
  const out = new Map();
  for (const f of fs.readdirSync(path.join(res, dir)).filter(f => /^strings.*\.xml$/.test(f))) {
    const x = fs.readFileSync(path.join(res, dir, f), 'utf8').replace(/<!--[\s\S]*?-->/g, '');
    for (const m of x.matchAll(/<string name="(\w+)"([^>]*)>([\s\S]*?)<\/string>/g)) {
      if (/translatable="false"/.test(m[2])) continue;
      out.set(m[1], { kind: 'string', text: m[3] });
    }
    for (const m of x.matchAll(/<string-array name="(\w+)">([\s\S]*?)<\/string-array>/g))
      out.set(m[1], { kind: 'array', items: [...m[2].matchAll(/<item>([\s\S]*?)<\/item>/g)].map(i => i[1]) });
  }
  return out;
}
const ph = t => (t.match(/%\d\$[sd]/g) || []).sort().join(' ');
const base = load('values');
let bad = 0;
for (const dir of fs.readdirSync(res).filter(d => /^values-(?!night)/.test(d) && fs.readdirSync(path.join(res, d)).some(f => f.startsWith('strings')))) {
  const tr = load(dir);
  for (const [name, v] of base) {
    const t = tr.get(name);
    if (!t) { console.log(`${dir}: missing ${name}`); bad++; continue; }
    if (v.kind === 'string' && ph(v.text) !== ph(t.text)) { console.log(`${dir}: placeholders differ in ${name}: [${ph(v.text)}] vs [${ph(t.text)}]`); bad++; }
    if (v.kind === 'array' && v.items.length !== t.items.length) { console.log(`${dir}: ${name} has ${t.items.length} items, expected ${v.items.length}`); bad++; }
  }
  for (const name of tr.keys()) if (!base.has(name)) { console.log(`${dir}: extra ${name}`); bad++; }
  console.log(`${dir}: ${tr.size} entries checked`);
}
console.log(bad ? `${bad} problems` : 'all translations complete');
process.exit(bad ? 1 : 0);
