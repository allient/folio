---
title: "Ink rendering research"
description: "How Folio renders handwriting on Android: Jetpack Ink, front-buffered wet ink and the options reviewed for low-latency stylus drawing."
---

Reviewed 2026-10-16. This is a source/code review, not a performance measurement or renderer migration.

## Recommendation

Keep the shared Compose Multiplatform interface and editable document model. Improve the Android rendering backend in two stages: remove unnecessary CPU work in the existing renderer, then evaluate Jetpack Ink for low-latency live strokes. Keep the existing renderer available for exports and fallback until rendering consistency and input filtering are demonstrated.

## Current code observations

- `InkCanvasView.onDraw()` constructs a NotebookPage and calls `NotebookRenderer.drawPage()` on each redraw.
- The renderer filters collections for each visible layer and visits all completed strokes.
- Stroke drawing creates Paint objects and, for some brushes, Path objects. Variable-width ink issues individual drawLine calls for point pairs.
- Live stroke drawing copies activePoints into a new list and constructs a stroke on each frame.
- Several property setters invalidate even when the assigned value has not changed. Compose AndroidView updates assign many properties together.
- Image cache misses perform image decoding inside drawing. Even a cache hit checks the source file's modification timestamp.
- There is a bounded image bitmap cache, but no retained rendering cache for completed ink.
- No explicit software layer is forced. GPU operation should be checked with Canvas.isHardwareAccelerated at runtime; configuration alone is not a device measurement.

These are candidate sources of overhead, not proven frame-time bottlenecks.

## Stage 1: improve the existing renderer

1. Reuse Paint, Path, and scratch buffers; replace temporary collection transformations in hot drawing paths with iteration. Avoid per-frame document model construction and live-point list copies.
2. Invalidate only for changed values. Keep pointer samples ordered and render on display frames; never discard samples just to reduce draw frequency.
3. Prepare image assets asynchronously and keep decoding/filesystem queries out of onDraw. Use placeholders and invalidate after loading. Bound memory use and handle missing assets.
4. Cache completed stroke geometry separately from the active stroke. Invalidate geometry when stroke content, brush, dimensions, or relevant transforms change.
5. Evaluate API 29+ RenderNode caches per layer or bounded chunks. Record completed content when it changes, draw cached command lists during live writing, and render active ink separately at the correct layer depth. Keep a software/API 25–28 fallback.
6. Add viewport/bounds culling for zoomed pages where profiling supports it. Keep eraser, lasso, undo, layer visibility, and page switches coherent with caches.

RenderNode retains drawing commands, not a permanent pixel snapshot. It can reduce Java/Kotlin recording work while the GPU still executes the commands. It does not guarantee constant GPU cost for arbitrarily dense pages.

## Stage 2: evaluate Jetpack Ink

Google's Ink API provides authoring, stroke geometry, brushes, and Canvas rendering, and builds on Jetpack low-latency graphics. Compose has an InProgressStrokes component; Views have InProgressStrokesView. Existing input handling may require the authoring APIs that allow application-controlled stroke lifecycles rather than a default component that directly accepts physical input.

Before adopting it, demonstrate:

- Pressure threshold and curve processing happen before accepted samples reach authoring.
- Falling below the threshold ends the logical stroke even when Android continues delivering MOVE.
- Crossing the activation threshold again starts a fresh stroke with no connecting segment.
- Hover, cancellation, historical samples, palm touches, and page/tool changes remain correct.
- Predictions are temporary visual data only, never saved as actual ink, and cannot revive a filtered stroke.
- Brushes, layers, erasing, selection, PDF/PNG output, and saved legacy strokes retain expected appearance and behavior.
- Low-latency support/fallback works on the actual XPPen tablet. API-level support alone cannot establish device latency.

This is an Android backend choice. The shared Compose UI can remain shared; Android Ink authoring components do not automatically supply an iOS inking backend.

## Avoid premature renderer replacement

- Do not assume switching to SurfaceView, OpenGL, Vulkan, or another Canvas wrapper automatically improves handwriting.
- Do not force a hardware layer on a constantly changing page as a general optimization.
- Do not cache the entire notebook as full-resolution bitmaps. A single 1200×1920 ARGB_8888 image uses about 8.8 MiB before additional copies; zoom and layer caches multiply this.
- Do not promise a latency/FPS improvement without measuring the device.

## Measurements before choosing a final backend

On the physical tablet, compare blank/light/dense pages, sustained writing, marker transparency, zoom/pan, eraser/lasso, and imported images. Measure frame deadlines, UI/RenderThread CPU time, allocations/GC, memory, and input-to-display latency. Emulator mouse behavior can establish functionality but cannot establish XPPen stylus latency. Use Android Studio system traces/Perfetto and frame metrics; keep document geometry and pressure profiles consistent across comparisons.

## Primary sources

- [Android Ink API overview](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/about-ink-api)
- [Ink modules and authoring/rendering separation](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/ink-api-modules)
- [Compose live and completed strokes](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/ink-api-draw-stroke)
- [Optimize custom views](https://developer.android.com/develop/ui/views/layout/custom-views/optimizing-view)
- [Hardware acceleration and drawing practices](https://developer.android.com/develop/ui/views/graphics/hardware-accel)
- [RenderNode command recording](https://developer.android.com/reference/android/graphics/RenderNode)
- [Low-latency graphics and motion prediction](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/advanced-stylus-features)
