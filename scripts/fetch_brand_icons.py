#!/usr/bin/env python3
"""Refresh offline PNG logos; no SVG/vector conversion."""
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.parse import quote
from io import BytesIO
from PIL import Image

OUT = Path("app/src/main/res/drawable-nodpi")
OUT.mkdir(parents=True, exist_ok=True)
ICONS = [
    ("netflix", "netflix.png", "netflix.com"),
    ("youtube", "youtube.png", "youtube.com"),
    ("crunchyroll", "crunchyroll.png", "crunchyroll.com"),
    ("spotify", "spotify.png", "spotify.com"),
    ("twitch", "twitch.png", "twitch.tv"),
    ("plex", "plex.png", "plex.tv"),
    ("appletv", "apple-tv-plus.png", "tv.apple.com"),
    ("dazn", None, "dazn.com"),
    ("paramountplus", "paramount-plus.png", "paramountplus.com"),
    ("samsung", "samsung-tv-plus.png", "samsung.com"),
    ("zdf", None, "zdf.de"),
    ("disneyplus", "disney-plus.png", "disneyplus.com"),
    ("primevideo", "prime-video.png", "primevideo.com"),
    ("canalplus", None, "canalplus.com"),
    ("joyn", None, "joyn.de"),
    ("waipu", "waipu-tv.png", "waipu.tv"),
    ("ard", None, "ard.de"),
    ("rtlplus", None, "plus.rtl.de"),
    ("magenta", None, "magenta.tv"),
    ("tiktok", "tiktok.png", "tiktok.com"),
    ("arte", None, "arte.tv"),
    ("sky", None, "sky.com"),
    ("wow", None, "wowtv.de"),
    ("hulu", "hulu.png", "hulu.com"),
]
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"

def fetch(url):
    request = Request(url, headers={"User-Agent": "SamsungRemoteAndroid"})
    with urlopen(request, timeout=25) as response:
        contents = response.read(1000000)
    if contents.startswith(PNG_SIGNATURE):
        return contents
    # Accept actual raster favicons (ICO / WebP / JPEG), convert them to PNG.
    with Image.open(BytesIO(contents)) as image:
        image = image.convert("RGBA")
        image.thumbnail((256, 256), Image.Resampling.LANCZOS)
        output = BytesIO()
        image.save(output, format="PNG")
        result = output.getvalue()
    if not result.startswith(PNG_SIGNATURE):
        raise ValueError("Invalid raster image")
    return result

missing = []
for ident, source_name, website in ICONS:
    filename = OUT / f"brand_png_{ident}.png"
    if filename.is_file() and filename.read_bytes().startswith(PNG_SIGNATURE):
        continue
    urls = []
    if source_name:
        urls.append(f"https://raw.githubusercontent.com/homarr-labs/dashboard-icons/main/png/{quote(source_name)}")
    urls.append(f"https://www.google.com/s2/favicons?domain={quote(website)}&sz=256")
    urls.append(f"https://icons.duckduckgo.com/ip3/{quote(website)}.ico")
    if website in {"arte.tv", "canalplus.com", "sky.com"}:
        urls.append(f"https://www.google.com/s2/favicons?domain=www.{quote(website)}&sz=128")
        urls.append(f"https://icons.duckduckgo.com/ip3/www.{quote(website)}.ico")
    for url in urls:
        try:
            png = fetch(url)
            filename.write_bytes(png)
            print(f"{ident}: {len(png)} bytes ({url.split('/')[2]})")
            break
        except Exception as exc:
            print(f"{ident}: source unavailable ({type(exc).__name__})")
    if not filename.is_file():
        missing.append(ident)
if missing:
    raise SystemExit("Missing PNG logos: " + ", ".join(missing))
print(f"Ready: {len(ICONS)} PNG icons")
