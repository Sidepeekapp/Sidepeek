"""Cut the setup screen recording into short annotated guide clips for the wizard.

usage: python3 make.py <recording.mp4> <out_dir>

Each clip: trimmed to the system-settings part of one wizard step, other apps' names blurred,
a green ring on what to tap, a caption band on top (replaces the status bar), slowed down,
last frame held. Times below are seconds after each segment's -ss start (same as the
contact sheets used to time them); coordinates are in the 1080x2340 source.
"""
import pathlib, subprocess, sys
from PIL import Image, ImageDraw, ImageFont

SRC, OUT = sys.argv[1], pathlib.Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)
TMP = OUT / "_parts"; TMP.mkdir(exist_ok=True)
FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"   # face 3 = Traditional Chinese
TOP = 110            # status bar height cropped away
BAND = 210           # caption band height
SPEED = 0.62         # playback speed
W_OUT = 480

def caption_png(path, step, text):
    im = Image.new("RGBA", (1080, BAND), (15, 23, 19, 255))
    d = ImageDraw.Draw(im)
    f_big = ImageFont.truetype(FONT, 58, index=3)
    f_tag = ImageFont.truetype(FONT, 36, index=3)
    tag = f"第 {step} 步"
    tw = d.textlength(tag, font=f_tag)
    d.rounded_rectangle([40, 30, 40 + tw + 40, 86], radius=28, fill=(74, 222, 128, 255))
    d.text((60, 31), tag, font=f_tag, fill=(5, 46, 22, 255))
    d.text((40, 104), text, font=f_big, fill=(255, 255, 255, 255))
    im.save(path)

def ring_png(path):
    s = 170; im = Image.new("RGBA", (s, s), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.ellipse([8, 8, s - 8, s - 8], fill=(74, 222, 128, 70), outline=(74, 222, 128, 255), width=12)
    im.save(path)

ring = TMP / "ring.png"; ring_png(ring)

CLIPS = {
    # step 1: notification access list
    "guide_step1": dict(step=1, parts=[(0.5, 1.0, 5.2)],
        rings=[(955, 2270, 2.2, 2.85), (765, 2235, 3.3, 3.85)],
        blur=[(150, 1800, 0, 99, None), (1800, 2215, 0, 2.95, None), (1800, 2215, 3.9, 99, None),
              (2215, 2340, 0, 1.6, None)],
        caps=[(0, 2.95, "往下找到 Sidepeek，打開開關"), (2.95, 3.9, "按「允許」"), (3.9, 99, "開好了，回到 Sidepeek")]),
    # step 4: try the accessibility service -> denied
    "guide_step4": dict(step=4, parts=[(10, 1.25, 5.4)],
        rings=[(420, 1375, 2.45, 3.05), (300, 1160, 3.45, 4.05), (540, 2203, 4.45, 4.95)],
        blur=[(280, 1100, 3.1, 99, None), (1210, 1450, 3.1, 99, None)],
        caps=[(0, 3.15, "點「已安裝的應用程式」"), (3.15, 4.15, "點「Sidepeek 側邊欄」"), (4.15, 99, "被拒絕是正常的，按「關閉」")]),
    # step 5: allow restricted settings (black secure screen in the middle is cut out)
    "guide_step5": dict(step=5, parts=[(19, 2.75, 5.7), (19, 7.05, 7.45)],
        rings=[(1035, 200, 3.9, 4.6), (800, 195, 4.95, 5.6)],
        blur=[],
        caps=[(0, 4.75, "點右上角的「︙」"), (4.75, 6.0, "點「允許受限設定」"), (6.0, 99, "驗證後看到這行提示＝完成")]),
    # step 6: reopen the accessibility service -> allow
    "guide_step6": dict(step=6, parts=[(29, 1.2, 6.2)],
        rings=[(420, 1375, 1.3, 1.85), (300, 1160, 2.45, 3.05), (960, 347, 4.3, 4.9), (855, 2219, 5.05, 5.55)],
        blur=[(280, 1100, 1.9, 3.4, None), (1210, 1450, 1.9, 3.4, None)],
        caps=[(0, 1.95, "點「已安裝的應用程式」"), (1.95, 3.15, "點「Sidepeek 側邊欄」"), (3.15, 4.98, "打開開關"),
              (4.98, 5.6, "按「允許」"), (5.6, 99, "開好了，回到 Sidepeek")]),
}

def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode: print(r.stderr[-3000:]); raise SystemExit(1)

for name, c in CLIPS.items():
    caps = []
    for i, (a, b, text) in enumerate(c["caps"]):
        p = TMP / f"{name}_cap{i}.png"; caption_png(p, c["step"], text); caps.append((a, b, p))
    part_files = []
    for k, (ss, a, b) in enumerate(c["parts"]):
        inputs = ["-ss", str(ss), "-t", str(b + 0.2), "-i", SRC, "-i", str(ring)]
        for _, _, p in caps: inputs += ["-i", str(p)]
        fc = [f"[0:v]trim=start={a}:end={b},setpts=PTS-STARTPTS[v0]"]
        cur = "v0"; off = a      # filters below run on the trimmed timeline: t + a == segment time
        for j, (y0, y1, t0, t1, _) in enumerate(c["blur"]):
            fc.append(f"[{cur}]split[bA{j}][bB{j}];[bB{j}]crop=1080:{y1-y0}:0:{y0},boxblur=28:3[bC{j}];"
                      f"[bA{j}][bC{j}]overlay=0:{y0}:enable='between(t+{off},{t0},{t1})'[b{j}]")
            cur = f"b{j}"
        for j, (x, y, t0, t1) in enumerate(c["rings"]):
            fc.append(f"[{cur}][1:v]overlay={x-85}:{y-85}:enable='between(t+{off},{t0},{t1})'[r{j}]")
            cur = f"r{j}"
        fc.append(f"[{cur}]crop=1080:{2340-TOP}:0:{TOP},pad=1080:{2340-TOP+BAND}:0:{BAND}:color=0x0F1713[p]")
        cur = "p"
        for j, (t0, t1, _) in enumerate(caps):
            fc.append(f"[{cur}][{2+j}:v]overlay=0:0:enable='between(t+{off},{t0},{t1 - 0.001})'[c{j}]")
            cur = f"c{j}"
        fc.append(f"[{cur}]setpts=PTS/{SPEED},fps=30,scale={W_OUT}:-2:flags=lanczos,format=yuv420p[out]")
        pf = TMP / f"{name}_{k}.mp4"
        run(["ffmpeg", "-y", "-v", "error", *inputs, "-filter_complex", ";".join(fc), "-map", "[out]",
             "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", str(pf)])
        part_files.append(pf)
    lst = TMP / f"{name}.txt"; lst.write_text("".join(f"file '{p.resolve()}'\n" for p in part_files))
    out = OUT / f"{name}.mp4"
    run(["ffmpeg", "-y", "-v", "error", "-f", "concat", "-safe", "0", "-i", str(lst),
         "-vf", "tpad=start_mode=clone:start_duration=0.4:stop_mode=clone:stop_duration=1.4",
         "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "27", "-profile:v", "main",
         "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(out)])
    print(name, out.stat().st_size // 1024, "KB")
