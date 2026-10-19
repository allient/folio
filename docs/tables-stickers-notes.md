# Tables, stickers and sticky notes

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

- Inserting a block switches to **Objects** mode with the block selected. A floating toolbar appears (table: header row/column, rows and columns +/-, equalize, border width, border and fill
  colour, delete; sticky note: colour, edit text, font size, delete). Drag the block to move it, drag the bottom-right dot to resize.
- In Objects mode a finger only selects/moves objects when **Write with finger** is on (Gestures panel), the same rule that already applied to images and text boxes.
- Tap empty page in Objects mode to deselect.

## Known gaps

- Cells have no typed text; tables carry ink only. Individual row and column dividers cannot be dragged (use Equalize, or resize the whole table).
- No cell merge, per-cell fill, or row/column selection handles as in the reference design.
- Sticker set is built-in emoji (five categories); there is no sticker search, recents, or imported sticker packs.
- Exports use the shared page renderer, so blocks are drawn in PNG and PDF exports; this has not been checked on a real export yet.
