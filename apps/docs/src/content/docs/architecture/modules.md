---
title: Modules and the iOS path
description: How Folio's Kotlin Multiplatform code is split today, the rules that keep it portable, and the order for an iOS port.
---

Folio ships on Android only. The code is still a Kotlin Multiplatform project, so an iPad version can reuse as much as possible later.

## Today

| Module | Source set | What lives there | May import |
|---|---|---|---|
| `shared` | `commonMain` | Home screens (Compose Multiplatform), folder editor, backup settings UI, templates, reader models, brand (`FolioBrand`), pure utils (`TextUtils.kt`) | Kotlin stdlib, Compose Multiplatform, lifecycle. **No `android.*` or `java.*`** |
| `shared` | `androidMain` | Android-only helpers for the shared UI | Android |
| `shared` | `iosMain` | `MainViewController()`, a placeholder screen | UIKit / Foundation |
| `androidApp` | `main` | Activities, editor, ink engine, storage (SQLite + `org.json`), files and backups, PDF/EPUB engines, OCR, in-app updates | Anything |
| `iosApp` | – | Xcode shell that hosts `MainViewController()` | – |

The iOS targets (`iosArm64`, `iosSimulatorArm64`) stay enabled in `shared/build.gradle.kts`. Android builds don't compile them, but `./gradlew :shared:compileKotlinIosSimulatorArm64` fails as soon as common code uses a JVM or Android API. Run it before merging changes to `shared`.

## Rules

1. **Put new logic in `commonMain` when it doesn't need the platform.** Models, validation, sorting, search, text and geometry helpers belong there. Platform code calls them.
2. **Keep platform code behind small interfaces.** When common code needs a platform service, declare an interface (or `expect`) in common and implement it per platform. Avoid passing `Context` or Android types through common APIs.
3. **One helper, one place.** Shared pure helpers go in `shared/commonMain` (`TextUtils.kt`); Android view helpers go in `androidApp/ViewUtils.kt` (`dp()`, `ReaderChromeBands`). Search before adding a new one.
4. **Lifetimes.** ViewModels hold the `Application`, never an Activity or View. Objects with listeners (`PlayUpdates`, `PenSettings`, preference listeners) register once and unregister in `onDestroy`/`onCleared`. Views cancel their coroutine scopes in `onDetachedFromWindow`. Native handles (Pdfium, MediaRecorder, Readium publications) are closed by their owner.
5. **No work on the main thread** beyond layout and drawing: storage, JSON, rendering PDFs and OCR run on worker threads or `Dispatchers.IO`.

## Performance notes

- Release builds run R8 (`isMinifyEnabled`, `isShrinkResources`). Keep rules for JNI and reflective libraries (Pdfium, zstd, Jetpack Ink, PDFBox, Readium, ML Kit) are in `androidApp/proguard-rules.pro`. After adding a library, test the release build: R8 problems only show up there (ML Kit OCR broke until its rules were added).
- Stroke points are stored with 5 decimals. Full float precision made pages about twice as large and pushed `org.json` onto its slow `BigInteger` path; a debug build took ~40 s to open a 1.6 MB library on a loaded emulator.
- Debug builds are much slower than release on ART. Judge performance on `just release`.
- Known hot spots, in order of payoff:
  1. `NotebookStorage.load()` parses every page of every notebook at startup. Load notebook summaries first and pages on open.
  2. `NotebookEditor` is one large composable; splitting the toolbar, rail and sheets would limit recomposition.
  3. `InkCanvasView.onDraw` allocates per frame (`PageOrder.objects`); cache per page revision.
  4. Library thumbnails render on every launch; cache them on disk by page revision.

## Order for an iOS port

Each step is useful on Android by itself, and none needs the next.

1. **Models and JSON format → `commonMain`.** Move `Notebook`, pages, strokes and blocks, and replace `org.json` with `kotlinx.serialization` (the `.folio` schema in `public/schemas` is the contract).
2. **Storage → common.** SQLDelight or Room KMP for `knotes.db` and `knotes-reader.db`, keeping the current schema and migrations.
3. **Files and backups → Okio** for ZIP, Zstandard and paths, with a small platform interface for the document picker and the backup folder.
4. **ViewModels → `commonMain`** (lifecycle-viewmodel is already multiplatform), with storage injected.
5. **Ink stays native per platform** behind an `InkSurface` interface: Jetpack Ink on Android, PencilKit or Metal on iPadOS. The stroke model is shared, so notebooks open on both.
6. **Reader engines stay native** behind a `ReaderEngine` interface: Pdfium and Readium on Android, PDFKit and the Readium Swift toolkit on iOS. Annotations and positions are already common models.
