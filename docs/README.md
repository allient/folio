# Folio developer documentation

These Markdown files are the project's developer documentation. They read well on GitHub and are also built into the website (`just docs`, then open http://localhost:4321). Start with [AGENTS.md](../AGENTS.md) for the engineering standards.

## Publishing

- [Publish on Google Play](publishing/google-play.md): first release and routine updates, step by step

## Architecture

- [Modules and the iOS path](architecture/modules.md): Kotlin Multiplatform layout, rules, performance notes, iOS port order
- [Local storage](architecture/local-storage.md): on-device storage, Trash, backups and import limits
- [Portable format](architecture/portable-format.md): the `.folio` archive (and legacy `.knotes`)
- [SQLite migrations](architecture/sqlite-migrations.md): database migration rules
- [Reader](architecture/reader.md): PDF and EPUB reader
- [Jetpack Ink implementation](architecture/jetpack-ink-implementation.md) and [ink rendering research](architecture/ink-rendering-research.md)

## Features

- [BOOX feature coverage](features/boox-feature-coverage.md)
- [Canvas tools review](features/canvas-tools-review.md)
- [Ruler and zoom window](features/ruler-and-zoom-window.md)
- [Tables, stickers and sticky notes](features/tables-stickers-notes.md)

## Investigations

- [MNP1095 mesh crash](investigations/mnp1095-mesh-crash.md): Android 14 rendering crash, analysis and fix

## Roadmap

- [Planned preferences](roadmap/planned-preferences.md): themes, colour options, languages
- [Responsive improvements](roadmap/responsive-improvements.md)

## Testing

- [Validation](testing/validation.md): test results and UI flow replays
- [Mirroring](testing/mirroring.md): controlling a tablet from the Mac

## Brand

- [Brand](brand.md): logo system, colours, type and where the app uses them

## Adding a page

Create a `.md` file in the right folder with a `title` (and optionally `description`) in its frontmatter:

```md
---
title: My topic
description: One sentence about it.
---
```

The website builds it automatically under the same path. Add it to this index.
