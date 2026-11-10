# AGENTS.md

Engineering guide for Folio, for human contributors and coding agents. Read it before changing code. The user-facing overview is in [README.md](README.md); in-depth notes are Markdown files in [`docs/`](docs), also published on the docs site (`just docs`).

## Product facts

| | |
|---|---|
| App | Folio: handwritten notes, infinite canvas, PDF and EPUB reader |
| Platforms | **Android only** (phones and tablets). iOS is planned; shared code must stay iOS-compatible |
| Application ID / package | `com.allient.folio` (release). Debug builds are **`com.allient.folio.dev`**, named "Folio Dev", and install next to the store app |
| Store title | `Folio: Notes & PDF Reader` (Google Play allows 30 characters) |
| Privacy | Content never leaves the device. No account or ads. Anonymous usage statistics via Google Analytics (`AppAnalytics`), on by default with an off switch; consent first in EU/EEA, UK and CH. Only runtime permission: `RECORD_AUDIO` |
| Reference devices | Android tablet emulator (API 36, 1200×1920), XPPen MagicNotePad MNP1095 (Android 14), phone-size emulator (~411 dp wide) |
| License | Apache 2.0 |

## Repository map

Bun workspaces and Turborepo: apps in `apps/`, shared packages in `packages/`.

Use Bun 1.4.4 or later (the minimum patched version for CVE-2026-111497). The root
`packageManager` field pins the version installed by the docs GitHub Actions workflow.

```
apps/mobile/                 Gradle root (Kotlin Multiplatform)
  androidApp/                Android app: activities, editor, ink, storage, files, reader, OCR, updates
  shared/                    KMP library: commonMain (home UI, models, brand, pure utils), androidMain, iosMain
  iosApp/                    Xcode shell with a placeholder screen
  fastlane/                  Google Play lanes and store listing (metadata/android/<locale>)
  artifacts/                 Demo books and local release builds (release/ is git-ignored)
apps/docs/                   Astro + Starlight site: landing, /privacy/, developer docs; deployed to Cloudflare
packages/brand/              Brand kit v1.0 (logo, icons, Android launcher resources, guide)
.argent/flows/               Recorded UI automation flows
.github/workflows/docs.yml   The only CI workflow (docs deploy)
justfile                     Common tasks (`just` lists them)
```

Key Android files (`apps/mobile/androidApp/src/main/kotlin/com/allient/folio/`):

| File | Role |
|---|---|
| `MainActivity.kt` | Launcher, library host, "Open with" for PDF/EPUB, in-app update hook |
| `NotebookEditor.kt`, `NotebookViewModel.kt` | Editor UI, editor state and the autosave queue |
| `InkCanvasView.kt` | Canvas input, erasers, lasso, shapes, zoom |
| `JetpackInkEngine.kt`, `FrontBufferedInkController.kt`, `NaturalMediaRenderer.kt` | Ink geometry and rendering, low-latency wet ink, pencil/chalk/spray/paint |
| `NotebookStorage.kt` | SQLite storage and the notebook JSON format |
| `NotebookFiles.kt`, `LocalBackup.kt` | Export, import, `.folio` archives, smart backups |
| `ReaderActivity.kt`, `PdfReaderEngine.kt`, `EpubReaderEngine.kt`, `ReaderStore.kt`, `ReaderExport.kt` | PDF/EPUB reader, annotations, OCR, export |
| `AppUpdates.kt` | Google Play in-app updates |
| `HandwritingRecognizer.kt` | ML Kit Digital Ink: lasso → Convert to text, language choice, model download on first use |
| `LiveBoardActivity.kt`, `PressureFilteringWebView.kt`, `PenPressureMapper.kt`, `LiveBoardStore.kt` | Live boards: Excalidraw in a WebView with Folio's pen filter, boards on disk (see `docs/features/live-boards.md`) |
| `ViewUtils.kt` | Android view helpers (`dp()`, `ReaderChromeBands`) |

