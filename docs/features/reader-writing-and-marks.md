---
title: Reader pens, selection and marks
description: Writing in the PDF reader with the notebook's pens, the selection toolbar, word and stylus selection, and the highlight and underline editor.
---

The PDF reader writes and selects the way the notebook does.

## Pens

The PDF reader has no separate "Notes" step: its pens sit in the reader's top bar. One tap on a pen picks it and starts writing; the pen in use is drawn in sage (`selected` for accessibility), and tapping it again returns to reading. The pens are only the ones that suit annotating a book (`readerBrushes` in `ReaderChrome.kt`):

| Button | Notebook preset it uses (first match, in this order) |
|---|---|
| **Pen** | Pen, Fineliner, Gel pen, Fountain pen, Brush pen |
| **Highlight** | Highlighter, Marker |
| **Pencil** (tablets only) | Pencil |

Each button is a preset from the notebook's saved pens (`BrushPresetStore` in the `editor` preferences), so your colours and widths carry over, and a pen you change in the reader changes in the notebook. The selected pen fills its own button; a selected pen of another type (or the Pencil on a phone) gets an extra button so the pen in use is always visible. Without any preset of a type, the catalog default is used. The selected pen is remembered (`reader-selected-brush`). **Long-press** a pen for its settings (`BrushSettings`: type, width, colour, add or delete a preset); **More → Pen settings** opens the same settings.

Picking a pen hides all the reader controls, so the page is clear and the pen keeps writing. A short **finger** tap in the middle of the page shows the top bar (the pen in use is sage, **Undo** and **Redo** are active) and another tap hides it; a stylus tap still leaves a dot. The eraser keeps the bar open for its options. Tapping the selected pen or eraser again, or Back, returns to reading (Read mode pans and zooms). The first time, a short notice explains the middle tap.

The top bar is one scrolling row after the back arrow. Phones (under 600 dp): Doc, **Pen**, **Highlight**, Search, Bookmark, More, then Eraser and Gestures when scrolled; while writing: Doc, Pen, Highlight, Eraser, Undo, Redo, then Search, Bookmark, More and Gestures. Tablets: Doc, Search, **Pen**, **Highlight**, **Pencil**, **Eraser**, Undo, Redo, Bookmark, More, Gestures; the order never changes when a pen is picked, and Undo and Redo are dimmed while reading. OCR is not in the bar; **More → Recognize scanned page** still runs it. Select text is in the bottom bar. EPUBs have no ink, so they keep a **Notes** button that opens highlights and notes.

Ink is stored in the annotation anchor: `brush`, `engine`, `variant`, `texture`, `grain`, `angle`, `sensitivity`, `pressures` and `times` (milliseconds since pen-down, so a saved stroke replays exactly like the live one). Older plain-line ink renders unchanged, and exporting the annotated PDF still flattens brush strokes to plain ink.

A finished stroke stays visible as a pending preview until its saved copy is projected, so lifting the pen never flashes. Footer text and the undo and redo buttons do not change while a stroke is saving.

The reader only draws while a pen is picked. In reading mode (no pen chosen) the stylus selects and pans like a finger and never leaves ink; the former **Stylus for writing** option, which let the stylus write without picking a pen, was removed. While a pen is active, a short stylus touch is a dot, never a page tap that opens the menu or turns the page.

## Selecting text

- **Long press** a word (finger or stylus) to select it, then drag the handles to extend. The handles are a stem with a large dot and a 40 dp grab area. With the stylus, hold still slightly longer than with a finger, so pauses while writing do not select.
- Touching a handle with the stylus only moves the handle; it never draws.
- A long press never opens the mark editor. When the selection touches a saved mark, the toolbar shows a **palette** button for it.

The selection toolbar is one compact row: grip, highlight, underline (solid, wavy or dashed), color, copy, note, a **More** menu (search in book, share, remove marks) and close. Drag the grip to move it; it starts 40 dp clear of the selection handles.

## Highlight and underline editor

Choose the type, see a live preview, pick one of 11 colors or a custom color (the notebook's color picker), then **Save** or **Delete**. The first four colors are the original palette, so existing marks keep their swatch.

## Bars and icons

The reader's top and bottom bars are compact (`ReaderAction`, 50 dp) and use Lucide icons for controls without a custom Folio glyph.
