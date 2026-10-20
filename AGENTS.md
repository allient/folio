# AGENTS.md

Engineering guide for Folio, for human contributors and coding agents. Read it before changing code. The user-facing overview is in [README.md](README.md); in-depth notes are Markdown files in [`docs/`](docs), also published on the docs site (`just docs`).

## Product facts

| | |
|---|---|
| App | Folio: handwritten notes, infinite canvas, PDF and EPUB reader |
| Platforms | **Android only** (phones and tablets). iOS is planned; shared code must stay iOS-compatible |
| Application ID / package | `com.allient.folio` |
| Store title | `Folio: Notes & PDF Reader` (Google Play allows 30 characters) |
| Privacy | Content never leaves the device. No account or ads. Anonymous usage statistics via Google Analytics (`AppAnalytics`), on by default with an off switch; consent first in EU/EEA, UK and CH. Only runtime permission: `RECORD_AUDIO` |
| Reference devices | Android tablet emulator (API 36, 1200×1920), XPPen MagicNotePad MNP1095 (Android 14), phone-size emulator (~411 dp wide) |
| License | Apache 2.0 |

## Repository map

Bun workspaces and Turborepo: apps in `apps/`, shared packages in `packages/`.

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
- Dialog, selection and search state survives rotation (`rememberSaveable`).
- Every icon-only control has a `contentDescription`. Keep existing test tags stable; flows and tests depend on them.

## Data invariants

- Databases: `knotes.db` (notebooks, pages, settings; notebook JSON version 2) and `knotes-reader.db` (reader, WAL). **Never rename them**; they're private and renaming a live database risks the library.
- Stroke engines are versioned (`jetpack-ink-v1`, `jetpack-ink-v2`, `shape-v1`, `fill-cut-v1`, …). Old ink must render unchanged; add a new version rather than changing an old one.
- `.folio` archives: ZIP with `manifest.json` and Zstandard-compressed JSON, format ID `application/vnd.folio.archive+zip`. Legacy `.knotes` archives and `Knotes-Backup-*.knotes` files must keep importing. JSON schemas are in `apps/docs/public/schemas`.
- Imports must reject unsafe archive paths (zip-slip) and must never erase existing data.
- Android cloud backup stays off (`allowBackup="false"`).
- Schema changes follow `docs/architecture/sqlite-migrations.md`.

## Platform pitfalls

- **Jetpack Ink mesh crash (Android 14, MNP1095):** keep `CanvasStrokeRenderer.create(forcePathRendering = true)` in `JetpackInkEngine.kt` when upgrading Ink. See `investigations/mnp1095-mesh-crash.md`.
- **R8:** release builds are minified. Keep rules for JNI and reflection libraries (Pdfium, zstd, Ink, PDFBox, Readium, ML Kit) are in `androidApp/proguard-rules.pro`. After adding or upgrading a library, test the release build: OCR silently broke until ML Kit's rules were added.
- **Analytics rules:** log through `AppAnalytics` only. Event parameters are short identifiers (`kind=pdf`), never user text: no note or book content, titles, file names or search text. The advertising ID and Privacy Sandbox ad permissions are removed in the manifest; keep it that way. Any new kind of collected data needs the privacy policy and the Play Data safety form updated first.
- **Firebase config:** `androidApp/google-services.json` comes from the Firebase console and is git-ignored. The Google Services and Crashlytics plugins are applied only when it exists, so builds without it work and Firebase stays inactive. Analytics and Crashlytics share one user switch (`AppAnalytics`); report handled errors with `AppAnalytics.recordError(error, where)` (no user text).
- **Website analytics:** Cloudflare Web Analytics (cookieless), enabled when `PUBLIC_CF_BEACON_TOKEN` is set at build time (repository variable or secret `CF_BEACON_TOKEN`; the bare token or the whole snippet both work).
- **Permissions:** after adding a dependency, check the merged release manifest (`build/intermediates/merged_manifest/release/`) for new permissions or exported components.
- **Stale Gradle outputs** from the project's old folder name can break dex merging. `./gradlew clean` fixes it.

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
