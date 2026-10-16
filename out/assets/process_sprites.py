#!/usr/bin/env python3
"""Post-process generated sprites: magenta chroma-key -> tight crop -> resize -> assets."""
from PIL import Image
import os

RAW_DIR = "out/sprites_raw"
OUT_DIR = "app/src/main/assets/sprites"
SPRITE_MAX = 256
BG_WIDTH = 1080
BG_HEIGHT = 1920

SPRITES = [
    "player", "bean", "meat", "pie", "rock", "bomb", "knife",
    "car", "tank", "tiger", "lion", "wolf",
]


def is_magenta(r: int, g: int, b: int) -> bool:
    return r > 140 and b > 140 and g < min(r, b) * 0.62


def key_out_magenta(img: Image.Image) -> Image.Image:
    img = img.convert("RGBA")
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a > 0 and is_magenta(r, g, b):
                px[x, y] = (r, g, b, 0)
    return img


def crop_to_content(img: Image.Image, pad_ratio: float = 0.03) -> Image.Image:
    bbox = img.getbbox()
    if bbox is None:
        return img
    left, top, right, bottom = bbox
    pad = int(max(right - left, bottom - top) * pad_ratio)
    left = max(0, left - pad)
    top = max(0, top - pad)
    right = min(img.width, right + pad)
    bottom = min(img.height, bottom + pad)
    return img.crop((left, top, right, bottom))


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    report = []
    for name in SPRITES:
        src = os.path.join(RAW_DIR, f"{name}.png")
        if not os.path.exists(src):
            report.append(f"{name}: MISSING")
            continue
        img = Image.open(src)
        img = key_out_magenta(img)
        img = crop_to_content(img)
        img.thumbnail((SPRITE_MAX, SPRITE_MAX), Image.LANCZOS)
        dst = os.path.join(OUT_DIR, f"{name}.png")
        img.save(dst)
        report.append(f"{name}: {img.width}x{img.height}")

    bg_src = os.path.join(RAW_DIR, "background.png")
    if os.path.exists(bg_src):
        bg = Image.open(bg_src).convert("RGB")
        bg = bg.resize((BG_WIDTH, BG_HEIGHT), Image.LANCZOS)
        bg.save(os.path.join(OUT_DIR, "background.png"), optimize=True)
        report.append(f"background: {BG_WIDTH}x{BG_HEIGHT}")
    else:
        report.append("background: MISSING")

    # contact sheet on a green field to inspect transparency and style
    cell = 200
    cols = 4
    rows = (len(SPRITES) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * cell, rows * cell), (96, 158, 72))
    for i, name in enumerate(SPRITES):
        p = os.path.join(OUT_DIR, f"{name}.png")
        if not os.path.exists(p):
            continue
        s = Image.open(p)
        s.thumbnail((cell - 16, cell - 16), Image.LANCZOS)
        x = (i % cols) * cell + (cell - s.width) // 2
        y = (i // cols) * cell + (cell - s.height) // 2
        sheet.paste(s, (x, y), s)
    sheet.save("out/assets/sprites_sheet.png")
    bg_path = os.path.join(OUT_DIR, "background.png")
    if os.path.exists(bg_path):
        thumb = Image.open(bg_path)
        thumb.thumbnail((360, 640), Image.LANCZOS)
        thumb.save("out/assets/background_thumb.png")

    print("\n".join(report))


if __name__ == "__main__":
    main()
