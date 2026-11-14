#!/usr/bin/env python3
"""Normalize bundled raster PNG logos for clean circular shortcut icons."""
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

OUT = Path("app/src/main/res/drawable-nodpi")
SIZE = 256
BACKGROUND = (23, 27, 40, 255)

def matte_color(im):
    w,h=im.size
    points=[im.getpixel((x,y))[:3] for x,y in
            [(1,1),(w-2,1),(1,h-2),(w-2,h-2)]]
    mean=tuple(sum(p[k] for p in points)//4 for k in range(3))
    delta=max(abs(p[k]-mean[k]) for p in points for k in range(3))
    return mean,delta

def strip_plain_matte(im):
    bg,delta=matte_color(im)
    light=min(bg)>=219
    dark=max(bg)<=42
    if delta>18 or not (light or dark):
        return im
    pixels=im.load()
    for y in range(im.height):
        for x in range(im.width):
            r,g,b,a=pixels[x,y]
            if not a:
                continue
            distance=max(abs(r-bg[0]),abs(g-bg[1]),abs(b-bg[2]))
            if distance<=18:
                pixels[x,y]=(r,g,b,0)
            elif distance<46:
                pixels[x,y]=(r,g,b,int(a*(distance-18)/28))
            elif light and max(r,g,b)<92:
                pixels[x,y]=(245,246,250,a)
    return im

def normalize(path):
    with Image.open(path) as src:
        im=src.convert("RGBA")
    im.thumbnail((800,800),Image.Resampling.LANCZOS)
    w,h=im.size
    corners=[im.getpixel((x,y))[3] for x,y in
             [(0,0),(w-1,0),(0,h-1),(w-1,h-1)]]
    square_tile=(.84<=w/h<=1.2 and min(corners)>248)
    if square_tile:
        # Full-bleed square artwork: the circular mask removes every square corner.
        scale=max(SIZE/w,SIZE/h)
        im=im.resize((round(w*scale),round(h*scale)),Image.Resampling.LANCZOS)
        x=(im.width-SIZE)//2
        y=(im.height-SIZE)//2
        artwork=im.crop((x,y,x+SIZE,y+SIZE))
    else:
        if min(corners)>248:
            im=strip_plain_matte(im)
        bounds=im.getchannel("A").getbbox()
        if bounds:
            im=im.crop(bounds)
        w,h=im.size
        im.thumbnail((210,128) if w/h>2.2 else (204,204),Image.Resampling.LANCZOS)
        artwork=Image.new("RGBA",(SIZE,SIZE),BACKGROUND)
        artwork.alpha_composite(im,((SIZE-im.width)//2,(SIZE-im.height)//2))
    mask=Image.new("L",(SIZE,SIZE),0)
    ImageDraw.Draw(mask).ellipse((1,1,SIZE-2,SIZE-2),fill=255)
    artwork.putalpha(ImageChops.multiply(artwork.getchannel("A"),mask))
    artwork.save(path,optimize=True)

for asset in sorted(OUT.glob("brand_png_*.png")):
    normalize(asset)
    print("Normalized round PNG:",asset.name)
