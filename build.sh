#!/usr/bin/env bash
# Compile Flamme Snap sans Gradle : aapt2 -> javac (R) -> kotlinc -> dx -> zip -> signature.
set -euo pipefail
cd "$(dirname "$0")"

T="${TOOLS:-$HOME/tools}"
ANDROID_JAR="$T/aosp/android-34/android.jar"
AAPT2="$T/bin/aapt2_64"
KOTLINC="$T/k19/kotlinc/bin/kotlinc"
STDLIB="$T/k19/kotlinc/lib/kotlin-stdlib.jar"
DX="$T/dex-tools-v2.4/lib/dx-30.0.2.jar"
SIGNER="$T/uber-apk-signer.jar"
KS="${KEYSTORE:-$HOME/flammesnap-release.jks}"
# Clé de signature : jamais publiée. Mot de passe dans la variable KS_PASS.
: "${KS_PASS:?Définis KS_PASS (mot de passe de la clé de signature)}"
VERSION_CODE=4
VERSION_NAME="1.3"

B=build
rm -rf "$B"
mkdir -p "$B/gen" "$B/classes" "$B/dex" "$B/out"

echo "1/6 Icônes"
python3 tools/make_icons.py app/res

echo "2/6 Ressources (aapt2)"
"$AAPT2" compile --dir app/res -o "$B/res.zip"
"$AAPT2" link -o "$B/base.apk" -I "$ANDROID_JAR" \
  --manifest app/AndroidManifest.xml -A app/assets \
  --java "$B/gen" --auto-add-overlay \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  "$B/res.zip"

echo "3/6 Classe R (javac)"
javac -nowarn --release 8 -d "$B/classes" -classpath "$ANDROID_JAR" $(find "$B/gen" -name '*.java') 2>&1 | grep -v "warning: \[options\]" || true

echo "4/6 Kotlin (kotlinc)"
"$KOTLINC" app/src -no-jdk -no-reflect \
  -classpath "$ANDROID_JAR:$B/classes" \
  -jvm-target 1.8 -Xlambdas=class -Xsam-conversions=class -Xstring-concat=inline \
  -nowarn -d "$B/classes"

echo "5/6 Dex (dx)"
cp "$STDLIB" "$B/stdlib.jar"
zip -q -d "$B/stdlib.jar" 'META-INF/versions/*' 'kotlin/comparisons/ComparisonsKt__ComparisonsKt.class' 'kotlin/streams/jdk8/*' >/dev/null 2>&1 || true
java -Xmx2g -cp "$DX" com.android.dx.command.Main --dex --min-sdk-version=24 \
  --output="$B/dex/classes.dex" "$B/classes" "$B/stdlib.jar"

echo "6/6 Assemblage + signature"
python3 tools/assemble.py "$B/base.apk" "$B/dex/classes.dex" "$B/unsigned.apk"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias flammesnap -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -dname "CN=Flamme Snap, O=Personnel, C=SN" >/dev/null 2>&1
fi
java -jar "$SIGNER" -a "$B/unsigned.apk" -o "$B/out" \
  --ks "$KS" --ksAlias flammesnap --ksPass "$KS_PASS" --ksKeyPass "$KS_PASS" \
  --allowResign >/dev/null
cp "$B"/out/*.apk "$B/FlammeSnap.apk"
echo "APK : $B/FlammeSnap.apk ($(du -h "$B/FlammeSnap.apk" | cut -f1))"
