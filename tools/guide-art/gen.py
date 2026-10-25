"""Render the in-app guide illustrations (Sidepeek) as PNGs with Playwright.

Every image is a self-contained dark rounded tile so it reads on both the
light and dark app theme. Output: app/src/main/res/drawable-xxhdpi/<name>.png
"""
import sys, pathlib
from playwright.sync_api import sync_playwright

OUT = pathlib.Path(sys.argv[1])

CSS = r"""
*{box-sizing:border-box;margin:0;padding:0}
html,body{background:transparent}
body{font-family:'Noto Sans CJK TC','Noto Sans TC',sans-serif;color:#F2F3F5;-webkit-font-smoothing:antialiased}
.tile{position:relative;overflow:hidden;border-radius:44px;
  background:radial-gradient(120% 90% at 15% 0%,#22262B 0%,#17191C 55%,#131417 100%);
  box-shadow:inset 0 0 0 2px #2B2F35}
.g{color:#86EFAC}
.muted{color:#9AA0A6}
.num{display:inline-grid;place-items:center;width:52px;height:52px;border-radius:50%;
  background:#86EFAC;color:#052E16;font-weight:900;font-size:30px;flex:none}
.num.sm{width:44px;height:44px;font-size:26px}
.screen{position:absolute;overflow:hidden;border-radius:36px;
  background:linear-gradient(160deg,#2B3440 0%,#1D2229 60%,#191C21 100%);
  box-shadow:inset 0 0 0 3px #3A3F46}
.wall{position:absolute;border-radius:50%;filter:blur(2px)}
.handle{position:absolute;width:30px;border-radius:15px;
  background:linear-gradient(90deg,rgba(255,255,255,.30),rgba(255,255,255,.16));
  box-shadow:inset 0 0 0 2.5px rgba(255,255,255,.62),0 0 22px rgba(255,255,255,.20)}
.handle i{position:absolute;left:50%;width:7px;height:7px;margin-left:-3.5px;border-radius:50%;background:rgba(255,255,255,.85)}
.ring{position:absolute;border-radius:999px;box-shadow:0 0 0 5px #86EFAC,0 0 30px 6px rgba(134,239,172,.55)}
.tap{position:absolute;width:64px;height:64px;border-radius:50%;
  background:radial-gradient(circle,rgba(255,255,255,.95) 0 30%,rgba(255,255,255,.35) 31% 55%,rgba(255,255,255,0) 56%)}
.callout{position:absolute;display:flex;align-items:center;gap:14px;padding:12px 26px 12px 14px;border-radius:999px;
  background:#86EFAC;color:#052E16;font-weight:900;font-size:34px;white-space:nowrap;box-shadow:0 8px 24px rgba(0,0,0,.45)}
.callout .num{background:#052E16;color:#86EFAC}
.av{display:grid;place-items:center;border-radius:50%;font-weight:900;color:#fff;flex:none}
.card{background:#2A2E34;border-radius:26px;box-shadow:inset 0 0 0 2px #353A41}
svg{display:block}
"""

def handle(x, y, h=170, w=30, extra=""):
    dots = "".join(f'<i style="top:{h/2 - 18 + k*14:.0f}px"></i>' for k in range(3))
    return f'<div class="handle" style="left:{x}px;top:{y}px;height:{h}px;width:{w}px;{extra}">{dots}</div>'

