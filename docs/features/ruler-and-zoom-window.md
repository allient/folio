---
title: "Ruler and zoom window"
description: "Folio’s ruler for straight lines and angles, and the zoom window for writing small details with the pen."
---

Two page tools added to the notebook editor. Both are screen-space helpers: neither adds model, storage or format state.

## Reference

The ruler design follows **XNotes for Android** by Shardul Vikram Singh
(<https://github.com/shardulvs/xnotes-android>, MIT licence, copyright 2026). The XNotes sources
studied were `app/src/main/java/com/xnotes/canvas/Ruler.kt` (geometry, hit-testing, edge projection,
axis snapping) and the ruler branches of `InteractionController.kt` (grab order, two-finger transform,
pen magnet). No code was copied; `Ruler.kt` here is a fresh implementation of the same ideas on
`android.graphics`, so the MIT notice does not need to be carried, but the design credit stays.

XNotes does **not** have a zoom window. That feature follows the zoom-window panel seen in another
note-taking app (a box on the page, a magnified writing panel below with locate, left, right, undo,
redo and close controls). Nothing was taken from that app's code.

## Ruler

Files: `apps/mobile/androidApp/.../Ruler.kt` (geometry and drawing), `InkCanvasView.kt` (touch, snapping), `NotebookEditor.kt` (rail button).

Behaviour
- Rail button **Ruler** (selected = visible, unselected = hidden). Visibility is not persisted.
- An infinite band fixed in screen space. It does not scroll or scale with the page, so only the
  perpendicular offset from the centre line matters for hit-testing and snapping.
- Graduations are millimetres, half-centimetres and centimetres, measured from the band centre. Spacing is
  `xdpi / 2.54 * zoom` pixels per centimetre, so it measures page content at the current zoom. Minor ticks
  drop out below 3.2 px; label steps widen (1, 2, 5, 10 cm) when zoomed out. Labels skip the buttons, handles
  and angle read-outs.
- One finger on the band moves it. Two fingers on the band move and twist it. A rotation handle (two, at
  ±26% of the short screen side) rotates about the centre. Rotation pulls onto 0/90/180/270 within 4°.
- Two buttons at the centre: **lock position** and **lock angle**. A locked band swallows finger touches on it.
- Angle read-outs at both ends, 0–359°.
- Pen magnet: a pen stroke or shape that starts within 24 dp of either long edge is projected onto that edge
  for the whole stroke (`rulerSnapSide`, applied in `gesturePosition`). The band is drawn above ink and is
  never grabbed by the pen except on its handles and buttons, so writing along the edge is safe.
- The eraser, lasso and other tools are not snapped.

Known gaps
- Snapping has only been exercised with synthetic touches on the emulator, not a real stylus.
- Numbers are drawn along the band and appear upside down past 90° of rotation.
- No length read-out and no protractor mode yet (XNotes has neither beyond the angle box).

## Zoom window

Files: `InkCanvasView.kt` (`CanvasMode.ZoomWindow`, `zoomWindow`, `showRegion`, `documentAspect`), `NotebookEditor.kt` (panel and menu entry).

Entry: **100%** rail button → Zoom → **Zoom window** (next to Free zoom-in).

Behaviour
- A blue box marks the area on the page. Drag it to move it, drag the bottom-right dots to resize,
  or tap empty page to put it there. While the mode is active the main page does not write.
- The bottom 40% of the screen is a second `InkCanvasView` showing exactly the box region, filling the
  panel width. Writing there (pen or finger) edits the same page, so ink appears in the box live.
- Panel buttons: locate (move the box to the current view), left and right (step by 90% of the box width,
  wrapping to the next or previous line on paged notebooks), undo, redo, close (removes the box and returns
  to the pen).
- The box height follows the panel's shape: `height = width * ratio`, where
  `ratio = (panelH * mainW) / (panelW * mainH)`. This is why `documentAspect` exists. A paged page normally
  stretches to its view (`documentHeight = view height`), which would stretch ink in a differently
  sized second view; with `documentAspect = mainH / mainW` both views map ink uniformly.
- Works on infinite canvases (box is not clamped there).

Known gaps
- The box and panel are not persisted; closing the editor drops them.
- Pinch and finger panning are off in the panel; use the arrow buttons or move the box.
- Only one zoom window at a time. The ruler can be shown on the main page while the window is open but
  is not mirrored into the panel.
