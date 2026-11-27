---
title: "Planned preferences (languages done)"
description: "Preferences planned for Folio but not built yet, and why they wait; app languages are now built."
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

## Plan

1. **Theme tokens.** Add a `KnotesTheme` with named tokens (surface, panel, ink, line, muted, accent, on-accent, selection) and provide it through a CompositionLocal. Replace hard-coded editor and reader colours with tokens. No visual change in Light.
2. **Light, Dark, System.** Add a Dark token set and follow the system setting by default. Add OLED as Dark with pure black surfaces.
3. **Paper stays white by default.** Ink colours, templates and exports assume white paper, so dark mode darkens only the chrome around the page. A separate "Dark paper" option can come later; it needs ink colour remapping for display without changing saved strokes or exports.
4. **Colour mode, style, contrast, surface colour.** Generate tokens from the accent pair, style and contrast. Keep presets as named token sets so they stay testable.
5. **Tests.** Screenshot comparisons of the home, editor toolbar, brush panel, pressure curves and reader in Light, Dark and OLED.

## Multi-language (done)

Shipped: English (default), Spanish and Brazilian Portuguese, following the device language with English as the fallback, and Preferences → Language (also Android 13's per-app language setting). How it works and how to add a language: the "Languages" note in `AGENTS.md`. Still open: a pseudo-locale (`en-XA`) UI pass and a native-speaker review of the translations.

## Constraints

- Saved notebooks, strokes and exports never change with the theme.
- Every new option must apply to the whole app before it appears in Preferences.
- Preferences are stored on the device only, like the rest of the app's settings.