## Commands

| Task | Command |
|---|---|
| Install workspace deps | `bun install` |
| Build and install debug on a device | `just install-debug [serial]` |
| Build, sign with the debug key and install release | `just install-release <serial>` (`FOLIO_TABLET` sets the default) |
| Lint | `cd apps/mobile && ./gradlew :androidApp:lintDebug` |
| Shared tests | `bun run test:android` (`:shared:testAndroidHostTest`) |
| iOS compatibility of shared code | `cd apps/mobile && ./gradlew :shared:compileKotlinIosSimulatorArm64` |
| Device tests | `just test-device [serial]` (`:androidApp:connectedDebugAndroidTest`) |
| Crash log | `just crashlog <serial>` |
| Mirror a tablet | `just mirror` (scrcpy) |
| Docs site | `just docs`, `just build-docs`, `just deploy-docs` |
| Signed release bundle | `just aab` |
| Play uploads | `just play-internal`, `just play-beta`, `just play-production`, `just play-promote`, `just play-metadata` |

With several devices connected, pass the serial (`adb -s <serial> …`).

## Architecture standards

### Kotlin Multiplatform
1. **Platform-free logic goes in `shared/commonMain`**: models, validation, sorting, search, text and geometry helpers. `commonMain` must not import `android.*` or `java.*`.
2. **Platform services sit behind small interfaces** (or `expect`/`actual`). Never pass `Context` or Android types through common APIs.
3. **Keep the iOS targets compiling.** Run `:shared:compileKotlinIosSimulatorArm64` after any change in `shared`.
4. The planned order for moving code to common (models and `kotlinx.serialization`, storage, files with Okio, ViewModels; ink and reader stay native behind interfaces) is in `docs/architecture/modules.md`.

### Reuse
- Search before writing a helper. Pure helpers live in `shared/commonMain/.../TextUtils.kt`; Android view helpers in `androidApp/.../ViewUtils.kt`.
- Extract code that appears three times or more. Don't add one-off wrappers.
- Constants that two places must agree on (for example the reader chrome bands) are defined once.

### Lifetimes and memory
- ViewModels hold the `Application`, never an Activity, View or Compose state holder.
- Anything that registers a listener unregisters it (`onDestroy`, `onCleared`, `onDetachedFromWindow`).
- Views cancel their coroutine scopes when detached and recreate them when re-attached.
- Native handles (Pdfium documents, `ParcelFileDescriptor`, `MediaRecorder`, Readium publications, cursors) are closed by their owner, with `use {}` where possible.
- Bitmaps are bounded (PDF renders are capped at 2560×4096) and recycled by their owner.

### Threading and performance
- Main thread: layout, drawing and input only. Storage, JSON, PDF rendering, OCR and file I/O run on workers or `Dispatchers.IO`.
- Rethrow `CancellationException`; never swallow it in a generic `catch`.
- Judge performance on a release build (`just install-release`). Debug builds on ART are several times slower.
- Stroke points are stored with 5 decimals (`NotebookStorage.compact`). Keep it: full float precision doubled page size and made startup parsing slow.
- Known hot spots: all pages load at startup, `NotebookEditor` is one large composable, `InkCanvasView.onDraw` allocates per frame, and thumbnails aren't cached. Details are in `modules.md`.