def arrow_svg(x, y, w, h, d, color="#86EFAC", sw=7, head=True, extra=""):
    marker = ('<defs><marker id="ah" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="4" markerHeight="4" orient="auto-start-reverse">'
              f'<path d="M0,0 L10,5 L0,10 z" fill="{color}"/></marker></defs>')
    me = 'marker-end="url(#ah)"' if head else ''
    return (f'<svg style="position:absolute;left:{x}px;top:{y}px;overflow:visible;{extra}" width="{w}" height="{h}">{marker}'
            f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{sw}" stroke-linecap="round" {me}/></svg>')

def dbl_arrow(x, y, h, color="#86EFAC"):
    return (f'<svg style="position:absolute;left:{x}px;top:{y}px;overflow:visible" width="40" height="{h}">'
            f'<path d="M20 14 V{h-14}" stroke="{color}" stroke-width="7" stroke-linecap="round"/>'
            f'<path d="M4 26 L20 4 L36 26 Z M4 {h-26} L20 {h-4} L36 {h-26} Z" fill="{color}"/></svg>')

def msg_card(x, y, w, color, initial, name, time, body, extra_html=""):
    """Chat-app style: avatar, name + time, then the message in a speech bubble."""
    return f'''<div style="position:absolute;left:{x}px;top:{y}px;width:{w}px;display:flex;gap:14px">
      <div class="av" style="width:52px;height:52px;background:{color};font-size:24px">{initial}</div>
      <div style="flex:1;min-width:0">
        <div style="display:flex;justify-content:space-between;align-items:baseline">
          <span style="font-size:25px;font-weight:700">{name}</span><span class="muted" style="font-size:20px">{time}</span></div>
        <div style="display:inline-block;max-width:100%;box-sizing:border-box;margin-top:6px;background:#2E3136;border-radius:4px 22px 22px 22px;padding:11px 18px 12px">
          <div style="font-size:23px;color:#F2F2F7;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{body}</div>{extra_html}</div>
      </div></div>'''

# ---------------------------------------------------------------- 1. 怎麼用（總覽）
def steps_overview():
    W, H = 1080, 640
    def col(x, n, title, sub, visual):
        return f'''<div style="position:absolute;left:{x}px;top:0;width:300px;height:{H}px">
          <div style="position:absolute;left:0;top:44px;width:300px;height:380px">{visual}</div>
          <div style="position:absolute;left:0;top:452px;width:300px;display:flex;gap:14px;align-items:center">
            <span class="num">{n}</span><span style="font-size:40px;font-weight:900;letter-spacing:1px">{title}</span></div>
          <div class="muted" style="position:absolute;left:0;top:526px;width:300px;font-size:29px;line-height:1.35">{sub}</div>
        </div>'''
    # ① stacked notification -> one chat card
    v1 = f'''
      <div class="card" style="position:absolute;left:34px;top:44px;width:232px;height:120px;opacity:.35"></div>
      <div class="card" style="position:absolute;left:17px;top:66px;width:266px;height:120px;opacity:.6"></div>
      <div class="card" style="position:absolute;left:0;top:90px;width:300px;padding:20px 20px 22px">
        <div style="display:flex;align-items:center;gap:12px">
          <div class="av" style="width:48px;height:48px;background:#3E7BFA;font-size:22px">專</div>
          <div style="font-size:27px;font-weight:700;flex:1">專案群組</div>
          <div class="muted" style="font-size:21px">21:05</div></div>
        <div style="font-size:24px;line-height:1.55;margin-top:12px;color:#D5D8DC">
          <b style="color:#fff">小明</b>：報告好了嗎<br><b style="color:#fff">阿華</b>：我晚點傳<br><b style="color:#fff">小美</b>：收到</div>
      </div>'''
    # ② screen edge with handle
    v2 = f'''<div class="screen" style="left:20px;top:0;width:260px;height:380px">
        <div class="wall" style="left:-80px;top:170px;width:300px;height:300px;background:rgba(134,239,172,.10)"></div>
        <div class="wall" style="left:60px;top:-120px;width:260px;height:260px;background:rgba(80,140,255,.12)"></div>
        {handle(214, 150, 150)}
      </div>'''
    # ③ panel slid out
    v3 = f'''<div class="screen" style="left:20px;top:0;width:260px;height:380px">
        <div style="position:absolute;left:58px;top:26px;width:190px;height:328px;border-radius:26px;background:rgba(18,20,23,.94);
             box-shadow:inset 0 0 0 2px #3A3F46;padding:16px 14px;display:flex;flex-direction:column;gap:12px">
          {''.join(f'<div style="display:flex;gap:10px;align-items:center"><div class="av" style="width:38px;height:38px;background:{c};font-size:18px">{i}</div><div style="flex:1"><div style="height:11px;width:{a}px;border-radius:6px;background:#E6E8EB"></div><div style="height:9px;width:{b}px;border-radius:5px;background:#6B7178;margin-top:8px"></div></div></div>' for c,i,a,b in [("#3E7BFA","專",80,110),("#F2994A","媽",56,96),("#86EFAC","華",70,84),("#B05CF5","美",60,100),("#3E7BFA","公",76,90)])}
        </div>
      </div>
      {arrow_svg(140, 330, 170, 40, "M150 20 H18")}'''
    body = (col(40, 1, "整理成對話", "每個聊天室<br>只留一則通知", v1)
            + col(390, 2, "Peek 條", "邊緣的半透明膠條<br>不擋畫面", v2)
            + col(740, 3, "滑開看訊息", "從 Peek 條往內滑<br>打開側邊欄", v3))
    chev = ''.join(arrow_svg(x, 220, 30, 40, "M6 4 L22 20 L6 36", color="#4A5058", sw=6, head=False) for x in (349, 699))
    return W, H, f'<div class="tile" style="width:{W}px;height:{H}px">{body}{chev}</div>'

# ---------------------------------------------------------------- 2. 長按拖曳 / 往內滑
def handle_drag():
    W, H = 1080, 660
    def panel(x, n, title, sub, inner):
        return f'''<div style="position:absolute;left:{x}px;top:36px;width:486px;height:{H-72}px;border-radius:32px;background:rgba(255,255,255,.035);box-shadow:inset 0 0 0 2px #2B2F35">
          <div style="position:absolute;left:30px;top:28px;display:flex;gap:14px;align-items:center">
            <span class="num sm">{n}</span><span style="font-size:38px;font-weight:900">{title}</span></div>
          <div class="muted" style="position:absolute;left:30px;top:88px;font-size:28px">{sub}</div>
          {inner}</div>'''
    scr = lambda extra="": f'''<div class="screen" style="left:36px;top:150px;width:414px;height:400px;border-top-right-radius:44px;border-bottom-right-radius:44px">
            <div class="wall" style="left:-60px;top:200px;width:300px;height:300px;background:rgba(134,239,172,.09)"></div>
            <div class="wall" style="left:200px;top:-140px;width:300px;height:300px;background:rgba(80,140,255,.10)"></div>{extra}</div>'''
    p1 = scr(handle(368, 118, 160)
             + '<div class="tap" style="left:351px;top:166px"></div>'
             + dbl_arrow(290, 50, 300)
             + '<div style="position:absolute;left:206px;top:150px;font-size:30px;font-weight:900;color:#86EFAC;line-height:1.25">上下<br>拖動</div>')
    p2 = scr('''<div style="position:absolute;left:28px;top:40px;width:180px;height:320px;border-radius:24px;background:rgba(14,16,19,.96);box-shadow:inset 0 0 0 2px #3A3F46;padding:16px 12px;display:flex;flex-direction:column;gap:14px">'''
             + ''.join(f'<div style="display:flex;gap:10px;align-items:center"><div class="av" style="width:34px;height:34px;background:{c};font-size:16px">{i}</div><div style="flex:1"><div style="height:10px;width:{a}px;border-radius:5px;background:#E6E8EB"></div><div style="height:8px;width:{b}px;border-radius:4px;background:#6B7178;margin-top:7px"></div></div></div>' for c,i,a,b in [("#3E7BFA","專",70,96),("#F2994A","媽",50,86),("#86EFAC","華",64,78),("#B05CF5","美",54,90)])
             + '</div>' + handle(368, 118, 160)
             + arrow_svg(222, 178, 130, 40, "M120 20 H14"))
    body = panel(36, 1, "長按半秒再拖", "會震動一下，調整高度", p1) + panel(558, 2, "往內滑", "從 Peek 條往內滑，打開側邊欄", p2)
    return W, H, f'<div class="tile" style="width:{W}px;height:{H}px">{body}</div>'

# ---------------------------------------------------------------- 3. Peek 條外觀 + 不透明度
def samsung_handle():
    W, H = 1080, 330
    samples = ''
    for k, (op, lab) in enumerate([(.35, "淡"), (.7, "預設"), (1.0, "清楚")]):
        x = 70 + k * 150
        samples += (f'<div style="position:absolute;left:{x}px;top:84px;width:110px;height:200px;display:flex;justify-content:center">'
                    f'{handle(40, 0, 150, extra=f"opacity:{op}")}</div>'
                    f'<div class="muted" style="position:absolute;left:{x}px;top:250px;width:110px;text-align:center;font-size:28px">{lab}</div>')
    body = f'''
      <div style="position:absolute;left:62px;top:26px;font-size:30px;font-weight:900" class="g">不透明度：看不清楚就調高</div>
      {samples}
            <div class="screen" style="left:600px;top:34px;width:440px;height:262px;border-radius:30px 44px 44px 30px">
        <div class="wall" style="left:-40px;top:60px;width:320px;height:320px;background:rgba(80,140,255,.12)"></div>
        <div class="wall" style="left:160px;top:-160px;width:300px;height:300px;background:rgba(134,239,172,.08)"></div>
        <div style="position:absolute;left:40px;top:40px;width:220px;height:16px;border-radius:8px;background:rgba(255,255,255,.14)"></div>
        <div style="position:absolute;left:40px;top:72px;width:160px;height:16px;border-radius:8px;background:rgba(255,255,255,.10)"></div>
        {handle(392, 52, 160)}
      </div>
      <div style="position:absolute;left:640px;top:236px;font-size:26px;font-weight:700;color:#E6E8EB">螢幕邊緣的磨砂膠條</div>'''
    return W, H, f'<div class="tile" style="width:{W}px;height:{H}px">{body}</div>'

# ---------------------------------------------------------------- 4. 常見問題：側拉標籤與 Peek 條各自獨立
def faq_header():
    W, H = 1080, 470
    sys_handle = '<div style="position:absolute;left:276px;top:58px;width:16px;height:110px;border-radius:8px;background:rgba(190,196,204,.75)"></div>'
    body = f'''
      <div class="av" style="position:absolute;left:58px;top:150px;width:150px;height:150px;background:rgba(134,239,172,.16);color:#86EFAC;font-size:96px;box-shadow:inset 0 0 0 3px rgba(134,239,172,.5)">?</div>
      <div class="screen" style="left:260px;top:34px;width:300px;height:402px;border-radius:44px">
        <div class="wall" style="left:-60px;top:200px;width:300px;height:300px;background:rgba(80,140,255,.12)"></div>
        <div style="position:absolute;left:110px;top:16px;width:80px;height:22px;border-radius:11px;background:#0E0F11"></div>
        {sys_handle}
        {handle(262, 210, 150)}
      </div>
      {arrow_svg(578, 112, 90, 10, "M0 4 H80", color="#7C838B", sw=4, head=False)}
      {arrow_svg(578, 284, 90, 10, "M0 4 H80", color="#86EFAC", sw=4, head=False)}
      <div style="position:absolute;left:684px;top:72px">
        <div style="font-size:36px;font-weight:900;color:#D5D8DC">三星側拉標籤</div>
        <div class="muted" style="font-size:27px;margin-top:4px">系統捷徑、App 捷徑</div></div>
      <div style="position:absolute;left:684px;top:244px">
        <div style="font-size:36px;font-weight:900" class="g">Sidepeek Peek 條</div>
        <div class="muted" style="font-size:27px;margin-top:4px">收常用 App 的訊息</div></div>
      <div style="position:absolute;left:684px;top:376px;font-size:25px;color:#C9CDD2;padding:8px 18px;border-radius:999px;background:rgba(255,255,255,.06)">兩個各自獨立，可錯開位置</div>'''
    return W, H, f'<div class="tile" style="width:{W}px;height:{H}px">{body}</div>'

# ---------------------------------------------------------------- 5. 精靈：打開側邊欄
def step_sidebar():
    W, H = 1080, 720
    link_btn = '<div style="display:inline-block;margin-top:10px;padding:6px 16px;border-radius:999px;background:rgba(134,239,172,.18);color:#BBF7D0;font-size:21px;font-weight:700">開啟連結</div>'
    panel = (f'''<div style="position:absolute;left:36px;top:72px;width:520px;height:600px;border-radius:34px;background:rgba(14,16,19,.96);box-shadow:inset 0 0 0 2px #3A3F46,-10px 10px 50px rgba(0,0,0,.45)">
          <div style="position:absolute;left:28px;top:22px;font-size:30px;font-weight:900">最近訊息</div>'''
             + msg_card(20, 80, 480, "#3E7BFA", "專", "專案群組", "21:05", "<b>小明</b>：報告好了嗎？")
             + msg_card(20, 222, 480, "#F2994A", "媽", "媽媽", "20:48", "晚上回來吃飯嗎")
             + msg_card(20, 364, 480, "#86EFAC", "華", "阿華", "20:30", "這家看起來不錯 shop.example", link_btn)
             + '</div>')
    body = f'''
      <div class="screen" style="left:50px;top:40px;width:660px;height:720px;border-radius:52px 52px 0 0">
        <div class="wall" style="left:-80px;top:300px;width:420px;height:420px;background:rgba(80,140,255,.12)"></div>
        <div class="wall" style="left:360px;top:-120px;width:360px;height:360px;background:rgba(134,239,172,.08)"></div>
        <div style="position:absolute;left:36px;top:22px;font-size:24px;color:#C9CDD2">21:06</div>
        {panel}
        {handle(600, 240, 170)}
      </div>
      {arrow_svg(706, 300, 260, 60, "M240 30 H20")}
      <div style="position:absolute;left:760px;top:180px;font-size:38px;font-weight:900;line-height:1.3" class="g">從 Peek 條<br>往內滑</div>
      <div class="muted" style="position:absolute;left:760px;top:390px;font-size:30px;line-height:1.4">側邊欄就會<br>從邊緣滑出來</div>'''
    return W, H, f'<div class="tile" style="width:{W}px;height:{H}px">{body}</div>'

# ---------------------------------------------------------------- 6. 協助工具：打開「Sidepeek 側邊欄」
def a11y_toggle():
    W, H = 1080, 720
    crumb = lambda t, hi=False: (f'<span style="padding:8px 20px;border-radius:999px;font-size:28px;font-weight:700;'
                                 f'background:{"#86EFAC" if hi else "rgba(255,255,255,.08)"};color:{"#052E16" if hi else "#D5D8DC"}">{t}</span>')
    sep = '<span class="muted" style="font-size:30px">›</span>'
    def tog(on, x, y, scale=1):
        bg = "#86EFAC" if on else "#5A5F66"
        kx = 46 if on else 6
        return (f'<div style="position:absolute;left:{x}px;top:{y}px;width:92px;height:52px;border-radius:26px;background:{bg};transform:scale({scale});transform-origin:left top">'
                f'<div style="position:absolute;left:{kx}px;top:6px;width:40px;height:40px;border-radius:50%;background:#fff"></div></div>')
    body = f'''
      <div style="position:absolute;left:44px;top:36px;display:flex;gap:12px;align-items:center">
        {crumb("協助工具")}{sep}{crumb("已安裝的應用程式")}{sep}{crumb("Sidepeek 側邊欄", True)}</div>
      <div style="position:absolute;left:44px;top:132px;width:992px;height:556px;border-radius:36px;background:#0B0C0E;box-shadow:inset 0 0 0 2px #2B2F35">
        <div style="position:absolute;left:40px;top:34px;font-size:30px" class="muted">‹</div>
        <div style="position:absolute;left:84px;top:26px;font-size:36px;font-weight:700">Sidepeek 側邊欄</div>
        <div style="position:absolute;left:32px;top:110px;width:928px;height:110px;border-radius:30px;background:#26292E">
          <div style="position:absolute;left:36px;top:30px;font-size:36px;font-weight:700;color:#C9CDD2">關閉</div>
          {tog(False, 790, 29)}
        </div>
        <div class="ring" style="left:810px;top:128px;width:112px;height:74px"></div>
        <div class="callout" style="left:420px;top:236px"><span class="num sm">✓</span>把它打開</div>
        {arrow_svg(690, 196, 140, 60, "M10 50 Q90 50 112 14")}
        <div style="position:absolute;left:32px;top:340px;width:928px;height:186px;border-radius:30px;background:#1C1E22">
          <div style="position:absolute;left:36px;top:30px;font-size:32px;color:#C9CDD2">Sidepeek 側邊欄 捷徑</div>
          <div style="position:absolute;left:360px;top:32px;font-size:24px;padding:2px 14px;border-radius:999px;background:rgba(255,255,255,.08)" class="muted">不用開</div>
          {tog(False, 790, 26)}
          <div style="position:absolute;left:36px;top:94px;width:856px;height:2px;background:#2B2F35"></div>
          <div style="position:absolute;left:36px;top:118px;font-size:32px;color:#C9CDD2">設定</div>
        </div>
      </div>'''
    return W, H, f'<div class="tile" style="width:{W}px;height:{H}px">{body}</div>'

# ---------------------------------------------------------------- 7. 拒絕視窗：按「關閉」
def a11y_denied():
    W, H = 1080, 650
    bars = ''.join(f'<div style="position:absolute;left:60px;top:{40+k*86}px;width:{w}px;height:22px;border-radius:11px;background:rgba(255,255,255,.07)"></div>' for k, w in enumerate([300, 420, 260, 380]))
    body = f'''
            <div style="position:absolute;left:70px;top:150px;width:940px;height:460px;border-radius:44px;background:#26292E;box-shadow:0 20px 60px rgba(0,0,0,.6)">
        <svg style="position:absolute;left:446px;top:40px" width="48" height="56" viewBox="0 0 24 28"><path d="M12 1 L22 5 V13 C22 20 17.5 25 12 27 C6.5 25 2 20 2 13 V5 Z" fill="none" stroke="#8FB1F5" stroke-width="2.2"/><path d="M12 8 V15" stroke="#8FB1F5" stroke-width="2.2" stroke-linecap="round"/><circle cx="12" cy="19" r="1.4" fill="#8FB1F5"/></svg>
        <div style="position:absolute;left:0;top:116px;width:940px;text-align:center;font-size:44px;font-weight:700">應用程式存取權已遭拒絕</div>
        <div style="position:absolute;left:60px;top:200px;width:820px;font-size:28px;line-height:1.6;color:#B9BEC4">如果授予這項權限，你的個人資訊和財務資訊可能會面臨風險。如果未取得這項受限制權限，應用程式可能無法正常運作。</div>
        <div style="position:absolute;left:60px;top:326px;width:820px;height:96px;border-radius:48px;background:#8FB1F5;display:grid;place-items:center;font-size:36px;font-weight:700;color:#0E1A33">關閉</div>
        <div class="ring" style="left:52px;top:318px;width:836px;height:112px"></div>
        <div class="tap" style="left:560px;top:342px"></div>
      </div>
      <div class="callout" style="left:70px;top:44px"><span class="num sm">1</span>按「關閉」</div>
      <div class="callout" style="left:430px;top:44px;background:#E6E8EB"><span class="num sm" style="background:#052E16;color:#E6E8EB">2</span>回來按「下一步」</div>'''
    return W, H, f'<div class="tile" style="width:{W}px;height:{H}px">{body}</div>'

# ---------------------------------------------------------------- 8. App 資訊：⋮ → 允許受限設定
def a11y_restricted():
    W, H = 1080, 720
    icon = ('<svg width="96" height="96" viewBox="0 0 108 108" style="border-radius:26px">'
            '<rect width="108" height="108" fill="#16A34A"/>'
            '<rect x="16" y="24" width="66" height="50" rx="18" fill="#FFFFFF"/>'
            '<path d="M26 68 L42 72 L25 84 Z" fill="#FFFFFF"/>'
            '<rect x="29" y="40" width="34" height="7" rx="3.5" fill="#16A34A"/>'
            '<rect x="29" y="53" width="24" height="7" rx="3.5" fill="#86EFAC"/>'
            '<rect x="90" y="30" width="9" height="46" rx="4.5" fill="#DCFCE7"/>'
            '<circle cx="76" cy="26" r="11" fill="#16A34A"/><circle cx="76" cy="26" r="8" fill="#FACC15"/></svg>')
    body = f'''
      <div style="position:absolute;left:36px;top:36px;width:1008px;height:648px;border-radius:36px;background:#0B0C0E;box-shadow:inset 0 0 0 2px #2B2F35">
        <div style="position:absolute;left:40px;top:40px;font-size:36px" class="muted">‹</div>
        <div style="position:absolute;left:92px;top:32px;font-size:40px;font-weight:700">應用程式資訊</div>
        <div style="position:absolute;left:912px;top:22px;width:72px;height:72px;display:grid;place-items:center;font-size:48px;font-weight:900;line-height:1">⋮</div>
        <div class="ring" style="left:910px;top:20px;width:76px;height:76px"></div>
        <div style="position:absolute;left:44px;top:170px;display:flex;gap:28px;align-items:center;opacity:.55">{icon}
          <div><div style="font-size:38px;font-weight:700">Sidepeek</div><div class="muted" style="font-size:27px">已安裝</div></div></div>
        <div style="position:absolute;left:44px;top:330px;font-size:27px;opacity:.45" class="muted">隱私設定</div>
        <div style="position:absolute;left:28px;top:380px;width:952px;height:110px;border-radius:30px;background:#1C1E22;opacity:.5">
          <div style="position:absolute;left:36px;top:32px;font-size:32px">通知</div></div>
        <div style="position:absolute;left:470px;top:108px;width:500px;border-radius:28px;background:#2F3338;box-shadow:0 18px 50px rgba(0,0,0,.65);padding:14px">
          <div style="position:relative;padding:22px 26px;border-radius:20px;background:rgba(134,239,172,.16);font-size:34px;font-weight:700;color:#fff">允許受限設定
            <div class="ring" style="left:-4px;top:-4px;right:-4px;bottom:-4px;border-radius:22px"></div></div>
        </div>
        <div class="tap" style="left:820px;top:130px"></div>
      </div>
      <div class="callout" style="left:80px;top:566px"><span class="num sm">1</span>點右上角 ⋮</div>
      <div class="callout" style="left:436px;top:566px"><span class="num sm">2</span>允許受限設定</div>'''
    return W, H, f'<div class="tile" style="width:{W}px;height:{H}px">{body}</div>'


JOBS = {
    "img_steps_overview": steps_overview,
    "img_guide_handle_drag": handle_drag,
    "img_samsung_handle": samsung_handle,
    "img_faq_header": faq_header,
    "img_step_sidebar": step_sidebar,
}

if __name__ == "__main__":
    only = sys.argv[2:] or list(JOBS)
    OUT.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page(device_scale_factor=1)
        for name in only:
            W, H, html = JOBS[name]()
            pg.set_viewport_size({"width": W, "height": H})
            pg.set_content(f"<!doctype html><meta charset=utf-8><style>{CSS}</style><body>{html}</body>")
            pg.wait_for_timeout(150)
            pg.screenshot(path=str(OUT / f"{name}.png"), omit_background=True, clip={"x": 0, "y": 0, "width": W, "height": H})
            print("ok", name, W, H)
        b.close()
