# Validation — 2026-10-16

Validated on the isolated `Knotes_Tablet` Android API 36 emulator, configured at 1200 × 1920. The XPPen tablet was disconnected during this work.

## Build and instrumentation

`ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest`

Result: **19 tests passed, no failures**. The tests cover legacy storage migration and interrupted-write recovery, SQLite round trips and changes, corrupt-source protection, backup assets and imported identities, invalid archive cleanup, image/PDF/PNG handling, pressure cutoff and historical samples, finger/layer rejection, saving before pen-up, object movement, shapes, erasing, and fill.

The HTML report is generated at `androidApp/build/reports/androidTests/connected/debug/index.html`.

## Navigation and visual review

Recorded and replayed `.argent/flows/boox-editor-navigation.yaml` end to end. Result: **PASS**, 33 executable steps passed; four additional steps are descriptive echoes. The path creates a notebook, adds a layer, adds a page, opens/closes page navigation, enters/exits focus mode, and returns to the library. Each replay creates one notebook.

Run again with `argent flow run boox-editor-navigation --platform android --device emulator-5554` when the emulator is running and the APK is installed.

The recorder initially selected duplicated child labels in two tool-rail buttons. Their stable resource IDs were verified with `editor-selector-check.yaml`, then used in the final flow. No coordinate taps remain in the saved navigation flow. A stale Android devtools snapshot during live authoring was recovered by restarting only the emulator's Argent services; the final replay completed without manual intervention or warnings.

The app was reopened after the replay and retained the created notebooks/pages in SQLite. Final screenshots: [library](screenshots/library.png) and [editor](screenshots/editor.png). System bars are hidden; the writing page remains blank white by default.

## Remaining validation

These checks do not establish physical XPPen pressure behavior, pen latency, microphone quality, or complete BOOX feature parity. The actual tablet needs a later handwriting check. See [feature coverage](boox-feature-coverage.md) for implemented features and remaining gaps.

## Canvas toolbox update

The subsequent canvas-tools update passed **38 instrumented tests** on the same isolated emulator and a full recorded toolbox navigation replay. It adds all three eraser modes, vector holes in filled shapes, ten shapes, selection transforms and brush controls. See [canvas tools review](canvas-tools-review.md) for exact coverage, the one non-fatal idle warning, test commands and hardware limitations. The final APK was updated in place on the connected XPPen tablet; device data was not reset.
