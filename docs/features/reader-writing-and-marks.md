---
title: Reader pens, selection and marks
description: Writing in the PDF reader with the notebook's pens, the selection toolbar, word and stylus selection, and the highlight and underline editor.
---

The PDF reader writes and selects the way the notebook does.

## Pens

The reader's drawing toolbar uses the notebook's pen bar (`BrushPresetBar`), pen settings (`BrushSettings`) and saved pens (`BrushPresetStore` in the `editor` preferences). A pen you change in the reader changes in the notebook. All brush types work, with pressure. The selected pen is remembered (`reader-selected-brush`).

The bar: close, **Pan and zoom** (keeps the bar open), pens, eraser, undo and redo, bookmark, select text and more.

Ink is stored in the annotation anchor: `brush`, `engine`, `variant`, `texture`, `grain`, `angle`, `sensitivity`, `pressures` and `times` (milliseconds since pen-down, so a saved stroke replays exactly like the live one). Older plain-line ink renders unchanged, and exporting the annotated PDF still flattens brush strokes to plain ink.

A finished stroke stays visible as a pending preview until its saved copy is projected, so lifting the pen never flashes. Footer text and the undo and redo buttons do not change while a stroke is saving.

With **Stylus for writing** on, a short touch is a dot, never a page tap that opens the menu or turns the page.

## Selecting text

- **Long press** a word (finger or stylus) to select it, then drag the handles to extend. The handles are a stem with a large dot and a 40 dp grab area. With the stylus, hold still slightly longer than with a finger, so pauses while writing do not select.
- Touching a handle with the stylus only moves the handle; it never draws.
- A long press never opens the mark editor. When the selection touches a saved mark, the toolbar shows a **palette** button for it.

The selection toolbar is one compact row: grip, highlight, underline (solid, wavy or dashed), color, copy, note, a **More** menu (search in book, share, remove marks) and close. Drag the grip to move it; it starts 40 dp clear of the selection handles.

## Highlight and underline editor

Choose the type, see a live preview, pick one of 11 colors or a custom color (the notebook's color picker), then **Save** or **Delete**. The first four colors are the original palette, so existing marks keep their swatch.

## Bars and icons

The reader's top and bottom bars are compact (`ReaderAction`, 50 dp) and use Lucide icons for controls without a custom Folio glyph.
