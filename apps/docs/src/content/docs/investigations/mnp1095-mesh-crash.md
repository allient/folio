---
title: "MNP1095 writing crash: Jetpack Ink mesh rendering"
---

2026-10-18

## Summary

Knotes crashed while writing on the XPPen MagicNotePad (Android 14) because Jetpack Ink drew finished strokes with Android's Mesh API, which crashes in that build's renderer. Finished strokes now draw as paths. The tablet no longer crashes, and writing does not feel slower.

- **Symptom:** the app closed within seconds of touching a notebook page with the pencil.
- **Root cause:** `CanvasStrokeRenderer.create()` uses mesh rendering on API 34+, and `MeshOp::onPrepareDraws` in `libhwui` reads from a null pointer on this build.
- **Trigger:** very short strokes (dots, quick taps). The user's damaged pencil makes them often, but a healthy pen makes them too.
- **Fix:** `CanvasStrokeRenderer.create(forcePathRendering = true)` in `JetpackInkEngine.kt`. The low-latency wet-ink layer (`InProgressStrokesView`) stays on.
- **Verified:** two test builds isolated the cause (see Bisect). The user tested the final build on the tablet and reported no crash and no lag.

## Device and crash signature

| Field | Value |
|---|---|
| Device | XPPen MagicNotePad MNP1095 |
| Build | `XPPen/MagicNotePad_NOEEA/MagicNotePad:14/UP1A.231005.007` (Android 14, API 34, first Android 14 release, October 2023) |
| Chip | MediaTek, arm64 |
| Ink | `androidx.ink:*:1.1.0-alpha09` |
| Signal | SIGSEGV, SEGV_MAPERR, fault addr 0x0 (null pointer), thread `RenderThread` |

```
#00 libc.so    __memcpy_aarch64_simd+144
#01 libhwui.so (anonymous namespace)::MeshOp::onPrepareDraws(GrMeshDrawTarget*)+1208
#02 libhwui.so skgpu::v1::OpsTask::onPrepare(GrOpFlushState*)+548
#03 libhwui.so GrRenderTask::prepare(GrOpFlushState*)+120
#04 libhwui.so GrDrawingManager::flush(...)+2760
...
#09 libhwui.so SkiaOpenGLPipeline::draw(...)
#10 libhwui.so CanvasContext::draw(bool)+812
#13 libhwui.so RenderThread::threadLoop()+404
```

Every frame is in Android's own code (Skia inside `libhwui`); the crash happens before the GPU driver is reached. It is a platform bug in early Android 14 mesh code, made reachable because this tablet never received later Android 14 updates. Whether Google fixed it in a later build is not confirmed.

Timing in the two original crashes:

- Crash 1: 16 ms after a stylus `ACTION_DOWN`.
- Crash 2: a ~60 ms tap moving ~6 px, crash 2 ms after `ACTION_UP`.

Finished strokes already on the page drew without crashing for several seconds before each crash. The input events (hover, down, up, coordinates) were normal. Logcat does not record pressure values.

## What was ruled out

- **Pressure filters:** they run in Kotlin. A fault there would be a Java exception, not a native crash on the RenderThread.
- **Autosave:** no file or database activity appears near the crash.
- **Performance or memory:** the heap was 16 MB of 33 MB, with no ANR and no low-memory kill.
- **Permissions:** drawing needs none. A missing permission raises `SecurityException`.

Online search (GitHub, Google issue tracker, Jetpack Ink release notes) found no report of `MeshOp::onPrepareDraws`, `drawMesh` or Jetpack Ink mesh crashes. The release notes list other rendering crash fixes, none about mesh. Treat it as unreported.

## How Jetpack Ink picks mesh or path

Checked in the compiled `ink-rendering` and `ink-authoring` 1.1.0-alpha09:

