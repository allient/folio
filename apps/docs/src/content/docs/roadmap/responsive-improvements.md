---
title: "Responsive layout: possible improvements"
description: Layout problems found when checking Folio in landscape and at phone size, kept for later.
---

2026-10-19

Folio is designed for tablets in portrait. A check on the emulator in landscape (1920×1200) and at phone size (1080×2340 at 420 dpi) found the issues below. The editor top bar on phones is already fixed; the rest is kept here for later.

## Done

- **Editor top bar on phones.** Below 600 dp wide the notebook title gives way and fewer brushes show (`compactBar` and `maxBrushes` in `NotebookEditor.kt`), so undo, redo and the page controls always fit.

## Library, landscape

- The sidebar becomes permanent but the grid stays at four columns, so the right side of the Folders and Canvas rows is mostly empty.
- Folder cards are tall: two cards fill about 40% of the screen height, and only about one row of notebooks shows above the fold.
- The segmented control (All / Notes / Canvas / Books) is centred with a large gap to the search and menu icons.
- Idea: compute the column count from the available width, use shorter folder cards in a horizontal row, and left-align the segmented control next to the title.

## Library, phone

- Three columns with large 4:5 cards means a lot of vertical scrolling, and names truncate ("Work proje…", "Lecture not…").
- The folder row leaves an empty third slot.
- Idea: two columns below 400 dp, or a list view by default; allow two-line names.

## Library, density

- On the same 1200×1920 screen, the library switches between a drawer and a permanent sidebar depending on display density (observed at 260 dpi in portrait). Layout choices use dp width, so a tablet with a large display-size setting can get the phone layout.
- Idea: decide from the window size class and keep the choice stable when density changes.

## Editor, landscape

- The left tool rail is cut by the shorter height. Sticky, Ruler, Zoom, Page, Text and Insert need a scroll, and the pinned **More** button sits close to the last visible item.
- The top bar has a wide empty title field on the left, with brush presets and page controls pushed far right.
- The page is scaled to fit the width, so only part of it is in view.
- Idea: a two-column rail in landscape, or a collapsible rail with the most-used tools first; cap the title field width; fit the page to the height in landscape.

## Editor, phone

- The rail is 49 dp wide, which leaves a narrow page on a 411 dp screen. Idea: a collapsible rail or a bottom tool bar.
- The notebook title is hidden below 600 dp. It can still be renamed from the More menu; a title in a second row could bring it back.
- **More** sits on the last rail item. Check that Text and Insert remain reachable by scrolling.

## Other ideas

- Test every screen in the Android window size classes (compact, medium, expanded) and in split-screen and freeform windows.
- Add screenshot tests at 411×891, 800×1280 and 1200×1920 in portrait and landscape.
- Reader: check the study view (book and notebook side by side) at the same sizes.
