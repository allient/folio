---
title: "PDF and EPUB reader"
description: "How Folio’s PDF and EPUB reader works: rendering, highlights and margin notes, search, OCR, bookmarks and export."
---

Android now has a Library for books and PDFs alongside notebooks. Books can be organized in the
same folders used by Knotes notebooks, and importing while a folder is selected files the new book
there. Book cards show a two-line title limit and reading percentage. Imported PDF files remain PDFs;
EPUB files are opened with Readium. Importing inside a notebook deliberately retains the existing
PDF-to-notebook-page conversion workflow. Older rasterized PDF imports cannot recover their original
text: reimport the original file into Books & PDFs.

## Engines and storage

- PDFium Android 2.0.3: native page rendering, selection geometry, text search. One page bitmap is
  displayed at a time, capped at 2560px wide and 4096px tall. Native documents/pages/text handles are closed.
- Readium Kotlin 3.4.0: established EPUB navigator, publication parsing, locator anchors, decoration
  highlights, table of contents, chapter search, reflow and reader preferences.
- PDFBox Android 2.0.27.0: background PDF annotation export only. Original text/vector content is retained;
  highlight, underline, ink and text comments are standard PDF annotations, not painted screenshots.
- ML Kit bundled Latin recognition 16.0.1: explicit on-device recognition of scanned PDF pages.
  OCR word boxes are bounded in memory to three pages; saved highlight geometry persists independently.
- Existing SQLite infrastructure: separate versioned reader database with WAL, indexed annotation rows,
  foreign keys and atomic writes. A process-wide ordered queue allows accepted writes to finish after
  an Activity closes. Room has not been added as an unused dependency or forced migration.
- Shared Kotlin document/annotation presentation models live in `apps/mobile/shared/.../reader`.

Originals are copied into private app storage with SHA-256 identities. Duplicate imports reuse the
library record. Import limits: 128 MiB per original; EPUB archive limits: 20,000 entries, 64 MiB per
resource and 512 MiB expanded. EPUB path traversal is rejected. Reader documents and annotations
influence the automatic backup signature. Library backups keep reader titles, content hashes, source URI
references, Knotes folder assignments, reading positions, bookmarks and annotations, but never
include PDF/EPUB bytes or covers. Restored books appear as unavailable until their original is
reimported; a matching content hash reconnects it to saved annotations, folder and progress.

## Features

- PDF/EPUB filtering, clearable title/author search, shared folder organization, covers, and cover-level reading progress.
- PDF page navigation by horizontal swipe, page arrows, or page jump; pinch zoom and pan, text-range selection, highlights and underlines. Swipes only turn pages in Read mode at normal zoom so selection, ink, and zoomed panning retain their gestures.
- PDF ink, notes at a location, bookmarks, annotation list, editing/deleting note comments.
- EPUB selection menu for highlights/notes, persistent locator-based decorations and bookmarks.
- EPUB font size, line spacing, continuous/paged reading, light/sepia/dark themes.
- EPUB page turns by horizontal swipe in paged mode, with arrows retained as an accessible alternative; continuous mode remains vertically scrollable.
- Study view splits reading from the full Knotes notebook editor. It creates a saved notebook for the current book and seeds its page with the book title, page/progress reference, and selected passage when available.
- Search within a document with cancellable queries and bounded result lists.
- Markdown note export and annotated PDF sharing.
- Android Open With handling for PDF/EPUB content URIs.

## Verification

Build: `./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest :shared:testAndroidHostTest`

Instrumentation: `ReaderTest` exercises originals/deduplication, selection/search, persistence across
reading-position updates, annotated PDF export preserving text, EPUB import/anchors, archive traversal
rejection and full library backup/restore. `InkCanvasViewTest` covers pencil movement, small-selection
hit targets, resizing, cancellation, layer safety and pressure cutoff.

Run device tests on a disposable emulator. Gradle's `leaveApksInstalledAfterRun` is set to preserve
installations, but verify runner behavior before using any device with valuable local data. For
manual test runs, install app/test APKs with `adb install -r`, then invoke the instrumentation runner
without an uninstall or clear step.

## Remaining release work

This is an Android implementation. The iOS target remains the pre-existing starter shell: integrate
PDFKit and Readium Swift with a matching persistence adapter before advertising iOS reader support.
DRM/Kindle formats and password-protected PDF opening are not supported in this iteration. OCR is
Latin-script only, invoked per page; OCR text is not embedded back into the original PDF. PDF navigation
is single-page; tiled deep zoom, PDF outline navigation, pressure-sensitive PDF ink and PDF erasing are
future enhancements (saved ink can currently be removed from the annotation list). Existing external
PDF annotations are rendered, but importing/editing their semantic records is not implemented.

The open-source PDFBox Android port's latest published version is older than the rendering libraries;
keep it isolated to export, monitor upstream security changes and validate complex/encrypted PDFs
before release. No commercial SDK subscription, backend, cloud sync or tracking service was added.
Release builds should use AAB ABI delivery to avoid shipping every PDFium/ML Kit architecture to a device.
