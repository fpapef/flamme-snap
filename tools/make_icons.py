"""Icônes PNG (Android 7) : carré blanc arrondi, flamme en dégradé (même dessin que le vectoriel)."""
import os
import sys
from PIL import Image, ImageDraw

RES = sys.argv[1]

OUTER = [(54, 25), [((54, 25), (72, 41), (72, 59)), ((72, 71), (64, 81), (54, 81)),
                    ((44, 81), (36, 71), (36, 60)), ((36, 52), (40.5, 46), (45, 41.5)),
                    ((45, 47.5), (47.7, 52), (51.2, 53)), ((49.4, 43), (51.3, 33), (54, 25))]]
INNER = [(54, 50), [((54, 50), (63, 59), (63, 67)), ((63, 72.5), (59, 76.5), (54, 76.5)),
                    ((49, 76.5), (45, 72.5), (45, 67)), ((45, 61.5), (49.5, 56), (54, 50))]]


def sample(path, n=48):
    start, curves = path
    pts, p0 = [], start
    for c1, c2, p3 in curves:
        for i in range(n):
            t = i / n
            mt = 1 - t
            x = mt**3 * p0[0] + 3 * mt**2 * t * c1[0] + 3 * mt * t**2 * c2[0] + t**3 * p3[0]
            y = mt**3 * p0[1] + 3 * mt**2 * t * c1[1] + 3 * mt * t**2 * c2[1] + t**3 * p3[1]
            pts.append((x, y))
        p0 = p3
    return pts


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def gradient(size, top, bottom, y0, y1):
    a, b = hexrgb(top), hexrgb(bottom)
    col = Image.new("RGBA", (1, size))
    px = col.load()
    for y in range(size):
        f = min(1, max(0, (y - y0) / max(1, (y1 - y0))))
        px[0, y] = tuple(int(a[i] + (b[i] - a[i]) * f) for i in range(3)) + (255,)
    return col.resize((size, size), Image.NEAREST)


def icon(size):
    ss = 4
    big = size * ss
    base = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    ImageDraw.Draw(base).rounded_rectangle([0, 0, big - 1, big - 1], radius=int(big * 0.23), fill="#FFFFFF")
    scale = big / 72.0  # zone utile 18..90 du canevas 108
    off = 18

    def tr(pts):
        return [((x - off) * scale, (y - off) * scale) for x, y in pts]

    for path, top, bottom, ys in ((OUTER, "#FF9F0A", "#FF3B30", (25, 81)), (INNER, "#FFE7A3", "#FFC53D", (50, 76.5))):
        mask = Image.new("L", (big, big), 0)
        ImageDraw.Draw(mask).polygon(tr(sample(path)), fill=255)
        g = gradient(big, top, bottom, (ys[0] - off) * scale, (ys[1] - off) * scale)
        base.paste(g, (0, 0), mask)
    return base.resize((size, size), Image.LANCZOS)


for folder, size in [("mipmap-mdpi", 48), ("mipmap-hdpi", 72), ("mipmap-xhdpi", 96),
                     ("mipmap-xxhdpi", 144), ("mipmap-xxxhdpi", 192)]:
    out = os.path.join(RES, folder)
    os.makedirs(out, exist_ok=True)
    icon(size).save(os.path.join(out, "ic_launcher.png"))
print("icônes OK")
