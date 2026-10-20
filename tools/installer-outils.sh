#!/usr/bin/env bash
# Télécharge les outils de compilation de Flamme Snap (mêmes versions que pour les APK publiés)
# dans $TOOLS (~/tools par défaut). Chaque fichier est vérifié par son empreinte SHA-256.
set -euo pipefail
T="${TOOLS:-$HOME/tools}"
mkdir -p "$T/bin" "$T/aosp/android-34"

telecharger() { # adresse fichier sha256
  if [ -f "$2" ] && echo "$3  $2" | sha256sum -c --status; then return; fi
  echo "Téléchargement de $(basename "$2")…"
  curl -fsSL --retry 3 -o "$2" "$1"
  echo "$3  $2" | sha256sum -c --quiet
}

# Compilateur Kotlin 1.9.24
telecharger https://github.com/JetBrains/kotlin/releases/download/v1.9.24/kotlin-compiler-1.9.24.zip \
  "$T/kotlin19.zip" eb7b68e01029fa67bc8d060ee54c12018f2c60ddc438cf21db14517229aa693b
[ -x "$T/k19/kotlinc/bin/kotlinc" ] || { mkdir -p "$T/k19"; unzip -q -o "$T/kotlin19.zip" -d "$T/k19"; }

# aapt2 (fourni dans Apktool 2.10.0)
telecharger https://github.com/iBotPeaches/Apktool/releases/download/v2.10.0/apktool_2.10.0.jar \
  "$T/apktool.jar" c0350abbab5314248dfe2ee0c907def4edd14f6faef1f5d372d3d4abd28f0431
unzip -q -o -j "$T/apktool.jar" prebuilt/linux/aapt2_64 -d "$T/bin" && chmod +x "$T/bin/aapt2_64"

# dx 30.0.2 (fourni dans dex-tools 2.4)
telecharger https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip \
  "$T/dex2jar24.zip" ee7c45eb3c1d2474a6145d8d447e651a736a22d9664b6d3d3be5a5a817dda23a
[ -f "$T/dex-tools-v2.4/lib/dx-30.0.2.jar" ] || unzip -q -o "$T/dex2jar24.zip" -d "$T"

# Signature et alignement
telecharger https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar \
  "$T/uber-apk-signer.jar" e1299fd6fcf4da527dd53735b56127e8ea922a321128123b9c32d619bba1d835

# android.jar (API 34)
telecharger https://github.com/Reginer/aosp-android-jar/raw/262f6ae931160011572a5adfe4ec6302585e8f60/android-34/android.jar \
  "$T/aosp/android-34/android.jar" d6b3248f33ca93606a4581fa7c678231dd6e3e69f3b7062c8da5ed2c49bdf396

echo "Outils prêts dans $T"
