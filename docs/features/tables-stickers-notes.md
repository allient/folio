---
title: "Tables, stickers and sticky notes"
description: "Tables, stickers and sticky notes on Folio pages: how they work and how they are stored."
---

Three page objects added to the notebook editor (rail buttons **Table**, **Stickers**, **Sticky**; also under **Insert**).

## Data model

Files: `NotebookStorage.kt` (`InkBlock`, JSON), `DrawingRenderer.kt` (`drawBlock`), `CompletedInkPageCache.kt`, `InkCanvasView.kt`, `BlockUi.kt`.

- **Tables and sticky notes** are `InkBlock`s stored in `NotebookPage.blocks` (JSON key `blocks`, absent in old files and read as empty).
  Geometry is page-normalised like images (`x`, `y`, `width`, `height`; infinite canvases are unclamped).
  - Table: `rows` and `columns` are fractions summing to 1, `headerRow`, `headerColumn`, `borderWidth` (0..8), `borderColor`, `fill` (0 = none), `headerFill`.
  - Sticky note: `fill` is the note colour, `text`, `textSize`.
- **Stickers** are ordinary `InkImage`s. The emoji is rendered to a 512 px transparent PNG in `filesDir/media` (`renderStickerFile`), so import, export, backup and
  the image selection/resize code all work unchanged.
- Blocks are drawn per layer after images and **before** ink, so pen strokes write over tables and notes. The retained-command cache has a `${layer}/blocks` record.

## Ink moves with a table

When a block is picked for a drag (`startBlockDrag`), every stroke on an editable layer whose bounding-box centre is inside the block is remembered.
Moving the table translates those strokes by the same amount; resizing scales their points about the table's top-left corner. The block list and the strokes are published
together through `onBlocksChanged(blocks, strokes)` so a single page edit (and a single undo step) covers both.
Strokes are not otherwise linked to the table: a stroke written outside it and later overlapped does not follow it.

## Interaction

- Inserting a table switches to **Objects** mode with the table selected; a sticky note is selected in the current tool. The selection bar sits right under the block
  (table: **Edit** for rows, columns, headers and merges, **Fill** for border width, border and fill colour, **Arrange**, **Delete**; sticky note: **Edit** text, **Fill** for colour and
  text size, **Arrange**, **Delete**). Drag the block to move it, drag a handle to resize.
- In Objects mode a finger only selects/moves objects when **Write with finger** is on (Gestures panel), the same rule that already applied to images and text boxes.
- Tap empty page in Objects mode to deselect.

## Cells, rows, columns and merges

- Entering Objects mode (Text rail → **Arrange objects**, or long-press when "Long press to select" is on) and tapping a table selects it; **tapping a cell selects the cell**
  (blue highlight). Tapping a second cell extends the selection to the rectangle between them, grown to include any merged range it cuts through. Tapping the same single cell again clears it.
- **Edit** on the table bar opens the table sheet. With a cell or range selected it starts with **Row above / Row below / Column left / Column right**,
  **Delete row / Delete column** (every selected row or column), **Merge** and **Unmerge**; below that come the Rows/Columns +/- counters, **Equal sizes** and the header toggles.
- Merges are stored as `merges: [[firstRow, firstColumn, lastRow, lastColumn], ...]` on the block. Inserting or deleting rows and columns shifts, grows or drops merges
  (`InkBlock.insertRow/insertColumn/deleteRow/deleteColumn` in `BlockUi.kt`). Merging draws no grid lines inside the merged range; header shading is by position, so a merged header stays shaded.
- Verified on the emulator: select one cell, extend to two, merge (interior line disappears, Unmerge becomes enabled), then Row below (a fourth row appears and the merge is kept).
  Not yet exercised: Delete row/column with merges present, Column left/right, and Unmerge.

## Known gaps

- Cells have no typed text; tables carry ink only. Individual row and column dividers cannot be dragged (use Equalize, or resize the whole table).
- No per-cell fill or row/column selection handles as in the reference design.
- A cell tap also happens on the tap that first selects the table, so the first tap selects the table and that cell together.
- Sticker set is built-in emoji (five categories); there is no sticker search, recents, or imported sticker packs.
- Exports use the shared page renderer, so blocks are drawn in PNG and PDF exports; this has not been checked on a real export yet.
- Stickers and sticky notes were built but not exercised on a device yet.
