---
title: "Planned preferences and languages (not implemented)"
description: "Preferences and app languages planned for Folio but not built yet, and why they wait."
---

2026-10-18

The Preferences screen ships only options that work across the whole app. The options below come from the user's reference (XPPen Notes / Infinite Painter style preferences) and are deliberately left out until the app can support them everywhere.

## Why they are missing

The drawing editor uses hard-coded light colours (`ChromeInk`, `ChromeLine`, `ChromeMuted`, literal `Color(0xFF…)` values in `NotebookEditor.kt`, `NotebookChrome.kt`, `DrawingToolsUi.kt`, `PressureCurvesUi.kt`, `BrushColorPicker.kt` and the reader chrome). A theme switch today would only change the home screen and leave the editor light.

## Missing options

| Option | Values in reference | What it needs |
|---|---|---|
| UI theme | System, Light, Dark, OLED | Editor, reader, dialogs and pickers read colours from one theme; OLED uses pure black surfaces |
| Colour mode | System colours, Single tone, Dual tone | Theme accent model with one or two colours; System colours uses Material You dynamic colour (Android 12+) |
| Contrast | 0–100% slider | Theme tokens generated from a contrast level for text, lines and borders |
| Style | Soft, Vivid, Original, Playful, Fresh, Clean, Muted, Grayscale | Palette generation rules per style applied to the accent pair |
| Surface colour | Custom colour | Second theme colour for panels and bars, separate from the accent |
| Custom accent colour | Colour picker | Free colour choice next to the presets |
| Language | System, English, Español, … | All UI text moved to string resources, then translations (see below) |

## Plan

1. **Theme tokens.** Add a `KnotesTheme` with named tokens (surface, panel, ink, line, muted, accent, on-accent, selection) and provide it through a CompositionLocal. Replace hard-coded editor and reader colours with tokens. No visual change in Light.
2. **Light, Dark, System.** Add a Dark token set and follow the system setting by default. Add OLED as Dark with pure black surfaces.
3. **Paper stays white by default.** Ink colours, templates and exports assume white paper, so dark mode darkens only the chrome around the page. A separate "Dark paper" option can come later; it needs ink colour remapping for display without changing saved strokes or exports.
4. **Colour mode, style, contrast, surface colour.** Generate tokens from the accent pair, style and contrast. Keep presets as named token sets so they stay testable.
5. **Tests.** Screenshot comparisons of the home, editor toolbar, brush panel, pressure curves and reader in Light, Dark and OLED.

## Multi-language (pending)

The app is English only. `apps/mobile/androidApp/src/main/res/values/strings.xml` holds a single string; every other label, button, hint and dialog text is a hard-coded literal in Compose code (`apps/mobile/androidApp/.../*.kt` and `apps/mobile/shared/src/commonMain/...`).

1. **Extract strings.** Move all user-facing text into resources: Android `res/values/strings.xml` for `androidApp`, and Compose Multiplatform resources (`apps/mobile/shared/src/commonMain/composeResources/values/strings.xml`) for the shared home UI so iOS gets them too. Use plurals for counts ("3 notes", "1 pins") and placeholders for values ("%1$s · %2$.1f px").
2. **Add languages.** Start with Spanish (`values-es`), the user's language. Further languages are added as new `values-xx` folders without code changes.
3. **Language setting.** Preferences → Language: System (default) or a fixed language, using Android per-app language support (`AppCompatDelegate.setApplicationLocales`, `locales_config.xml`, Android 13+ system settings integration).
4. **Formats.** Dates, numbers and units follow the selected locale (`DateTimeFormatter`, `NumberFormat`) instead of fixed `Locale.US` formatting. Canvas sizes keep a dot decimal only in saved data, never in the UI.
5. **Layout checks.** Spanish and German text is often 20–35% longer: check the brush panel, toolbox, pill switcher and sidebar for clipping; prefer wrapping or scrolling over fixed widths.
6. **Tests.** Run UI tests under a pseudo-locale (`en-XA`) to catch untranslated or truncated text.

User content (notebook names, typed text, OCR results) is never translated.

## Constraints

- Saved notebooks, strokes and exports never change with the theme.
- Every new option must apply to the whole app before it appears in Preferences.
- Preferences are stored on the device only, like the rest of the app's settings.
