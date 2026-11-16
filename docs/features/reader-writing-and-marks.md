---
title: Reader pens, selection and marks
description: Writing in the PDF reader with the notebook's pens, the selection toolbar, word and stylus selection, and the highlight and underline editor.
---

The PDF reader writes and selects the way the notebook does.

## Pens

The PDF reader has no separate "Notes" step: its pens sit in the reader's top bar. One tap on a pen picks it and starts writing; the pen in use is drawn in sage (`selected` for accessibility), and tapping it again opens its settings (type, size, colour, pressure, add or delete a pen).

The pens are **every pen saved in the notebook editor** (`readerBrushes` in `ReaderChrome.kt`, from `BrushPresetStore` in the `editor` preferences), in the same order, so colours, widths and several highlighters in different colours are ready to use without editing anything, and a pen you change in the reader changes in the notebook. They scroll in their own strip (three visible on a phone, five on a tablet) so the eraser, undo and the other actions stay in place; the strip scrolls to keep the pen in use in view. A pen in use that is not saved is added to the strip, and without any saved pen the Pen, Highlighter and Pencil stand in. Long-pressing any pen opens the same settings.

Picking a pen hides the other reader controls and keeps the top bar on screen, with the pen in use in sage and **Undo** and **Redo** active. With **Gestures → Hide the bar while writing** on (off by default) the bar hides as soon as a pen is picked, and a short **finger** tap in the middle of the page shows or hides it; a stylus tap still leaves a dot. While a pen or the eraser is active, one finger writes and **two fingers swiping sideways turn the page** (right to left for the next page, or the reverse in right-to-left books). The **eye** (Read mode) at the start of the pens is always there and marked while no pen is active; tapping it puts the pen down and returns to reading (Read mode pans and zooms and brings back all the reader controls). Back and tapping the eraser again do the same. The first time the bar hides, a short notice explains the middle tap.

The top bar is one scrolling row after the back arrow. Tablets: the **eye (Read)**, the **saved pens** (five visible, scrolling), **Eraser**, Undo, Redo, Bookmark, Gestures, Doc, More; the order never changes when a pen is picked, and Undo and Redo are dimmed while reading. Phones (under 600 dp): the eye, the saved pens (three visible), Eraser, Bookmark, Gestures, Doc and More; while writing Undo and Redo join after the Eraser. Search is the first button of the bottom Progress panel (where Contents was; Contents stays in the tab bar). OCR is not in the bar; **More → Recognize scanned page** still runs it. Select text is in the bottom bar. EPUBs have no ink, so they keep a **Notes** button that opens highlights and notes.

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
