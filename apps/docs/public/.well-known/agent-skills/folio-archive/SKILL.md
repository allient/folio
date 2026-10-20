---
name: folio-archive
description: Open, inspect, convert or create Folio .folio notebook archives (ZIP with Zstandard-compressed JSON). Use when a user shares a .folio or legacy .knotes file, or asks to extract, search, summarize or generate Folio notebooks.
---

# Folio archives (.folio)

[Folio](https://folio.allient.io) is an Android note-taking app. It exports notebooks and full-library backups as `.folio` files. The format is open and documented.

## Container

- A standard **ZIP** file. Media type / format ID: `application/vnd.folio.archive+zip`.
- `manifest.json` at the root, valid against <https://folio.allient.io/schemas/folio-archive-manifest-v1.schema.json>.
- **Notebook archive:** `notebook.json.zst` and `assets/`.
- **Library archive:** `library.json.zst`, `notebooks/<notebook-id>/notebook.json.zst` and `assets/`.
- JSON entries are **Zstandard** compressed (level 3, RFC 8878). Decompress before parsing. Images, audio and PDFs in `assets/` are stored as-is.
- Asset paths inside JSON are relative archive paths.
- Not encrypted.
- Legacy: `.knotes` archives (format ID `application/vnd.knotes.archive+zip`) have the same content. Very old backups may contain a plain `notebook.json` instead of `.zst`.

## Reading a notebook

```python
import json, zipfile, zstandard  # pip install zstandard

with zipfile.ZipFile("My notes.folio") as z:
    manifest = json.loads(z.read("manifest.json"))
    raw = z.read("notebook.json.zst") if "notebook.json.zst" in z.namelist() else None
    notebook = json.loads(zstandard.ZstdDecompressor().decompress(raw, max_output_size=1 << 30)) if raw else json.loads(z.read("notebook.json"))

for page in notebook["pages"]:
    typed = [t["text"] for t in page.get("texts", [])]
    print(page["id"], len(page.get("strokes", [])), "strokes", typed)
```

The notebook JSON (version 2) follows <https://folio.allient.io/schemas/folio-notebook-v2.schema.json>. Required: `version` (2), `id`, `title`, `updatedAt`, `pages`. The library index follows <https://folio.allient.io/schemas/folio-library-v1.schema.json>.

## What the data means

- **Strokes** are handwriting: `points` with `x`, `y` normalized to the page (0–1), `pressure` and `elapsedMillis`. `brush`, `color` (ARGB int), `width` and `engine` describe the look. Handwriting is not text; to get text from it, run handwriting recognition on the points (for example ML Kit Digital Ink) or OCR on a rendered page.
- **Texts** are typed text boxes (`text`, `x`, `y`, `size`, `color`). Use them for search and summaries.
- `engine` values are versioned (`jetpack-ink-v1`, `jetpack-ink-v2`, `shape-v1`, `fill-cut-v1`). For `shape-v1` 3D solids, `pressure` codes edges (1 visible, 0.5 hidden, 0 pen lift).
- Objects carry optional `rotation`, `flipX`, `flipY` and `z`; ink is drawn at level 2.

## Writing an archive

1. Build JSON that validates against the schemas. Use new UUIDs for every ID.
2. Compress each JSON document with Zstandard and add it as a ZIP entry; add `manifest.json` uncompressed.
3. Put media under `assets/` and reference it by relative path.
4. Folio imports archives as **new copies**; importing never overwrites existing notebooks.

## Rules

- Tolerate unknown properties; Folio adds optional fields over time.
- Never extract entries whose paths escape the target folder (zip-slip).
- Format reference: <https://folio.allient.io/architecture/portable-format/>.
