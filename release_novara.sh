#!/data/data/com.termux/files/usr/bin/bash
set -e

ROOT="$HOME/Novara"
ANDROID="$ROOT/android"

cd "$ANDROID"

echo "======================================"
echo "        NOVARA RELEASE BUILDER"
echo "======================================"

if ! command -v gh >/dev/null 2>&1; then
    echo
    echo "GitHub CLI (gh) nahi mila."
    echo "Install karo:"
    echo "pkg install gh"
    echo "phir:"
    echo "gh auth login"
    exit 1
fi

echo
echo "===== CURRENT VERSION ====="
grep -n -E 'versionCode|versionName' app/build.gradle

CURRENT_CODE=$(sed -n 's/.*versionCode[[:space:]]*\([0-9][0-9]*\).*/\1/p' app/build.gradle | head -1)

if [ -z "$CURRENT_CODE" ]; then
    echo "ERROR: versionCode nahi mila."
    exit 1
fi

NEW_CODE=$((CURRENT_CODE + 1))
NEW_NAME="1.${NEW_CODE}"

echo
echo "Old versionCode : $CURRENT_CODE"
echo "New versionCode : $NEW_CODE"
echo "New versionName : $NEW_NAME"

cp app/build.gradle \
   "$ROOT/pre_auto_update_backup/app-build.gradle.$CURRENT_CODE"

sed -i "s/versionCode[[:space:]]*[0-9][0-9]*/versionCode $NEW_CODE/" app/build.gradle
sed -i "s/versionName[[:space:]]*\"[^\"]*\"/versionName \"$NEW_NAME\"/" app/build.gradle

echo
echo "===== BUILDING SIGNED APK ====="

if [ -z "${NOVARA_KEYSTORE_PASSWORD:-}" ] || [ -z "${NOVARA_KEY_PASSWORD:-}" ]; then
    echo
    echo "ERROR: Release keystore passwords set nahi hain."
    echo "Pehle current shell mein:"
    echo 'export NOVARA_KEYSTORE_PASSWORD="YOUR_KEYSTORE_PASSWORD"'
    echo 'export NOVARA_KEY_PASSWORD="YOUR_KEY_PASSWORD"'
    exit 1
fi

./gradlew clean assembleRelease

APK="$ANDROID/app/build/outputs/apk/release/app-release.apk"

if [ ! -f "$APK" ]; then
    echo "ERROR: APK create nahi hua."
    exit 1
fi

mkdir -p "$HOME/Novara/releases"

OUT="$HOME/Novara/releases/Novara-v${NEW_NAME}-code${NEW_CODE}.apk"
cp "$APK" "$OUT"

echo
echo "===== APK READY ====="
echo "$OUT"

echo
echo "===== GITHUB REPOSITORY ====="
gh repo view --json nameWithOwner -q .nameWithOwner

REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)

if [ -z "$REPO" ]; then
    echo "ERROR: Current folder GitHub repository se connected nahi hai."
    echo "Repo connect karke script dobara run karo."
    exit 1
fi

TAG="v${NEW_NAME}"

echo
echo "===== CREATING GITHUB RELEASE ====="
echo "Repository : $REPO"
echo "Tag        : $TAG"

gh release create "$TAG" "$OUT" \
    --repo "$REPO" \
    --title "Novara $NEW_NAME" \
    --notes "Novara Android release $NEW_NAME (versionCode $NEW_CODE)"

echo
echo "======================================"
echo "       NOVARA RELEASE SUCCESS"
echo "======================================"
echo "Version : $NEW_NAME"
echo "Code    : $NEW_CODE"
echo "APK     : $OUT"
echo
echo "GitHub Release successfully published."
echo "======================================"
