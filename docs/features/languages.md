---
title: Languages
description: Which languages Folio is translated into, how it picks one, the language picker, and how to add a new language.
---

Folio is translated into seven languages. It follows the device language, falls back to English, and can be set to a fixed language in **Preferences → Language**.

| Language | Picker | Resource folder |
|---|---|---|
| English (default and fallback) | 🇺🇸 English | `values` |
| Spanish (Latin American) | 🇪🇸 Español | `values-es` |
| Portuguese (Brazil) | 🇧🇷 Português (Brasil) | `values-pt` |
| French | 🇫🇷 Français | `values-fr` |
| German | 🇩🇪 Deutsch | `values-de` |
| Hindi | 🇮🇳 हिन्दी | `values-hi` |
| Simplified Chinese | 🇨🇳 简体中文 | `values-zh` |

## How Folio picks the language

- **Device language (default).** Folio uses the first of the device's preferred languages that it is translated into; if there is none, English (`AppLanguages.resolve`, shared code). A device set to French (Canada) gets French; Italian gets English. Every Chinese locale, Traditional included, gets Simplified Chinese.
- **A fixed language.** Preferences → Language lists "Device language" (phone icon) and each language in its own name with a flag. The flag is decoration only: screen readers read the name.
- **Android 13 and later:** the choice is Android's per-app language (`LocaleManager`), the same setting as Settings → Apps → Folio → Language. Changing either changes both.
- **Android 12 and earlier:** the choice is stored in the `app-language` preference and applied when each screen starts (`AppLocales.wrap`).

Dates, numbers and plurals follow the chosen language. Notebook titles, typed text, OCR results and anything else you wrote are never translated.

## Where the text lives

- Shared screens (library, Preferences, Backup settings, About): Compose Multiplatform resources in `apps/mobile/shared/src/commonMain/composeResources/values*/` (`strings.xml`, `strings_home.xml`, `strings_errors.xml`).
- Android screens (editor, tools, reader, Live boards, errors, default names): Android resources in `apps/mobile/androidApp/src/main/res/values*/` (`strings_editor.xml`, `strings_tools.xml`, `strings_reader.xml`, `strings_errors.xml`, `strings_defaults.xml`).

Rules the translations follow:

- Placeholders (`%1$s`, `%2$d`) instead of joining translated pieces; counts use `<plurals>` with the quantities each language needs (French, Spanish and Portuguese `one`/`many`/`other`, German and Hindi `one`/`other`, Chinese `other`).
- Stored values (preference values, brush names, toolbox tool ids, layer names, template and accent ids) stay English and get a translated label when shown, so changing language never changes saved data.
- Test tags stay English: components that build their tag from a label take a separate English `tag`.
- Brand and file-format names stay as they are (Folio, PDF, EPUB, OCR, Excalidraw, Google Play).

Play installs carry every language (`bundle.language.enableSplit = false` in `androidApp/build.gradle.kts`), so a language picked in Preferences always has its text.

## Adding a language

1. Copy every strings file listed above into a new `values-<tag>` folder next to it (shared and Android), and translate it. Keep the same keys and placeholders, and give `<plurals>` the quantities lint asks for.
2. Add the language to `AppLanguages.supported` (`shared/src/commonMain/kotlin/com/allient/folio/AppLanguages.kt`) with its own name and a flag, and update `AppLanguagesTest`.
3. Build with `./gradlew :androidApp:assembleDebug :androidApp:lintDebug`: lint reports missing keys, plural quantities and bad placeholders. The build generates Android's list of app languages from the `values-*` folders.

No code changes per string are needed.

## Status

All seven languages cover every screen. The Spanish, Portuguese, French, German, Hindi and Chinese texts have not yet been reviewed by native speakers; terms to check first are listed in the project log (`odd/tasks/multilanguage.md`). The Google Play listing is in English and Spanish only.
