"""Build the GitHub Pages site into _site/.

- index.html and og.png (the link-preview image) are copied as is.
- demos/feature-3.html and demos/feature-4.html are tools/feature-video/feature3.html and feature4.html
  (the pages the feature videos are recorded from), so the site plays the very same animations.
- privacy.html is generated from docs/privacy.md, so the policy has one source of truth
  (the same text the app shows in PrivacyActivity).

Usage: python3 site/build.py   (needs `pip install markdown`)
"""
import html
import pathlib
import re
import shutil

import markdown

ROOT = pathlib.Path(__file__).resolve().parent.parent
SITE = ROOT / "site"
OUT = ROOT / "_site"

PAGE = """<!doctype html>
<html lang="zh-Hant-TW">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{title} — Sidepeek</title>
<meta name="description" content="Sidepeek 隱私權政策：讀取哪些資料、怎麼使用、存在哪裡。">
<meta name="theme-color" content="#16a34a">
<link rel="icon" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Crect width='64' height='64' rx='16' fill='%2316a34a'/%3E%3Cpath d='M16 20h32a6 6 0 0 1 6 6v14a6 6 0 0 1-6 6H30l-10 8v-8h-4a6 6 0 0 1-6-6V26a6 6 0 0 1 6-6z' fill='%23fff'/%3E%3C/svg%3E">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Huninn&family=Noto+Sans+TC:wght@400;500;700&display=swap">
<style>
:root {{ --bg:#f6f7f4; --fg:#16181d; --fg-2:#4a4f5a; --fg-3:#7b8190; --line:#dde1d8; --brand:#16a34a; --brand-ink:#0f7a37;
  --round:"Huninn","Noto Sans TC","Noto Sans CJK TC","PingFang TC","Microsoft JhengHei",sans-serif;
  --sans:"Noto Sans TC","Noto Sans CJK TC","PingFang TC","Microsoft JhengHei",system-ui,sans-serif; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg:#0d0f12; --fg:#eef0f3; --fg-2:#b4b9c4; --fg-3:#858b98; --line:#262b33; --brand-ink:#4ade80; color-scheme:dark; }} }}
* {{ box-sizing:border-box; }}
body {{ margin:0; background:var(--bg); color:var(--fg); font:16px/1.8 var(--sans); }}
.wrap {{ max-width:760px; margin:0 auto; padding:24px max(20px, env(safe-area-inset-right, 0px)) 72px max(20px, env(safe-area-inset-left, 0px)); }}
a {{ color:var(--brand-ink); text-underline-offset:3px; }}
.back {{ display:inline-flex; gap:8px; align-items:center; text-decoration:none; font-family:var(--round); font-size:20px; color:var(--fg); }}
.back b {{ font-weight:400; color:var(--brand-ink); }}
h1 {{ font-family:var(--round); font-weight:400; font-size:clamp(30px,5vw,40px); line-height:1.25; margin:36px 0 8px; }}
h2 {{ font-family:var(--round); font-weight:400; font-size:23px; margin:40px 0 10px; padding-top:20px; border-top:1px solid var(--line); }}
p, li {{ color:var(--fg-2); }}
strong {{ color:var(--fg); }}
ul {{ padding-left:1.3em; }}
li + li {{ margin-top:6px; }}
footer {{ margin-top:48px; padding-top:20px; border-top:1px solid var(--line); font-size:14px; color:var(--fg-3); }}
</style>
</head>
<body>
<div class="wrap">
<a class="back" href="./">Side<b>peek</b></a>
{body}
<footer>Sidepeek 是非官方 App，與 LINE、LY Corporation、Samsung 無關。<a href="./">回首頁</a></footer>
</div>
</body>
</html>
"""


def main() -> None:
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir()
    shutil.copy(SITE / "index.html", OUT / "index.html")
    shutil.copy(SITE / "og.png", OUT / "og.png")  # link-preview image (og:image)
    # The Peek bar and ringtone demos are the feature-video pages themselves, played live.
    (OUT / "demos").mkdir()
    for name in ("feature-3.html", "feature-4.html"):
        shutil.copy(ROOT / "tools" / "feature-video" / name.replace("-", ""), OUT / "demos" / name)

    md = (ROOT / "docs" / "privacy.md").read_text(encoding="utf-8")
    title = re.search(r"^#\s+(.+)$", md, re.M).group(1).replace("Sidepeek ", "").strip()
    body = markdown.markdown(md, extensions=["extra", "sane_lists"])
    # mailto links are fine on a real website; keep them.
    (OUT / "privacy.html").write_text(PAGE.format(title=html.escape(title), body=body), encoding="utf-8")
    (OUT / ".nojekyll").write_text("", encoding="utf-8")
    print("built:", sorted(p.name for p in OUT.iterdir()))


if __name__ == "__main__":
    main()
