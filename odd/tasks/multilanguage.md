# Multi-language Folio (English, Spanish, Portuguese)

Objective: every user-visible string in Folio is translatable; English default, Spanish and Brazilian Portuguese shipped; the app follows the system language and falls back to English; new languages are a new `values-xx` folder.
Branch: `feature/multilanguage` (from `development`). Delivery: ask-on-risk, work-unit commits per task. Test runner: `./gradlew :androidApp:assembleDebug :androidApp:lintDebug`, `:shared:testAndroidHostTest`, `:shared:compileKotlinIosSimulatorArm64`, device tests on `XCD1101AC825907421`.

## Specs

- S1: "I want the app alolo 3 languages, english as default, spanish and portuguess" — English (`values/`), Spanish (`values-es/`), Portuguese as Brazilian Portuguese (L2: "ye brazilian"), shipped as `values-pt/` with Brazilian wording.
- S2: "get by defaukt the system one. if not avaiable get english" — with no choice made, Folio uses the system language when it is one of the shipped languages, otherwise English.
- S3: "thingkin than on future we can add mor elangauges" — adding a language means adding translation files (and one entry in the language list); no code changes per string.
- S4: "try to follow the best practices on multilanguage app" — no hardcoded UI text; plurals via plural resources; placeholders instead of concatenation; dates and numbers formatted for the active locale; IDs, preference keys, analytics names, test tags, JSON keys and file names stay untranslated; user content is never translated.
- S5: "seahc on internet best libs to implemnt thsi on thsi project" — use the platform tools the research found: Compose Multiplatform resources for `shared/commonMain`, Android `res/values*` for `androidApp`, AndroidX per-app languages (`AppCompatDelegate.setApplicationLocales`, AGP-generated locale config). No third-party i18n library.
- S6 (assumption, from the roadmap and best practice): Preferences → Language offers System default, English, Español, Português; on Android 13+ the system per-app language setting also works and stays in sync.
- S7 (assumption): error messages shown to users come from string resources, not from exception text.

## Tasks

| ID | Specs | Route | Status | Commit |
|---|---|---|---|---|
| T1 | S1, S2, S3, S5, S6 | inline | done | 5c39be2 |
| T2 | S1, S4 | delegated (parallel, worktree) | done | f8165d2 |
| T3 | S1, S4 | delegated (parallel, worktree) | done | ff05dcf |
| T7 | S1, S4 | delegated (parallel, worktree) | done | 87bd1a9 |
| T4 | S1, S4 | delegated (parallel, worktree) | done | 15aa884 |
| T5 | S4, S7 | delegated (context backstop) | done | a4d19f3, ad98f96 |
| T6 | S1–S7 | inline | done (pseudo-locale pass pending) | a6890b1, ad98f96 |

- T1: Infrastructure. `values/strings.xml` in composeResources and androidApp res (plus `-es`, `-pt`), AGP `generateLocaleConfig` + `resources.properties`, a `AppLanguage` model (shared, ordered list of supported tags), locale bridge so Compose resources follow the app locale (`Locale.setDefault` from `AppCompatDelegate.getApplicationLocales()`), Preferences → Language picker, Android 13 system setting sync. Proves the pipeline with the Preferences/About screens.
- T2: Shared home UI (`shared/commonMain`: NotebookHome, HomeScreens, BackupSettingsScreen, FolderEditorDialog, HomeBoards, HomeSidebar, HomeModels labels, template catalog names, plurals).
- T3: Notebook editor (`NotebookEditor.kt`).
- T7: Editor tools and library host (`NoteToolbox.kt`, `NotebookPanels.kt`, `BlockUi.kt`, `BrushCatalog.kt` labels, `BrushSettingsPanel.kt`, `DrawingToolsUi.kt`, other editor UI files, `MainActivity.kt` UI text and date/number formatting).
- T4: Reader and Live boards (`ReaderActivity.kt`, `ReaderChrome.kt`, `ReaderMarkEditor.kt`, `ReaderSplitPane.kt`, `LiveBoardActivity.kt`).
- T5: User-facing errors as typed errors mapped to strings (`NotebookFiles.kt`, `ReaderStore.kt`, `LocalBackup.kt`, `NotebookAudioRecorder.kt`, engines).
- T6: Pseudo-locale and overflow check, ML Kit default follows app language, docs (`AGENTS.md`, roadmap page, README), device tests.

