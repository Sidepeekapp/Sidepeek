const fs=require('fs');const path=require('path');
const icons=require('./sidepeek-icons.js');
const svg=(ic,color,size)=>`<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-linecap="round" stroke-linejoin="round">${
  ic.parts.map(p=>`<path d="${p.d}" stroke-width="${p.w||2}"${p.o?` opacity="${p.o}"`:''}${p.fill?` fill="${color}" stroke-width="0.01"`:''}/>`).join('')}</svg>`;
const panel=(bg,fg,green,sub)=>`
<div style="background:${bg};color:${fg};padding:28px 32px;border-radius:24px;margin-bottom:24px">
 <div style="font-size:22px;font-weight:700;margin-bottom:18px">${sub}</div>
 <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:22px 12px">
 ${icons.map(ic=>`<div style="text-align:center">
   <div style="display:flex;gap:14px;justify-content:center;align-items:flex-end">${svg(ic,green,48)}${svg(ic,green,24)}</div>
   <div style="font-size:15px;margin-top:8px;opacity:.85">${ic.label}${ic.tab?'（分頁列）':''}</div></div>`).join('')}
 </div>
 <div style="margin-top:22px;background:${bg==='#111214'?'#1d1e21':'#f0f1f3'};border-radius:18px;padding:6px 0">
 ${icons.filter(i=>!i.tab).slice(0,3).map(ic=>`<div style="display:flex;align-items:center;padding:12px 18px;gap:18px">${svg(ic,green,24)}<div><div style="font-weight:700;font-size:17px">${ic.label}</div><div style="opacity:.6;font-size:13px">設定清單裡的樣子</div></div></div>`).join('')}
 </div>
</div>`;
const html=`<!doctype html><html><head><meta charset="utf-8"><style>body{margin:0;padding:24px;background:#888;font-family:"Microsoft JhengHei","Noto Sans TC",sans-serif;width:820px}</style></head><body>
${panel('#111214','#f2f2f2','#4ADE80','深色模式')}
${panel('#ffffff','#1a1a1a','#16A34A','淺色模式')}
</body></html>`;
fs.writeFileSync(path.join(__dirname,'preview.html'),html);console.log('ok');
