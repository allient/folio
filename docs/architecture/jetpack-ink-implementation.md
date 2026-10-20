---
title: "Jetpack Ink implementation and code review"
description: "How Folio uses Jetpack Ink for low-latency, pressure-sensitive handwriting on Android, with a code review of the integration."
---

2026-10-16

## Implemented

- Pinned stable AndroidX Ink 1.0.0 brush, strokes, and rendering artifacts. Uses public APIs only; Android lint caught and helped remove a restricted native clear() call.
- New freehand strokes use InProgressStroke for incremental geometry and CanvasStrokeRenderer for rendering. Stock brush families are pinned to V1. Fineliner/Marker use marker, Highlighter uses highlighter, other pen styles use pressurePen; custom colors, widths, and pressure profiles remain available. Pencil is not a textured graphite simulation.
- Knotes processes physical contact events and historical samples first. Raw pressure at/below the selected cutoff commits/ends the current logical segment, even if Android keeps sending MOVE. Only accepted points and remapped pressure reach Ink. UP coordinates are still excluded to prevent lift-off jumps. No predictions are persisted.
- Relative input times and `jetpack-ink-v1` engine metadata are persisted with vector points. Legacy strokes and geometric shapes retain their prior renderer. Shared thumbnails and PDF/PNG output use the same version-aware renderer. Partial eraser interpolation preserves timestamps.
- API 29+ hardware drawing retains completed page commands in RenderNode blocks of 32 strokes, plus separate paper/image/text blocks. Appending ink updates the affected tail block, while unchanged blocks are reused. Live ink draws at the correct layer depth, below same-layer text and higher layers. Software/older-API rendering retains the vector fallback.
- Mutable rendering scratch is thread-local, avoiding races between UI drawing and worker exports. Legacy Paint/Path objects are reused; unnecessary collection copies and active-point snapshots were removed from the live drawing path. Unchanged assignments no longer request redraws; drawing requests use display-frame scheduling without discarding input samples.
- Image decoding and EXIF orientation run on workers. Global decoded cache is bounded to 24 MiB; each editor pins a sampled image set with an aggregate pixel budget (about 24 MiB), thumbnails use a smaller budget (about 1 MiB). This prevents images disappearing when another view evicts the shared cache. Export decoding is permitted synchronously on its IO worker. Image IO is excluded from normal view drawing.

## Best-practice review fixes

- Large SQLite page payloads read through bounded text chunks to avoid CursorWindow row-size failures.
- Export flushes pending ink and captures immutable current-page/notebook snapshots before starting IO.
- Backup export enforces the same entry, JSON, per-asset, and aggregate limits as restoration; partial output is removed on failure. Archive names are import-compatible.
- Backup import remaps the last-viewed page when IDs are regenerated.
- Library/editor imports are retained in NotebookViewModel, queue while loading, and merge against current documents. Missing destinations cause owned imported media cleanup.
- Recorder prepare/start/stop/release are serialized off main. Retained lifecycle state handles stop requests while start is pending, and completed attachments survive activity disposal.
- ViewModel library changes are marshalled to main; intentionally deleting every notebook no longer regenerates a default notebook at startup.
- Image fitting accounts for EXIF rotation, and decode handles rotation/mirroring.
- Canvas exposes an accessibility label and calls performClick for completed touch gestures.

## Verification performed

`./gradlew :androidApp:assembleDebug :androidApp:lintDebug` succeeded.

Final lint report: **0 errors, 33 warnings, 1 hint**. Remaining warnings include available dependency updates, style/KTX suggestions, launcher/backup configuration, and allocations on content/cache-rebuild paths. The project is not warning-free and this review is not a guarantee that every code path is defect-free.

Manual Android API 36 emulator checks:

- Opened a legacy notebook successfully.
- Drew two new separate pen strokes with mouse/finger input enabled: no connecting segment.
- Undo removed the second stroke.
- Restarted the process: the first stroke reloaded from SQLite and rendered in the library thumbnail and editor.
- Runtime log: `Jetpack Ink 1.0; hardwareCanvas=true; commandCache=true`.

No automated test suite was run for this update. The new retained import/audio paths and EXIF handling passed compilation/lint and code review; they have not received full device interaction coverage.

## Limits and next measurements

