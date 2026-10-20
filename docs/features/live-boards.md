---
title: Live boards
description: Excalidraw boards inside Folio with Folio's pen filters, a Live tab in the library, and live sessions for video calls.
---

Live boards let you write with the stylus during a call while others watch and draw from a browser. A board is an [Excalidraw](https://excalidraw.com) scene running in a WebView. Folio filters the pen before the page sees it, so the board feels like a Folio notebook: the same threshold, pressure curve and sensitivity.

Status: proof of concept on the `development` branch (commit `9c50cd1`), tested on the tablet emulator and the XPPen MNP1095.

## For users

- **Library → Live tab** (after Books) lists boards as cards with a preview. **+** on that tab creates a board; the card's **⌄** menu opens, renames or deletes it.
- **Board header:** back (saves and returns to the library), the board name (tap to rename; shows **● Live session** during a session), **✋ Fingers** (off by default: only the pen works, so a resting hand can't zoom or tap), **Pen on the board** (pressure settings) and **Share**.
- **Share dialog:**
  - **Live session:** *Start live session* opens Excalidraw's own collaboration dialog (*Start session*). While a session runs, the dialog shows the link with *Copy link*, *Send link…* and *Stop session…*.
  - **Join a session:** paste an Excalidraw room link (`…#room=…`, filled in from the clipboard when one is there) and *Join*.
  - **Export:** *Image (PNG)* of the board, or an *Excalidraw file* (`.excalidraw`) that excalidraw.com and the Excalidraw apps open.
- **Pen on the board:** *Sensitivity* (default 0.75), *Lightest* and *Strongest stroke*, *Smoothing*, *Reset to defaults*, and *Pen settings…* (the app-wide threshold and pressure curve).

## How it works

| File | Role |
|---|---|
| `LiveBoardActivity.kt` | Board screen: WebView setup, header, Share and pressure dialogs, loading and saving boards |
| `PressureFilteringWebView.kt` | Pen filter between Android and the page |
| `PenPressureMapper.kt` | Raw pressure → board pressure; `penPressure()` is shared with the notebook canvas |
| `LiveBoardStore.kt` | Boards on disk, plus `LiveBoardPreview` for library cards |
| `shared/.../home/HomeBoards.kt` | The Live tab (cards, rename, delete, new board dialog) |
| `DrawingProfiles.kt` | `sensitivityCurve()`, shared by brushes and the board |

### Pen filter (`PressureFilteringWebView`)

1. **Threshold as hover.** A stroke starts when pressure passes the app's pen threshold and **ends** when it drops back to it. The MNP1095 keeps sending `MOVE` with near-zero pressure after the tip leaves the glass; without this the board kept drawing in the air.
2. **Pressure.** Every sample, including batched historical ones, gets `min + sensitivityCurve(penPressure(raw), sensitivity) × (max − min)`. Chromium hands it to the page as `PointerEvent.pressure` with `pointerType = "pen"`; Excalidraw stores it per point (`pressures`), so collaborators see the same widths.
3. **Single-pointer pen stream.** The page only ever sees the pen, as one pointer, and every pen down gets its up or cancel. When a palm and the pen touched together, the pen's lift-off used to be lost; Excalidraw then kept a "pressed" pointer and turned later strokes into pinch-zoom.
4. **Palm rejection.** Fingers are dropped while the pen touches or hovers, for 0.7 s after, and always when *Fingers* is off. A finger gesture the page never saw start (or saw cancelled) is dropped as a whole.
5. **Smoothing** (optional): exponential smoothing of the pen position.

### Excalidraw specifics

- excalidraw.com keeps the scene in `localStorage`: `excalidraw` (elements) and `excalidraw-state` (appState).
- **Pressure only counts with variable width.** Excalidraw stores `strokeOptions.variability` per stroke; its default, `"constant"`, ignores pen pressure. Folio sets `currentItemStrokeVariability = "variable"` every time a board loads.
- The *Live collaboration* button is `.collab-button`; a session shows up as `#room=<id>,<key>` in the page URL, which the header polls once a second.

### Saving and loading boards

- `files/liveboards/index.json` lists boards; each has `<id>.json` (Excalidraw's `elements` and `appState` strings) and `<id>.jpg` (preview).
- **Load:** a document-start script (`WebViewCompat.addDocumentStartJavaScript`, androidx.webkit) writes the board's scene into `localStorage` before Excalidraw's scripts run. A `sessionStorage` key (`folio-board`) stops a reload of the same board from overwriting newer, unsaved changes. WebViews without document-start scripts get the same script after load, then one reload.
- **Save:** every 15 s and on back, `localStorage` is read with `evaluateJavascript` and written atomically (temp file and rename). Back waits for the save, so the preview is taken while the board is still on screen.
- **Preview:** `PixelCopy` of the WebView's area (API 26+). `View.draw` can't see Excalidraw's GPU canvas and gives a blank image.

## Pitfalls found while building it

| Symptom | Cause | Fix |
|---|---|---|
| Excalidraw blank, toolbar 0 px tall | `AndroidView` gives the WebView `WRAP_CONTENT`, so the page's viewport collapses | Set `MATCH_PARENT` layout params; load the URL after the first layout (`doOnLayout`) |
| Lines keep drawing after lifting the pen | MNP1095 sends `MOVE` with low pressure in the air | End the stroke when pressure ≤ threshold |
| Pen zooms in and out instead of drawing | Lost pointer-up for the pen in mixed pen + palm events | Extract the pen as a single pointer; always send up or cancel |
| Toolbar tool or zoom changes by itself | Palm taps and two-finger touches reach the page | Fingers off by default; palm rejection window |
| Pressure "does nothing" | Excalidraw's `constant` stroke variability | Force `variable` on load |
| Widths barely change | Excalidraw's width range is small (~3×) and pens rarely pass 0.6–0.7 raw | Board *Sensitivity*, same curve as notebook brushes |
| Settings edited with `run-as` don't apply | SharedPreferences are cached in the running process | Restart the app, or change them in the app |

## Testing

- `adb shell input stylus swipe …` sends real stylus events (constant pressure) on the emulator; `input tap` is a finger and is ignored while *Fingers* is off.
- WebView DevTools: `adb forward tcp:9333 localabstract:webview_devtools_remote_<pid>`, then `curl localhost:9333/json` for the page's WebSocket URL. Useful to read `localStorage` or what pointer events the page receives.

## Limits and next steps

- Depends on **excalidraw.com** and its room server. Self-hosting Excalidraw (and `excalidraw-room`) under our domain would remove the dependency and allow more line thinning than Excalidraw's fixed width range.
- **Images** inserted on a board live in the page's IndexedDB and aren't saved with the board yet.
- **During a live session** Excalidraw keeps the scene in the room; drawing done in a session may not reach Folio's saved copy.
- A preview taken while a dialog is open shows the dialog.
- Live boards aren't in `.folio` backups yet.
- **Privacy:** boards load excalidraw.com, and a live session sends the drawing (end-to-end encrypted) through Excalidraw's servers. The privacy policy covers this; the Play Data safety form needs a review before live boards ship to production.
