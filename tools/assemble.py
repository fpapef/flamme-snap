"""Ajoute classes.dex à l'APK produit par aapt2 (resources.arsc gardé non compressé)."""
import sys
import zipfile

base, dex, out = sys.argv[1:4]

with zipfile.ZipFile(base) as src, zipfile.ZipFile(out, "w") as dst:
    for info in src.infolist():
        data = src.read(info.filename)
        stored = info.filename == "resources.arsc" or info.compress_type == zipfile.ZIP_STORED
        zi = zipfile.ZipInfo(info.filename, date_time=(2026, 10, 20, 12, 0, 0))
        zi.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
        zi.external_attr = 0o644 << 16
        dst.writestr(zi, data)
    zi = zipfile.ZipInfo("classes.dex", date_time=(2026, 10, 20, 12, 0, 0))
    zi.compress_type = zipfile.ZIP_DEFLATED
    zi.external_attr = 0o644 << 16
    with open(dex, "rb") as f:
        dst.writestr(zi, f.read())
print("APK non signé assemblé")
