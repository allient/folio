---
title: "Canvas tools review — 2026-10-16"
---

Reference: [BOOX Handwritten Notes](https://help.boox.com/hc/en-us/articles/8569373888788-Handwritten-Notes). This is app-local functionality, with no root, driver changes, cloud services, or changes to the existing pressure activation curve.

## Implemented and corrected

| Tool | Result | Evidence in this update |
| --- | --- | --- |
| Pens | Seven labeled brush choices; actual Jetpack Ink preview; width, color, opacity and quick presets persist locally. Changing color preserves chosen opacity. | Every brush rasterizes in instrumentation; Pens and Highlighter preview inspected in the emulator and replayed. |
| Partial eraser | Exact swept segment/capsule cuts between input samples; pressure, timing, tags and style survive fragment creation. Size is consistent in document space when zooming. | Sparse-vector splitting, metadata, zoom-size and threshold interruption tests. |
| Filled-shape erasing | Vector subtraction retains the un-erased solid region, including holes. Geometry stays editable and portable. | Pixel checks before/after backup/import, PNG and PDF export; JSON round trip. |
| Stroke eraser | Removes entire touched ink strokes. Each gesture is one change, including fast motion between samples. | Swept erasing and one-commit tests. |
| Lasso eraser | Draw a freeform enclosure to delete intersecting strokes. | Sparse crossing stroke, outside-stroke retention and one-commit test. |
| Eraser controls | Size, active-layer/all-editable-layer scope, return to previous pen, clear active/all editable ink with confirmation. Text, images and paper are retained. | Scope/locked/hidden-layer tests; all three modes inspected and replayed. Automatic return callback tested at pressure cutoff. |
| Hardware eraser | A reported `TOOL_TYPE_ERASER` overrides the current input tool for that contact, including Shape, Lasso, Objects and Pan. | Synthetic hardware-eraser test in all four modes. This does not establish which features the XPPen pencil physically exposes. |
| Selection | Freeform/rectangle selection, drag, copy, paste, duplicate, cut, recolor, tags, delete, rotate left/right, flip both axes, grow/shrink. | Rectangle selection of a sparse crossing stroke and drag; rotation, scaling, flip, metadata and page-boundary tests; selector UI inspected/replayed. Transforms use 90-degree/10-percent steps. |
| Shapes | Line, arrow, double arrow, rectangle, square, ellipse, circle, triangle, diamond and star. Closed shapes can be filled. Width is independent of sample pressure. | All ten produce finite in-bounds geometry; circle/square pixel proportions tested; Shapes dialog and selection replayed. |
| Fill | Colors a closed vector path on the active editable layer. A gesture commits once; cancellation rolls it back. | Fill, cancellation, one-commit and filled-shape export tests. Not a bitmap flood-fill tool. |
| Text and image arrangement | Continuous text dragging no longer finishes on the first move. Image dragging/resizing and cancel/commit behavior are retained. Selected objects have edit/delete/deselect controls. | Text multi-move and cancel tests; image drag/resize, one-commit and locked-layer tests. |
| Input and pressure | Existing threshold, historical-sample segmentation and lift handling are retained. A hardware mouse can draw with finger drawing off. | Low/historical pressure, separate contacts, flush, finger rejection and mouse tests. Emulator touch gestures still require Write with finger. |
| Undo, redo and save | Erase/fill/drag updates publish once per gesture, so the editor creates one undo snapshot and save request. Cancellation restores the prior content without publishing an edit. | Commit-count and cancellation assertions; existing editor snapshot/save paths reviewed. History remains in memory. |
| Layers, paper, pages | Existing layer lock/visibility/reordering, paper choices and page navigation remain wired to the editor model. New tools honor editable layers. | Layer protection tests; code review of existing menus. This update does not claim new manual coverage for every page/layer management action. |
| Zoom and pan | Existing bounded viewport, two-finger navigation, fit/reset and zoom badge retained. New eraser size and selection coordinates use the current viewport transform. | Zoomed eraser test and source review. Multi-finger feel and physical stylus latency require hardware testing. |
| Insert, attachments, export | Existing image/PDF insertion, attachments, audio and sharing controls remain. Cut vectors render through the same editor/export renderer. | Image/PDF/backup/storage tests and explicit cut-vector PNG/PDF checks. Microphone and external share/file-picker apps were not exercised in this update. |

## Validation

`./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest` succeeded.

On the isolated Android API 36 `Knotes_Tablet` emulator:

```sh
adb -s emulator-5554 shell am instrument -w \
  com.allient.folio.test/androidx.test.runner.AndroidJUnitRunner
```

Result: **38 tests passed**: 25 canvas/input/rendering checks, 7 file/export checks and 6 storage checks. The earlier portable-backup fixture was updated to inspect `manifest.json` and decompress `notebook.json.zst`; its asset, identity and restore assertions were retained.

The recorded `.argent/flows/canvas-toolbox-review.yaml` completed a full 78-step replay on the emulator. One idle check reported a small changing screen region after switching eraser mode; the following stable Close Eraser control and all later navigation checks succeeded. That dialog has no loading/network operation. Raw recorder taps were replaced with their actual stable control IDs after verifying them against the runner in the `canvas-*-selector-check` fragments. No coordinate taps remain in the saved main flow.

```sh
argent flow run canvas-toolbox-review --platform android --device emulator-5554
```

The flow creates a blank notebook each run and assumes the built-in Highlighter opacity (27%). It covers Create, Eraser modes, Shapes, Rectangle selection, focus mode, Pens and return to the library. It does not simulate pen pressure.

The final debug APK was installed in place on the connected XPPen tablet with `adb install -r` successfully. No uninstall or data reset was performed. Instrumentation ran only on the emulator.

## Further BOOX-style tools

Handwriting recognition, hold-to-perfect shapes, automatic lasso and scribble-to-erase remain candidates for separate implementations. No inactive placeholder is presented as a functioning tool. Handwriting recognition needs an offline recognition engine/language and privacy design; automatic recognition gestures need pressure-fault and accidental-deletion validation. Configurable stylus buttons depend on actual button events from the hardware.

The lasso works on handwriting/vector shapes; arrange typed text and images through Text → Arrange text & images. The app still uses a bounded page and does not reproduce BOOX firmware functions, proprietary services or its `.note` format. Physical XPPen pressure response, hover compensation and front-buffer latency still need user handwriting checks.

## Selection drag/resize and autosave controls update

Selected ink and vector shapes can now be dragged with the pen, a mouse, or a finger even when finger drawing is disabled. Direct dragging starts inside the selection. The selection toolbar also has an explicit **Move selection** action: while enabled, dragging anywhere on the page moves the selected group. The bottom-right blue corner handle resizes the group proportionally, including stroke widths. Both operations publish one undo/save operation on release, and cancellation restores the initial snapshot. Movement is constrained to page bounds without distorting the group or accumulating drift; lifting below the pressure threshold ends the drag and ignores subsequent false-hover moves.

Selection input no longer clears selected ink just because an eraser brush preset was previously active. When zoomed, a finger drag on a selection takes priority over page panning. Finger touches outside a selection still do not draw with finger drawing disabled.

The always-visible top-right Save button was removed. Autosave and save-on-exit remain; a retry control appears only if saving fails.

Validation: **47 instrumentation tests passed** (the previous 38 plus 9 regression cases for dragging, resizing, zoom, pressure cutoff, cancellation, eraser-preset selection, and finger rejection). The emulator editor was inspected to confirm the Save button is absent. The updated APK was installed on the XPPen tablet with `adb install -r`, without removing local data. Real stylus feel still requires handwriting on the tablet.