### UI
- Follow the brand kit: sage `#2F7A68`, ink `#1E2B28`, cream `#F6F1E6`, Geist (`FolioBrand`, `folioTypography()`). The editor keeps its fixed light colours.
- Support phones (~360–411 dp) and tablets. Touch targets are at least 48 dp. Dialogs and sheets scroll, and long text ellipsizes.
- Notebook editors share the compact tab strip with the reader. **More → Show recent note tabs** saves visibility in `editor` preferences. Tabs show the current note plus up to 11 recent notebooks/canvases; closing tabs never deletes notes, and switching flushes ink. Tabs keep their position when selected (`stableDocumentTabOrder`, saved as `noteTabOrder`); new tabs go to the end. The "N ▾" button opens a drop-down with Hide tab bar, Close other tabs and per-tab options. The common `recentDocumentTabs` helper filters Trash/closed items.
- Dialog, selection and search state survives rotation (`rememberSaveable`).
- Notebook pages are added without the Page+ sheet: on the last page the top-bar next arrow becomes a **+** (`action-add-page`); in Single Page layout the page follows a sideways drag (`onPageDrag`; one finger with finger drawing off, two fingers otherwise) and turns or, past the last page, reveals "Release to add page"; pulling up past the last page and holding for `PULL_TO_ADD_HOLD_MS` adds one (one finger with finger drawing off, two fingers otherwise). Writing never adds pages on its own. **+ → Import PDF/EPUB as notebook** bypasses the reader: each PDF page becomes a notebook page with the page as a picture; an EPUB is converted to typed-text pages (`NotebookFiles.importEpubPages`, shared `epubParagraphs`/`paginateChapterLines`; chapters start a new page, DRM books are rejected, at most 600 pages). The page manager selects several pages (**Select** or long-press) and deletes them after a confirmation; a notebook always keeps at least one page.
- The folder editor offers **Mosaic** (`NoteFolder.style = "mosaic"`), saved per folder. It shows up to four live notebook, canvas or book thumbnails matching the active tab; All mixes these types. Empty folders retain folder artwork.
- Reader Contents preserves PDF/EPUB chapter hierarchy with expandable groups and a page fallback for PDFs without outlines. Compact open-book tabs include a pinned scrollable-list picker; tabs and gesture switches are saved in `reader-preferences`; closing a tab never deletes content.
- The PDF reader's drawing bar (`ReaderDrawingToolbar`) reuses the notebook pens: `rememberBrushPresetsState` over the `editor` preferences, `BrushPresetBar` and `BrushSettings` (no page colour picker). Hand keeps the bar open and pans (tool `Read`); picking a pen returns to `Pen`. The reader's last pen is `reader-selected-brush` in `reader-preferences`. Brush ink adds `brush`, `engine`, `variant`, `texture`, `grain`, `angle`, `sensitivity` and a `pressures` array to the ink anchor; legacy anchors without `brush` render as before. Annotated PDF export flattens brush ink to plain Ink annotations.
- Reader top and bottom action bars use the compact `ReaderAction` (50 dp tall, 22 dp icon over a 10.5 sp label). It has an `InkIcon` overload and a Lucide `ImageVector` overload (`ReaderAction(Lucide.FileText, "Doc") {}`); use the Lucide one for reader controls without a custom Folio glyph.
- Reader selection toolbars follow projected PDF selection bounds or the EPUB navigator selection rectangle, prefer above/below the passage, and stay inside screen margins. The toolbar is one compact row (grip, highlight, underline with a style menu, color, copy, note, a More menu and close). Drag the grip to move it (`draggedToolbarPosition`); its automatic position keeps 40 dp clear of the selection handles. The mark editor (`ReaderMarkEditor.kt`) and the selection toolbar share one colour palette, `ReaderColors` (11 swatches plus a custom picker built on `BrushColorPicker`); the first four colours are the original palette, so saved marks keep their swatch. A finished pen stroke stays visible as a pending preview (`PendingInk` in `PdfPageReaderView`) until its saved copy is projected, so lifting the pen never flashes. A long press never opens the mark editor: it selects the word, and the toolbar shows an **Edit highlight** (palette) button when the selection overlaps a saved mark. Selection handles are a stem plus a 13 dp dot (40 dp grab radius, `handleDrop`/`handleRadius` in `PdfPageReaderView`). A long press selects the pressed word (the handles extend it), not the whole line. With **Stylus for writing** on, a stylus that stays still for the long-press time selects the word too (the pen dot is dropped; `gestureToolOverride` in `PdfPageReaderView`); moving the stylus still writes. The stylus hold is 300 ms longer than a finger long press so pauses while writing do not select. A short touch while writing is a dot, never a page tap (`onPageTap` runs only for Read/Select), and brush ink stores per-point `times` so the saved stroke replays exactly like the live one.
- Reader Contrast offers saved PDF display adjustments (embolden, sharpen, watermark bleaching, dark color enhancement and smoothing); process pixel filters on workers, preserve originals/exports, and keep the footer limited to time, battery, chapter and position.
- Icons: generic actions use Lucide (`com.composables:icons-lucide-cmp`), drawn through `LucideGlyph` (shared) at a 1.5 stroke so they match Folio's custom ones. Library icons go through `HomeSymbol`/`Symbol`, editor and reader icons through `InkSymbol`/`InkIcon.lucide`. Folio-specific tools (pens, erasers, lasso, reader underline styles, split view, page fitting, brush glyphs) keep their custom paths.
- The About screen (`AboutScreen` in `shared/.../home/HomeScreens.kt`) shows the version, a short privacy summary, links (documentation site, GitHub star, issues, privacy policy, license; addresses in `FolioLinks.kt`) and the open-source libraries. Keep its privacy text in step with the privacy policy.
- Every icon-only control has a `contentDescription`. Keep existing test tags stable; flows and tests depend on them.

