"""訊息預覽 MAX launcher icon generator (v3).

Base design = the very first v1 icon (recovered verbatim): LINE green #06C755 square with the
white rounded speech bubble path below, plus a bold "MAX" set inside the bubble.
Outputs vector resources (no photo) and a squircle PNG preview.
Usage: python tools/make_icon.py [preview.png]      (needs fontTools, Pillow, numpy, cairosvg optional)
"""
import os, sys, re
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'app', 'src', 'main', 'res')
FONT = '/usr/share/fonts/truetype/sand-box/google/Poppins/Poppins-ExtraBold.ttf'

# v1 bubble, 108x108 viewport (spans x22-86, y26-87)
V1_BUBBLE = ("M54,26C36.3,26 22,37.6 22,52c0,12.9 11.4,23.7 26.9,25.7 1,0.2 2.5,0.7 2.8,1.6 0.3,0.8 0.2,2.1 "
             "0.1,2.9l-0.5,2.7c-0.1,0.8 -0.6,3.2 2.8,1.7 3.4,-1.4 18.4,-10.8 25.1,-18.6C83.8,62.9 86,57.7 86,52 "
             "86,37.6 71.7,26 54,26z")
GREEN, GREEN_DARK, GREEN_LIGHT = '#06C755', '#04A847', '#22D86B'
# bubble is scaled into the 66dp safe zone (pivot = bubble body centre)
B_SCALE, B_PIV = 0.86, (54, 54)
TEXT_BOX = (54, 52.5, 35)   # centre x, centre y (bubble coords), target width of "MAX"
TRACKING = 1.5              # extra letter spacing (font units / 100)

def max_path():
    f = TTFont(FONT); gs = f.getGlyphSet(); cmap = f.getBestCmap(); upm = f['head'].unitsPerEm
    names = [cmap[ord(c)] for c in 'MAX']
    # measure
    adv, xs = 0, []
    for i, n in enumerate(names):
        xs.append(adv); adv += gs[n].width + (TRACKING * upm / 100 if i < 2 else 0)
    bp = BoundsPen(gs)
    for x, n in zip(xs, names):
        gs[n].draw(TransformPen(bp, (1, 0, 0, 1, x, 0)))
    x0, y0, x1, y1 = bp.bounds
    s = TEXT_BOX[2] / (x1 - x0)
    cx, cy = TEXT_BOX[0], TEXT_BOX[1]
    sp = SVGPathPen(gs, ntos=lambda v: f'{v:.2f}'.rstrip('0').rstrip('.'))
    for x, n in zip(xs, names):
        # font y-up -> viewport y-down, centred on (cx, cy)
        t = (s, 0, 0, -s, cx - (x0 + x1) / 2 * s + x * s, cy + (y0 + y1) / 2 * s)
        gs[n].draw(TransformPen(sp, t))
    return sp.getCommands()

HDR = '<?xml version="1.0" encoding="utf-8"?>\n'
AAPT = '\n    xmlns:aapt="http://schemas.android.com/aapt"'
def vec(body, size=108, vp=108, ns=''):
    return (HDR + f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
            f'    android:width="{size}dp" android:height="{size}dp"\n'
            f'    android:viewportWidth="{vp}" android:viewportHeight="{vp}">\n{body}</vector>\n')
def group(body, s=B_SCALE, piv=B_PIV, dx=0, dy=0):
    return (f'    <group android:scaleX="{s}" android:scaleY="{s}" android:pivotX="{piv[0]}" android:pivotY="{piv[1]}"'
            f' android:translateX="{dx}" android:translateY="{dy}">\n{body}    </group>\n')
def path(d, color, alpha=None, evenodd=False):
    s = f'        <path android:pathData="{d}" android:fillColor="{color}"'
    if alpha is not None: s += f' android:fillAlpha="{alpha}"'
    if evenodd: s += ' android:fillType="evenOdd"'
    return s + '/>\n'
def write(rel, text):
    p = os.path.join(RES, rel); os.makedirs(os.path.dirname(p), exist_ok=True); open(p, 'w').write(text)

