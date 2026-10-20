---
title: "BOOX-inspired notes: implementation coverage"
---

Knotes follows the requested [BOOX Handwritten Notes reference](https://help.boox.com/fa5fa/4d151/f57ec) in its compact top toolbar, vertical tool rail, white writing surface, notebook library, and page navigation. This document describes the current source implementation; it is not a claim of complete BOOX feature parity or a hardware validation report.

## Available workflows

| Area | Current implementation | Limits |
| --- | --- | --- |
| App layout | English UI, monochrome notebook grid, compact editor toolbar, tool rail on either side, focus mode with a floating toolbar. Android system bars hide while Knotes is focused. | A system swipe can reveal transient system bars. Layout is Knotes's own implementation. |
| Notebooks | Compose Multiplatform home with folder, all, favorite, and recent views; notebook name/text/tag search; custom folders with long-press drag/drop and Move menus. | Folders are one level deep; drop targets must be visible. No account or cloud library. |
| Pages | Multiple pages, thumbnail navigation, add, duplicate, delete, move earlier/later, and assign outline headings. | The last page cannot be deleted. Page content uses normalized coordinates on a bounded canvas, not an infinite canvas. |
| Writing | Seven Jetpack Ink brushes: pen, pencil, marker, fountain pen, brush pen, fineliner and highlighter; saved width/color/opacity and quick presets; preview uses the actual Ink renderer; mouse and hardware eraser support. | No textured natural-media simulation or tilt shading. Emulator touch input still requires Write with finger; explicit mouse input is accepted without enabling finger input. |
| Pressure | Soft, Balanced, Firm, and custom settings; adjustable activation threshold and two editable Bézier curve controls. Threshold crossings close stroke segments. | Applies to Knotes only. Presets are adjustable defaults, not measured guarantees for every pen. A faulty pen can report values that overlap real writing. |
| Touch handling | Finger writing is off by default; optional finger writing; pen-aware handling ignores extra finger contacts during pen input. | This is app-local handling, not a device-wide palm or stylus driver replacement. |
| Erasing | Partial, whole-stroke and freeform lasso erasers; adjustable size; active-layer or visible/unlocked-layer scope; optional return to previous pen; confirmed clear-ink actions. Swept vector cuts cover motion between samples and retain holes in filled shapes. | Erases handwriting and vector shapes, not image pixels, text boxes or paper. Each erasing gesture publishes one undo/save operation; cancel restores it. |
| Undo/redo | Page editing snapshots, up to 80 undo entries. | History is held in editor memory and is not persisted across app restarts or page editor recreation. |
| Lasso | Freeform and rectangle selection; drag, copy/paste, duplicate, cut, recolor, rotate left/right 90 degrees, grow/shrink, flip horizontally/vertically, delete and tag ink. Direct pen/finger dragging, an explicit Move action and a blue corner resize handle are available; finger dragging works with finger drawing off. Group transforms and paste preserve proportions at page edges. | Applies to ink and vector shapes; text/images use Arrange text & images. Rotation uses 90-degree steps; resizing supports both the continuous corner handle and 10-percent menu steps. |
| Shapes and fill | Ten manual shapes: line, arrow, double arrow, rectangle, square, ellipse, circle, triangle, diamond and star; outline/solid fill and fill closed ink paths. Shapes use fixed width, so pressure changes do not distort their edges. | No automatic handwriting-to-shape recognition; fill operates on closed vector outlines, not image pixels. |
| Layers | Add, rename, reorder, hide/show, lock/unlock, and delete layers; ink, text, and images refer to layers. | Editor UI allows up to six layers. This is not BOOX's proprietary layer format. |
| Paper | Blank white page by default; lined, grid, dot, and to-do paper. Create opens a named notebook/template picker. Local Template Hub offers Square Page, Lined Page, and To-do List with a persisted Downloaded collection. New pages inherit current paper. | Hub templates are bundled and available offline; Download saves availability locally. No online marketplace or scrolling page layout. |
| Text | Typed text boxes, size control, editing/deletion, drag positioning, and timestamp insertion. | No handwriting recognition, rich word-processing document mode, or AI writing assistance. |
| Images and files | Insert, drag, resize and delete images; select, edit and delete text objects; append PDF pages for annotation; attach local copies of files and open them with another Android app. | PDF import rasterizes pages into PNG backgrounds. Original PDF text and objects cannot be edited. |
| Audio | Record microphone audio as a notebook attachment, stop and save it, and open it with an audio-capable app. | Requires microphone permission. Recording stops when the editor leaves the foreground. Audio is not synchronized with ink or a playback timeline. |
| Navigation and search | Notebook tags, selected-ink tags, page outlines, tag/outline navigation, and search through titles, typed text, outlines, and tags. | Handwritten words are not searchable unless manually tagged. |
| Viewport | Two-finger pinch/pan, pan tool, optional single-finger pan when zoomed, zoom badge and fit/reset view. | Page content remains bounded; no expanding or infinite canvas. |
| Export and sharing | All-page PDF; current-page PNG; editable `.folio` backup with assets; Android file picker for saving and Android sharing. | Export page size is 1200 × 1920. `.folio` is a custom ZIP format, not BOOX `.note` compatibility. |
| Local persistence | SQLite notebook/page storage, private media files, background serialized saves, explicit save progress/errors, and migration of the earlier JSON library. | No application cloud sync or collaboration. See [local storage](/architecture/local-storage/). |

## Features outside this implementation

Cloud sync, accounts, and server services are intentionally outside the requested local-only scope. BOOX proprietary formats, its device firmware tools, and its platform services are not replicated.

Handwriting recognition, AI assistance, automatic smart-shape recognition, an infinite canvas, a template marketplace, and audio-to-ink timeline linking remain unimplemented. The pen filter cannot modify stylus events sent to other Android apps, and it does not repair a defective pressure sensor.

Tests and a successful build must be reported separately from this coverage list. A disconnected tablet cannot validate the physical pen's pressure behavior or writing latency.

## Home and template UI update

The library, create screen, and template hub are implemented in `apps/mobile/shared/src/commonMain/kotlin/com/allient/folio/home/NotebookHome.kt`, using platform-neutral Compose components. Android supplies persisted models and native ink thumbnails. Folder names/download choices live in the SQLite settings table; notebook folder/favorite/open timestamps live in notebook metadata. Notebook changes and library settings are committed in the same transaction.

Build: `./gradlew :androidApp:assembleDebug` succeeded. Home, Create, and Template Hub layouts were inspected in the Android emulator. No automated tests were added or rerun for this update; folder drag/drop and persistence still need device interaction coverage.

Notebook cards now open with a tap and show their options after a stationary long press is released. Moving after holding preserves folder drag/drop. The separate options icon below each notebook has been removed. Cards show last-viewed/total page counts and preview that page; reopening the notebook restores its last viewed page.

### Rounded details dialogs and gesture correction

Notebook and folder cards use the standard Compose `combinedClickable` handler: tap opens, long press immediately opens a rounded General details dialog. The Manage folder control has been removed. Notebook actions include Rename, Copy, Delete, Move, Favorites, and PDF/PNG/editable-backup sharing. Folder actions include Rename and Remove (preserves notebooks). Lock, nested-folder moves, and custom covers are not implemented.

Drag no longer competes with the long press: select Move → Drag on home screen, then drag the selected notebook. A destination picker remains available. Inside a folder, the top-left view selectors become a Back arrow and folder name. Home cards, template cards, fields, and editor tool dialogs use rounded Material corners.

Emulator inspection confirmed that real 800ms holds open both notebook and folder General dialogs. `assembleDebug` succeeded.

## Canvas toolbox review — 2026-10-16

The eraser/selection implementation follows the public [BOOX Handwritten Notes tool descriptions](https://help.boox.com/hc/en-us/articles/8569373888788-Handwritten-Notes). See [canvas review and validation](/features/canvas-tools-review/) for the exact checks and limitations.

Useful future candidates are handwriting recognition, hold-to-perfect shapes, automatic lasso recognition, and configurable stylus buttons when the hardware reports them. These are not exposed as functioning tools in this version. Scribble-to-erase needs deliberate opt-in and separate validation before shipping, especially with a faulty stylus. Firmware/system-wide input tools cannot be ported as ordinary Android app controls.
