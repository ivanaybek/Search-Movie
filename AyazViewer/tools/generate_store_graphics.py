#!/usr/bin/env python3
"""Google Play mağaza görsellerini üretir (uygulama simgesiyle aynı çizim).

Çıktılar (AyazViewer/magaza/):
  simge-512.png               512x512 uygulama simgesi (Play Console > Mağaza girişi)
  tanitim-gorseli-1024x500.png 1024x500 öne çıkan görsel

Kullanım:  python3 tools/generate_store_graphics.py
Gereken:   Pillow, Inter yazı tipi (yoksa varsayılan yazı tipi kullanılır)
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "magaza")

# res/drawable/ic_launcher_background.xml ile aynı renkler
STOPS = [(0.0, (0x2F, 0x5B, 0xFF)), (0.55, (0x5B, 0x4D, 0xF2)), (1.0, (0x9B, 0x5C, 0xF6))]


def lerp_color(t):
    t = max(0.0, min(1.0, t))
    for (a, ca), (b, cb) in zip(STOPS, STOPS[1:]):
        if t <= b:
            k = (t - a) / (b - a)
            return tuple(round(ca[i] + (cb[i] - ca[i]) * k) for i in range(3))
    return STOPS[-1][1]


def gradient(w, h):
    """Sol üstten sağ alta doğrusal geçiş."""
    img = Image.new("RGB", (w, h))
    px = img.load()
    span = w + h
    for y in range(h):
        for x in range(w):
            px[x, y] = lerp_color((x + y) / span)
    return img


def arc(cx, cy, r, a0, a1, n=10):
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
             cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]


def icon_shapes():
    """ic_launcher_foreground.xml yolları (108 birimlik görüntü alanı)."""
    body = [(40, 30), (60, 30), (72, 42), (72, 74)] + arc(68, 74, 4, 0, 90) + [(40, 78)] + \
        arc(40, 74, 4, 90, 180) + [(36, 34)] + arc(40, 34, 4, 180, 270)
    fold = [(60, 30), (60, 38)] + arc(64, 38, 4, 180, 90) + [(72, 42)]
    letter = [(54, 44), (63, 68), (57.8, 68), (55.9, 62.6), (47.9, 62.6), (46, 68), (40.8, 68), (49.8, 44)]
    hole = [(51.4, 58.4), (56.6, 58.4), (54, 50.8)]
    line = [(42, 71), (66, 71), (66, 73.4), (42, 73.4)]
    return body, fold, letter, hole, line


def draw_icon(draw, ox, oy, scale):
    """Simgenin ön planını (ox, oy) kaydırması ve [scale] ölçeğiyle çizer."""
    def tr(pts):
        return [(ox + x * scale, oy + y * scale) for x, y in pts]
    body, fold, letter, hole, line = icon_shapes()
    draw.polygon(tr(body), fill=(255, 255, 255))
    draw.polygon(tr(fold), fill=(0xC9, 0xD3, 0xFF))
    draw.polygon(tr(letter), fill=(0x4F, 0x5B, 0xF6))
    draw.polygon(tr(hole), fill=(255, 255, 255))
    draw.polygon(tr(line), fill=(0x8B, 0x5C, 0xF6))


def app_icon(size=512, ss=4):
    """Uyarlanabilir simgenin görünen merkezi (108 alanın ortadaki 72 birimi)."""
    big = size * ss
    img = gradient(big // 8, big // 8).resize((big, big), Image.BICUBIC)
    d = ImageDraw.Draw(img)
    scale = big / 72.0
    draw_icon(d, -18 * scale, -18 * scale, scale)
    return img.resize((size, size), Image.LANCZOS)


def font(name, size):
    for path in (f"/usr/share/fonts/opentype/inter/{name}.otf", f"/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def feature_graphic(w=1024, h=500, ss=2):
    W, H = w * ss, h * ss
    img = gradient(W // 8, H // 8).resize((W, H), Image.BICUBIC)
    d = ImageDraw.Draw(img)
    # yumuşak dekoratif daireler
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.ellipse((W * 0.70, -H * 0.35, W * 1.25, H * 0.75), fill=(255, 255, 255, 22))
    od.ellipse((W * 0.58, H * 0.55, W * 0.95, H * 1.35), fill=(255, 255, 255, 16))
    img = Image.alpha_composite(img.convert("RGBA"), overlay)
    d = ImageDraw.Draw(img)

    # simge: yuvarlatılmış kare içinde
    box = int(H * 0.46)
    bx, by = int(W * 0.075), (H - box) // 2
    tile = app_icon(box, 2).convert("RGBA")
    mask = Image.new("L", (box, box), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, box - 1, box - 1), radius=int(box * 0.24), fill=255)
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((bx + 6 * ss, by + 10 * ss, bx + box + 6 * ss, by + box + 10 * ss),
                                             radius=int(box * 0.24), fill=(20, 10, 60, 70))
    img = Image.alpha_composite(img, shadow)
    img.paste(tile, (bx, by), mask)
    d = ImageDraw.Draw(img)

    tx = bx + box + int(W * 0.05)
    room = W - tx - int(W * 0.05)

    def fitted(name, size, text):
        """Metin sağ kenara taşmayacak şekilde yazı boyutunu küçültür."""
        f = font(name, size * ss)
        while d.textlength(text, font=f) > room and size > 10:
            size -= 1
            f = font(name, size * ss)
        return f

    lines = [
        ("Ayaz Viewer", "InterDisplay-Bold", 88, (255, 255, 255)),
        ("PDF ve Excel: görüntüle, düzenle, oluştur", "Inter-SemiBold", 32, (255, 255, 255)),
        ("Çevrimdışı  •  Reklamsız  •  Hesap gerektirmez", "Inter-Medium", 25, (232, 230, 255)),
    ]
    fonts = [fitted(n, sz, t) for t, n, sz, _ in lines]
    gaps = [18 * ss, 12 * ss]
    heights = [d.textbbox((0, 0), t, font=f)[3] for (t, _, _, _), f in zip(lines, fonts)]
    y = (H - (sum(heights) + sum(gaps))) // 2 - 6 * ss
    for i, ((t, _, _, color), f) in enumerate(zip(lines, fonts)):
        d.text((tx, y), t, font=f, fill=color)
        y += heights[i] + (gaps[i] if i < len(gaps) else 0)
    return img.convert("RGB").resize((w, h), Image.LANCZOS)


def main():
    os.makedirs(OUT, exist_ok=True)
    app_icon().save(os.path.join(OUT, "simge-512.png"), optimize=True)
    feature_graphic().save(os.path.join(OUT, "tanitim-gorseli-1024x500.png"), optimize=True)
    print("Kaydedildi:", OUT)


if __name__ == "__main__":
    sys.exit(main())
