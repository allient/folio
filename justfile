SESSION_NAME := `basename "$PWD"`
MOBILE := "apps/mobile"
APK_DEBUG := MOBILE + "/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
APK_UNSIGNED := MOBILE + "/androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk"
RELEASE_DIR := MOBILE + "/artifacts/release"
# Physical tablet serial, e.g. in your shell profile: export FOLIO_TABLET=<adb serial>
TABLET := env("FOLIO_TABLET", "emulator-5554")

# List recipes
default:
    @just --list

# One shot: attach if session exists, else create it with ~/.config/zellij/layouts/vibe.kdl
vibe: vibe-clean
    zellij --layout vibe attach {{ SESSION_NAME }} --create

vibe-clean:
    zellij delete-session {{ SESSION_NAME }} 2>/dev/null || true

# Install workspace dependencies
install:
    bun install

# Docs site dev server (http://localhost:4321)
docs:
    bun run dev:docs

# Build everything (docs site and debug APK)
build:
    bun run build

# Build the docs site into apps/docs/dist
build-docs:
    bun run build:docs

# Build the Android debug APK
build-android:
    bun run build:android

# Shared module host tests
test:
    bun run test:android

# Instrumented tests on a device or emulator (serial optional)
test-device serial="":
    cd {{ MOBILE }} && {{ if serial != "" { "ANDROID_SERIAL=" + serial + " " } else { "" } }}./gradlew :androidApp:connectedDebugAndroidTest

# Connected devices
devices:
    adb devices -l

# Build and install the debug APK on one device (default: emulator)
install-debug serial="emulator-5554": build-android
    adb -s {{ serial }} install -r {{ APK_DEBUG }}

# Build a release APK: signed with the upload key when apps/mobile/keystore.properties exists,
# otherwise signed here with the local debug key
release:
    #!/usr/bin/env bash
    set -euo pipefail
    cd {{ MOBILE }} && ./gradlew :androidApp:assembleRelease -q && cd - >/dev/null
    mkdir -p {{ RELEASE_DIR }}
    OUT={{ MOBILE }}/androidApp/build/outputs/apk/release
    if [ -f "$OUT/androidApp-release.apk" ]; then
        cp "$OUT/androidApp-release.apk" {{ RELEASE_DIR }}/Folio-release.apk
    else
        BT=$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* | sort -V | tail -1)
        "$BT/zipalign" -f -p 4 {{ APK_UNSIGNED }} {{ RELEASE_DIR }}/aligned.apk
        "$BT/apksigner" sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android \
            --ks-key-alias androiddebugkey --out {{ RELEASE_DIR }}/Folio-release.apk {{ RELEASE_DIR }}/aligned.apk
    fi
    echo "{{ RELEASE_DIR }}/Folio-release.apk"

# Build and install the signed release APK on one device (default: $FOLIO_TABLET)
install-release serial=TABLET: release
    adb -s {{ serial }} install --no-incremental -r {{ RELEASE_DIR }}/Folio-release.apk

# Mirror and control the connected physical tablet (needs scrcpy)
mirror:
    scrcpy -d

# Launch Folio on one device
launch serial="emulator-5554":
    adb -s {{ serial }} shell monkey -p com.allient.folio -c android.intent.category.LAUNCHER 1

# Crash and error log from one device
crashlog serial=TABLET:
    adb -s {{ serial }} logcat -d -b crash,main -v time '*:E' | tail -200

# Deploy the docs site to Cloudflare from this machine (needs `bunx wrangler login` once)
deploy-docs: build-docs
    cd apps/docs && bunx wrangler deploy

# Signed release bundle (.aab) for Google Play; needs apps/mobile/keystore.properties
aab:
    cd {{ MOBILE }} && fastlane aab

# Google Play uploads with fastlane (see docs: Publishing › Publish on Google Play)
play-internal:
    cd {{ MOBILE }} && fastlane internal

play-beta:
    cd {{ MOBILE }} && fastlane beta

play-production:
    cd {{ MOBILE }} && fastlane production

play-promote:
    cd {{ MOBILE }} && fastlane promote

play-metadata:
    cd {{ MOBILE }} && fastlane metadata
