"""Génère les icônes PNG (Android 7) à partir du même dessin que l'icône vectorielle."""
import os
import sys
from PIL import Image, ImageDraw

RES = sys.argv[1]

OUTER = [(54, 22), [((54, 22), (74, 40), (74, 60)), ((74, 73), (65, 84), (54, 84)),
                    ((43, 84), (34, 73), (34, 61)), ((34, 52), (39, 45), (44, 40)),
                    ((44, 47), (47, 52), (51, 53)), ((49, 42), (51, 31), (54, 22))]]
INNER = [(54, 50), [((54, 50), (64, 60), (64, 69)), ((64, 75), (59, 79), (54, 79)),
                    ((49, 79), (44, 75), (44, 69)), ((44, 63), (49, 57), (54, 50))]]


def sample(path, n=40):
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


def icon(size, round_=False):
    ss = 8
    big = size * ss
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if round_:
        d.ellipse([0, 0, big - 1, big - 1], fill="#FFFC00")
    else:
        d.rounded_rectangle([0, 0, big - 1, big - 1], radius=int(big * 0.22), fill="#FFFC00")
    # On recadre la zone utile (18..90 sur 108) pour remplir l'icône.
    scale = big / 72.0
    off = 18

    def tr(pts):
        return [((x - off) * scale, (y - off) * scale) for x, y in pts]

    d.polygon(tr(sample(OUTER)), fill="#FF5A1F")
    d.polygon(tr(sample(INNER)), fill="#FFC93C")
    return img.resize((size, size), Image.LANCZOS)


for folder, size in [("mipmap-mdpi", 48), ("mipmap-hdpi", 72), ("mipmap-xhdpi", 96),
                     ("mipmap-xxhdpi", 144), ("mipmap-xxxhdpi", 192)]:
    out = os.path.join(RES, folder)
    os.makedirs(out, exist_ok=True)
    icon(size).save(os.path.join(out, "ic_launcher.png"))
print("icônes OK")