- `CanvasStrokeRenderer.create(forcePathRendering, textureStore)` builds a `CanvasStrokeUnifiedRenderer`. On `SDK_INT >= 34`, without `forcePathRendering`, it lists `CanvasMeshRenderer` before `CanvasPathRenderer`.
- Each draw asks the mesh renderer `canDraw`, which needs `canvas.isHardwareAccelerated` and a successful test `drawMesh` (`CanvasMeshSupport.canDrawMesh`). Otherwise it falls back to path.
- `InProgressStrokesView` creates its own renderer with `CanvasStrokeRenderer.create(textureStore)` and has no option to force path. On API 33+ its helper `CanvasInProgressStrokesRenderHelperV33` records into a `RenderNode` (hardware canvas), so **the live wet-ink stroke still uses mesh on Android 14**.

| Android version | Default renderer |
|---|---|
| 13 and older (API ≤ 33) | Path only (`android.graphics.Mesh` does not exist) |
| 14+ (API 34+) | Mesh, falling back to path |

**Mesh vs path:** mesh sends Ink's triangles straight to the GPU. Path converts them to a `Path` that Skia fills, which costs more processor time on very dense pages. For handwriting the difference was not noticeable, partly because the app caches completed strokes in RenderNode blocks of 32. Path is the compatible choice: it is the only renderer on API ≤ 33 and it avoids this crash on 34+.

## Bisect

Run on the tablet with Pen-family brushes (Pencil, Chalk and Spray use `NaturalMediaRenderer` and never reach Ink rendering), with many tiny dots and quick taps.

| Test | Finished strokes | Live stroke | Result |
|---|---|---|---|
| Original | Mesh | Wet-ink layer (mesh) | 2 crashes |
| First fix | Path | App-drawn path, wet-ink layer off | No crash, ~1,800 stylus events |
| A | **Mesh** | App-drawn path, wet-ink layer off | **3 crashes** in ~35 s, same `MeshOp` stack |
| B | Path | Wet-ink layer on (mesh) | No crash, dozens of strokes |
| Final | Path | Wet-ink layer on (mesh) | No crash, no lag (user check) |

Only finished-stroke mesh rendering in the main view crashes. Why the wet-ink layer's mesh does not is not proven. It draws one short-lived stroke on its own surface, while the page batches many finished strokes, including tiny ones, into one frame, which is where `MeshOp::onPrepareDraws` runs.

Logs are in the session scratchpad, not in the repo.

## Code changes

`apps/mobile/androidApp/src/main/kotlin/com/allient/folio/JetpackInkEngine.kt`:

```kotlin
// Before
private val renderer = CanvasStrokeRenderer.create()

// After
// API 34+ defaults to android.graphics.Mesh; drawing finished strokes that way crashes
// hwui's MeshOp on early Android 14 builds (MNP1095). Ink's wet layer is unaffected.
private val renderer = CanvasStrokeRenderer.create(forcePathRendering = true)
```

This renderer draws finished strokes and the app-drawn live stroke (used when the wet-ink layer is not active). `InkCanvasView.kt` ends unchanged: the first fix limited `FrontBufferedInkController` to API 29–33, and the bisect showed that was unnecessary, so it was reverted to API 29+. The temporary `MeshTest` switches were removed.

Release build installed on the tablet with `adb install --no-incremental -r`, signed with the local debug key as before.

## Open risks and next steps

- **Wet ink still uses mesh on API 34+.** If a crash ever happens while the pencil is down (not after lift), turn off `FrontBufferedInkController` on API 34+. That configuration was tested without crash or noticeable lag.
- **Report to Google** (androidx.ink component): `CanvasStrokeRenderer` mesh rendering of finished strokes crashes in `MeshOp::onPrepareDraws` on `UP1A.231005.007`, triggered by tiny stylus strokes.
- **Ask XPPen** for a system update beyond the first Android 14 release.
- **Optional:** filter accidental micro-taps from the damaged pencil in the pressure filters, to reduce stray dots.
- **Upgrading Ink:** recheck this doc's renderer analysis after any `androidx.ink` version change; `forcePathRendering` must stay.