def main(preview):
    MAXP = max_path()
    write('drawable/ic_launcher_background.xml', vec(
        '    <path android:pathData="M0,0h108v108h-108z">\n        <aapt:attr name="android:fillColor">\n'
        f'            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54" android:endY="108"\n'
        f'                android:startColor="#FF{GREEN_LIGHT[1:]}" android:centerColor="#FF{GREEN[1:]}" android:endColor="#FF{GREEN_DARK[1:]}"/>\n'
        '        </aapt:attr>\n    </path>\n', ns=AAPT))
    fg = (group(path(V1_BUBBLE, '#FF003A18', alpha='0.16'), dy=1.4) +          # soft drop shadow
          group(path(V1_BUBBLE, '#FFFFFFFF') + path(MAXP, '#FF' + GREEN[1:])))
    write('drawable/ic_launcher_foreground.xml', vec(fg))
    write('drawable/ic_launcher_monochrome.xml', vec(group(path(V1_BUBBLE + MAXP, '#FF000000', evenodd=True))))
    # notification small icon: bubble with MAX punched out (24dp)
    write('drawable/ic_stat_msg.xml', vec(
        f'    <group android:scaleX="0.355" android:scaleY="0.355" android:translateX="-7.2" android:translateY="-7.2">\n'
        + path(V1_BUBBLE + MAXP, '#FFFFFFFF', evenodd=True) + '    </group>\n', size=24, vp=24))
    adaptive = lambda mono: (HDR + '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_launcher_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        + ('    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n' if mono else '')
        + '</adaptive-icon>\n')
    for v, mono in (('v26', False), ('v33', True)):
        write(f'mipmap-anydpi-{v}/ic_launcher.xml', adaptive(mono))
        write(f'mipmap-anydpi-{v}/ic_launcher_round.xml', adaptive(mono))
    # remove the old photo-based rasters
    for d in os.listdir(RES):
        if d.startswith('mipmap-') and 'anydpi' not in d:
            for fn in ('ic_launcher_foreground.png', 'ic_launcher.png', 'ic_launcher_round.png'):
                p = os.path.join(RES, d, fn)
                if os.path.exists(p): os.remove(p)
            if not os.listdir(os.path.join(RES, d)): os.rmdir(os.path.join(RES, d))
    render_preview(preview)

def svg_of(rel, w):
    """Tiny VectorDrawable -> SVG converter (enough for our own files)."""
    x = open(os.path.join(RES, rel)).read()
    x = re.sub(r'<\?xml[^>]*>', '', x)
    out = []
    for m in re.finditer(r'<(group|path|/group)([^>]*)>', x, re.S):
        tag, a = m.group(1), m.group(2)
        g = lambda k, d=None: (re.search(rf'android:{k}="([^"]*)"', a) or [None, d])[1]
        if tag == 'group':
            s, px, py = float(g('scaleX', 1)), float(g('pivotX', 0)), float(g('pivotY', 0))
            tx, ty = float(g('translateX', 0)), float(g('translateY', 0))
            out.append(f'<g transform="translate({tx + px},{ty + py}) scale({s}) translate({-px},{-py})">')
        elif tag == '/group': out.append('</g>')
        else:
            col = g('fillColor')
            if col and col.startswith('#') and len(col) == 9: col = '#' + col[3:]
            fa = g('fillAlpha', '1'); rule = 'evenodd' if g('fillType') == 'evenOdd' else 'nonzero'
            if col: out.append(f'<path d="{g("pathData")}" fill="{col}" fill-opacity="{fa}" fill-rule="{rule}"/>')
            else:  # our gradient background
                out.append(f'<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{GREEN_LIGHT}"/>'
                           f'<stop offset=".5" stop-color="{GREEN}"/><stop offset="1" stop-color="{GREEN_DARK}"/></linearGradient></defs>'
                           f'<rect width="108" height="108" fill="url(#bg)"/>')
    return ''.join(out)

def render_preview(png, px=512):
    import numpy as np
    from PIL import Image
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{px}" height="{px}" viewBox="18 18 72 72">'
           + svg_of('drawable/ic_launcher_background.xml', px) + svg_of('drawable/ic_launcher_foreground.xml', px) + '</svg>')
    open('/tmp/icon_v3.svg', 'w').write(svg)
    import subprocess
    subprocess.run(['rsvg-convert', '-w', str(px * 2), '-h', str(px * 2), '/tmp/icon_v3.svg', '-o', '/tmp/icon_v3_raw.png'], check=True)
    im = Image.open('/tmp/icon_v3_raw.png').convert('RGBA'); N = im.size[0]
    c = (np.arange(N, dtype=np.float32) + .5 - N / 2) / (N / 2)
    m = Image.fromarray(((np.abs(c)[None] ** 5 + np.abs(c)[:, None] ** 5) <= 1).astype(np.uint8) * 255)
    out = Image.new('RGBA', (N, N), (0, 0, 0, 0)); out.paste(im, (0, 0), m)
    out.resize((px, px), Image.LANCZOS).save(png)
    out.resize((48 * 3, 48 * 3), Image.LANCZOS).save('/tmp/icon_v3_144.png')
    print('wrote', png)

if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else '/workspace/icon-preview-v3.png')