## Data invariants

- Databases: `knotes.db` (notebooks, pages, settings; notebook JSON version 2) and `knotes-reader.db` (reader, WAL). **Never rename them**; they're private and renaming a live database risks the library.
- Stroke engines are versioned (`jetpack-ink-v1`, `jetpack-ink-v2`, `shape-v1`, `fill-cut-v1`, …). Old ink must render unchanged; add a new version rather than changing an old one.
- `.folio` archives: ZIP with `manifest.json` and Zstandard-compressed JSON, format ID `application/vnd.folio.archive+zip`. Legacy `.knotes` archives and `Knotes-Backup-*.knotes` files must keep importing. JSON schemas are in `apps/docs/public/schemas`.
- Imports must reject unsafe archive paths (zip-slip) and must never erase existing data.
- Live boards live in `files/liveboards/` (`index.json`, `<id>.json` with Excalidraw's own `elements`/`appState` strings, `<id>.jpg` preview). Keep Excalidraw's JSON untouched so boards stay openable in Excalidraw.
- Android cloud backup stays off (`allowBackup="false"`).
- Schema changes follow `docs/architecture/sqlite-migrations.md`.

## Platform pitfalls

- **Jetpack Ink mesh crash (Android 14, MNP1095):** keep `CanvasStrokeRenderer.create(forcePathRendering = true)` in `JetpackInkEngine.kt` when upgrading Ink. See `investigations/mnp1095-mesh-crash.md`.
- **R8:** release builds are minified. Keep rules for JNI and reflection libraries (Pdfium, zstd, Ink, PDFBox, Readium, ML Kit) are in `androidApp/proguard-rules.pro`. After adding or upgrading a library, test the release build: OCR silently broke until ML Kit's rules were added.
- **Analytics rules:** log through `AppAnalytics` only. Event parameters are short identifiers (`kind=pdf`), never user text: no note or book content, titles, file names or search text. The advertising ID and Privacy Sandbox ad permissions are removed in the manifest; keep it that way. Any new kind of collected data needs the privacy policy and the Play Data safety form updated first.
- **Two apps on one device:** debug = `com.allient.folio.dev` (`applicationIdSuffix`, name in `src/debug/res`), release = `com.allient.folio`. Different IDs mean different signing keys never clash. Dev builds never use Firebase (no Analytics or Crashlytics), whatever `google-services.json` contains. Code that needs the ID uses `context.packageName` / `${applicationId}`, never a hard-coded string.
- **Firebase config:** `androidApp/google-services.json` comes from the Firebase console and is git-ignored. The Google Services and Crashlytics plugins are applied only when it exists, so builds without it work and Firebase stays inactive. Analytics and Crashlytics share one user switch (`AppAnalytics`); report handled errors with `AppAnalytics.recordError(error, where)` (no user text).
- **Website analytics:** Cloudflare Web Analytics (cookieless), enabled when `PUBLIC_CF_BEACON_TOKEN` is set at build time (repository variable or secret `CF_BEACON_TOKEN`; the bare token or the whole snippet both work).
- **Permissions:** after adding a dependency, check the merged release manifest (`build/intermediates/merged_manifest/release/`) for new permissions or exported components.
- **Stale Gradle outputs** from the project's old folder name can break dex merging. `./gradlew clean` fixes it.
- **Live boards (WebView + Excalidraw):** give the WebView `MATCH_PARENT` (Compose's default `WRAP_CONTENT` collapses the page); keep `currentItemStrokeVariability = "variable"` or Excalidraw ignores pen pressure; every pen down sent to the page needs an up/cancel or later strokes become pinch-zoom; capture previews with `PixelCopy` (`View.draw` can't see the GPU canvas). Details in `docs/features/live-boards.md`.

## Testing

Before you finish a change:
1. `./gradlew :androidApp:assembleDebug :androidApp:lintDebug`. Lint must have 0 errors.
2. `./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64` if you touched `shared`.
3. The relevant device tests (`InkCanvasViewTest`, `NotebookStorageTest`, `NotebookFilesTest`, `LocalBackupTest`, `ReaderTest`, `NaturalBrushTest`, `TrashTest`). They keep app data on the device (`leaveApksInstalledAfterRun`).
4. For visible changes, check the screen on a tablet and at phone width. For releases, smoke-test the R8 build: library, editor, PDF with search and OCR, EPUB, export.

Known: 5 `InkCanvasViewTest` cases (selection drag, sparse shape erase, fill-cut) fail on `main` since the selection-bar and shape changes and need updating.

## Releases (Google Play)

Builds and uploads run on the maintainer's machine with fastlane. **Don't add app build or release workflows to GitHub Actions.**

- Store listing (`en-US`, `es-419`), icon, feature graphic and screenshots: `apps/mobile/fastlane/metadata/android/`.
- Signing: `apps/mobile/keystore.properties` (git-ignored) or the `FOLIO_UPLOAD_*` environment variables. Never commit keystores, passwords or `play-service-account.json`.
- Version code: highest code on any Play track + 1 (computed by fastlane). Version name: `FOLIO_VERSION_NAME`.
- In-app updates: flexible by default; immediate for update priority 4–5 or versions more than 30 days old (`UPDATE_PRIORITY=4 just play-production`).
- First-time setup (Play Console forms, data safety, service account, first manual upload): `docs/publishing/google-play.md`.
- Builds from before 2026-10-19 used `com.example.knotes` and install as a separate app.

## Website and CI

- `apps/docs` is deployed to Cloudflare Workers Static Assets (`apps/docs/wrangler.jsonc`, one environment, observability on) by `.github/workflows/docs.yml` on pushes to `main`. Pull requests only build. Each run posts to Slack `#eventos`.
- Repository secrets: `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `SLACK_WEBHOOK_URL`.
- **SEO and agent discovery:** shared facts (keywords, FAQ, crawler policy) live in `apps/docs/src/site.ts`. The build writes `robots.txt` (with Content Signals and AI crawler groups), `sitemap.xml`, `/.well-known/ai-catalog.json`, and, in `scripts/agent-files.mjs`, a Markdown copy of every page, `llms.txt`, `llms-full.txt` and the agent skills index (with SHA-256 digests). `worker/index.ts` runs in front of the assets: it returns the Markdown for `Accept: text/markdown`, adds Link headers and allows cross-origin reads of the agent files. `public/webmcp.js` registers WebMCP tools. Give every doc a `description` in its frontmatter (it becomes the meta description and the llms.txt line). Agent skills go in `public/.well-known/agent-skills/<name>/SKILL.md`.
- The privacy policy at `/privacy/` (`src/pages/privacy.astro`) is linked from the Play listing. Update it whenever permissions or data handling change.
- Landing page layout primitives live in `apps/docs/src/components/ui` (`Box`, `Stack`, `Grid`, `Container`, `Tablet`); design tokens in `src/styles/tokens.css`.

## Documentation index

Sources: [`docs/`](docs). Each file needs a `title` in its frontmatter; the website builds them under the same paths.

| Document | Topic |
|---|---|
| `architecture/modules.md` | Module rules, performance notes, iOS port order |
| `architecture/local-storage.md` | On-device storage, Trash, backup and import limits |
| `architecture/portable-format.md` | `.folio` archive format (and legacy `.knotes`) |
| `architecture/sqlite-migrations.md` | Database migration rules |
| `architecture/reader.md` | PDF and EPUB reader |
| `architecture/jetpack-ink-implementation.md` | Jetpack Ink integration |
| `architecture/ink-rendering-research.md` | Rendering performance research |
| `investigations/mnp1095-mesh-crash.md` | Android 14 mesh crash: analysis and fix |
| `features/pages-and-import.md` | Swipe to turn or add pages, page manager, PDF/EPUB as a notebook |
| `features/reader-writing-and-marks.md` | Reader pens, selection toolbar, word and stylus selection, mark editor |
| `features/live-boards.md` | Live boards: Excalidraw with Folio's pen filter, storage, pitfalls, limits |
| `features/*.md` | Feature reviews (BOOX coverage, canvas tools, ruler and zoom window, tables and stickers) |
| `roadmap/*.md` | Planned preferences (themes, languages), responsive improvements |
| `testing/validation.md`, `testing/mirroring.md` | Test results, UI flow replays, device mirroring |
| `publishing/google-play.md` | Play Store setup and release process |
| `publishing/play-console-answers.md` | Copy-ready listing text and every Play Console form answer |
| `brand.md` | Brand kit and where the app uses it |

## Conventions

- Kotlin official code style. Match the surrounding code: naming, comment density and idioms.
- Commit messages: one short sentence in the imperative or descriptive style used in `git log` (for example "Table bar next to the table"). No prefixes.
- Keep docs in step with code. If behaviour, a command or a data format changes, update this file, the relevant docs page and the README if users would notice.
- Never commit secrets, signing material, device backups (`artifacts/recovery`) or screenshots of other apps.

### Reader Format preferences

PDF display preferences live in `reader-preferences` SharedPreferences. Confirm marquee
crops before saving; Cancel must preserve the prior crop. Crops transform the reading view
only; keep annotations in original normalized page coordinates and exports unchanged.
An applied crop hides Fit Screen/Fit Width until Reset; derive this from the persisted crop.

Split View uses `ReaderSplitPane.kt` for an independently owned secondary PDF/EPUB session.
Close its native session and remove its EPUB fragment on disposal. Horizontal means side-by-side;
Vertical means stacked. Reader landscape locks and split layout persist in `reader-preferences`.

ReaderActivity follows display orientation on API 36+ devices; do not add restricted-
resizability compatibility properties, which letterbox the reader. Landscape locks remain
available on Android 15 and earlier.

PDF Read mode supports hold-then-drag text selection and draggable range handles. Keep its selection toolbar non-modal so handles remain reachable. PDF underline variants live in the optional anchor JSON `underlineStyle` field; missing values mean solid.

Reader Normal mode has neither fit control selected. Fit Screen and Fit Width detect the
visible PDF content on a worker and trim blank margins (including scanned pages). Fit Screen
fits that content within the viewport; Fit Width fills its width. Reset clears fitting and
manual crop and returns to Normal. Reset Zoom clears zoom while retaining crop/fit settings.