## Log

- L1 (2026-11-26, user): "analize my project i want you to impment multilanguage in de manera ordenada. I want the app alolo 3 languages, english as default, spanish and portuguess, and get by defaukt the system one. if not avaiable get english seahc on internet best libs to implemnt thsi on thsi project  and thingkin than on future we can add mor elangauges and try to follow the best practices on multilanguage app.."
- L2 (2026-11-26, user): "ye brazilian so continue"
- L3: Map (explorer): ~1,200–1,500 hardcoded strings; only `app_name` is a resource; shared uses Compose resources 1.12.2 (`knotes.shared.generated.resources`); MainActivity is `ComponentActivity`; plurals hand-built (`HomeScreens.kt:106`, `NotebookHome.kt:227-252`); ~50 error messages reach UI via `exception.message`; roadmap `docs/roadmap/planned-preferences.md` already plans this.
- L4: Compose resources 1.12.2 pick the language from `androidx.compose.ui.text.intl.Locale.current` (Java default locale), not the Android per-app locale, so T1 must set the default locale from the app locale.
- L5: T1 verified on MNP1095 (Android 14): Preferences → Language switches the app (Spanish, Portuguese); `cmd locale set-app-locales` (system per-app setting) switches it too and the picker follows; generated locale config = en-US, es, pt. Pre-33 path not device-tested (no device below API 33).
- L6: Route for T2/T3/T4/T7: Writer trigger (a) parallelism: four independent units, disjoint files, each hundreds of strings; each in its own git worktree so Gradle builds don't collide; per-area string files (`strings_<area>.xml`) so no shared file. Glossary in each writer prompt keeps terms consistent.
- L7: Merged T2 (f8165d2, 389 keys), T3 (ff05dcf, 330), T4 (15aa884, 256), T7 (87bd1a9, 442); key parity en/es/pt verified by each writer. Follow-ups fixed in 90ca125: editor InkToolButton/DrawingChoice/ToolDialog pass English `tag` (rememberEnglishResources), template picker uses translated names, `bundle.language.enableSplit = false` (lint AppBundleLocaleChanges: Play language splits would drop in-app-chosen languages).
- L8: Verified: assembleDebug + lintDebug (0 errors), shared host tests, iOS compile, full device suite on MNP1095: 113 tests, only the 5 known InkCanvasViewTest failures.
- L9: Open for T5: backup screen hides its folder card by comparing `state.folder` with "No folder selected" (BackupSettingsScreen); host labels in LocalBackup.kt/MainActivity backup state still English. Open for T6: Portuguese rail labels may clip (InkRailButton 9 sp, 48 dp); two " · " joins remain (book details line, bulk delete message).
- L10: T5 (a4d19f3): typed user errors (`UserMessages.kt`, `Throwable.userMessage`), 74 error strings, backup "no folder" is state not text, backup failures stored as codes. Independent verifier (risk item 1, stored backup prefs): PASS, no blockers; its concern (low-space byte counts not cleared) fixed in ad98f96 with default names for new content. Device: LocalBackupTest, UserMessageTest, NotebookStorageTest pass.
- L11: T6 visual check on MNP1095 (landscape): library and editor rail in Spanish and Portuguese, no clipping ("Preencher" is the longest rail label and fits). Docs in a6890b1. Not done: `en-XA` pseudo-locale pass, phone-width check, native-speaker review, Android 12 and older (no device). Pre-existing, unrelated: in landscape full screen a page fills the view, so images look squashed compared with portrait.