- API 29+ hardware windows now use public InProgressStrokesView front-buffer authoring for freehand strokes on the top visible layer without same-layer text. Other layer arrangements and tools retain the cached Canvas renderer. Only filtered samples reach authoring, and immutable vectors are persisted before GPU handoff. Device latency has not been measured.
- Emulator functionality is not a physical XPPen pressure/hover test or a performance benchmark. Measure frame times, UI/RenderThread work, memory, and stylus latency on the tablet before claiming a speedup.
- Zoom changes can re-record completed blocks. Very large pages still need scale measurements; native geometry caches are bounded by input count rather than an exact native-byte measurement.
- Hardware-accelerated rendering and shared UI do not make the Android backend portable to iOS automatically.
- Hard process termination during an import or recording cannot guarantee completion. Export preparation remains tied to its UI coroutine.
- The pressure workaround remains app-local and does not repair the pen hardware.

## Primary references

- [Stable Ink releases](https://developer.android.com/jetpack/androidx/releases/ink)
- [Ink architecture](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/ink-api-modules)
- [RenderNode](https://developer.android.com/reference/android/graphics/RenderNode)
- [Custom view optimization](https://developer.android.com/develop/ui/views/layout/custom-views/optimizing-view)

## Pen-lift flicker correction

- Keep the Save icon mounted and visually stable during automatic background saves; previously every stroke briefly replaced it with a spinner. Save errors still expose the retry action.
- Invalidate the dry canvas immediately in the Ink finished callback, alongside removal of wet strokes. Do not defer that handoff invalidation through postInvalidateOnAnimation, which can leave the dry display list one frame behind. Normal move redraws remain frame scheduled.
- Compilation and tablet installation do not establish that a rapid visual artifact is resolved on physical hardware; confirm by writing repeated short strokes on the tablet.

## Full-range pressure dynamics (2026-10-16)

- Ink artifacts are now pinned together to **1.1.0-alpha09**, because public custom brush construction is unavailable in stable 1.0.0. This introduces an alpha dependency; compilation is not a substitute for device validation.
- Stable 1.0.0 pressurePen V1 maps normalized pressure 0.8–1.0 to a 1.0–1.5 size multiplier. That narrow range explains why pressure changes below 0.8 did not visibly affect thickness. Source: official Google Maven ink-brush-android 1.0.0 sources, StockBrushes.kt.
- New `jetpack-ink-v2` freehand strokes use public custom BrushFamily/BrushTip/BrushBehavior APIs. Pen, Pencil, Fountain pen and Brush pen map the entire accepted pressure range 0–1 to artistic diameter ratios of 0.20–1.80, 0.15–1.45, 0.15–2.10, and 0.08–2.60 respectively. These are brush design choices, not measured hardware thresholds. Pencil also maps pressure to opacity 0.35–1.0; this is not graphite texture simulation.
- No activation thresholds, editable pressure curves, or raw input/contact filtering were changed. New Brush pen strokes no longer square the already curved pressure again.
- Front-buffer authoring, cached rendering, thumbnails and export use the same versioned family and input mapping. Existing `jetpack-ink-v1` strokes retain stock V1 families and the prior Brush pen mapping; legacy strokes retain their renderer.
- Fineliner, Marker and Highlighter retain their consistent-width stock families.
- AssembleDebug succeeded. Actual pressure feel and alpha runtime behavior still require tablet use; no automated tests or pressure calibration were run.

References: [Ink release notes and public custom API availability](https://developer.android.com/jetpack/androidx/releases/ink), [BrushFamily](https://developer.android.com/reference/androidx/ink/brush/BrushFamily).

## Zoom controls and panning

- The canvas clips in viewport coordinates before zoom and pan transforms; its Compose AndroidView is also clipped to bounds, preventing page content from drawing into the top or side rails.
- While zoom exceeds 101%, a rounded floating chip shows the current percentage and resets zoom plus position when tapped.
- Pan mode now tracks pointer positions in screen pixels and advances the drag origin per move. This fixes the prior behavior that interpreted panning as page coordinates and repeatedly accumulated from a stale origin. Panning remains clamped to the zoomed page bounds.
- Two-finger gestures now track their centroid separately from pinch scale, so a constant-distance two-finger drag translates the page horizontally and vertically while zoomed.
- `:androidApp:assembleDebug` succeeded for this update.

## Path rendering for finished strokes (2026-10-18)

- `CanvasStrokeRenderer` now uses `forcePathRendering = true`. Ink's default mesh rendering on API 34+ crashed `libhwui` `MeshOp::onPrepareDraws` on the MNP1095 (Android 14 `UP1A.231005.007`) when drawing finished strokes, especially tiny ones. `InProgressStrokesView` wet ink remains enabled and was tested without crash. Details, bisect and risks: [mnp1095-mesh-crash.md](/investigations/mnp1095-mesh-crash/).
