---
title: "Knotes portable format"
---

`.knotes` is a ZIP archive using the standard ZIP container and the `.knotes` extension. The package deliberately keeps portable data separate from SQLite's internal schema.

## Version 1 archive

Every new archive contains `manifest.json` validated by `apps/docs/public/schemas/knotes-archive-manifest-v1.schema.json`.

- Notebook archive: `notebook.json.zst` plus `assets/`.
- Library archive: `library.json.zst`, `notebooks/<notebook-id>/notebook.json.zst`, and `assets/`.
- After Zstandard decompression, notebook JSON uses the version 2 model described by `apps/docs/public/schemas/knotes-notebook-v2.schema.json`.
- Library index uses the version 1 schema in `apps/docs/public/schemas/knotes-library-v1.schema.json` and preserves folders and the template selection list.
- Asset paths inside JSON are relative archive paths. They never contain device-private absolute paths.
- The importer continues to accept legacy ZIP backups that contain only `notebook.json` and `assets/`.

JSON is compressed with Zstandard level 3 inside standard ZIP entries. Zstandard is specified in [RFC 8878](https://www.rfc-editor.org/rfc/rfc8878.html), and the ZIP container remains easy to list/extract with common desktop tools; reading notebook data requires Zstandard decompression before JSON parsing. Known already-compressed media (for example JPEG, PNG, WebP, MP3, MP4, and PDF) is stored without recompressing it. Existing legacy ZIP archives with plain `notebook.json` and `assets/` remain importable. The package is not encrypted.

The JSON schemas document the current portable contract. Format changes must add a new schema/version and an explicit import migration; do not reuse SQLite's schema version as a portable-format version. The importer tolerates additional JSON properties, but this version does not preserve unknown properties when importing and re-exporting.

The app can import either a single-notebook archive or a full-library archive. Importing a library backup merges it as fresh copies with new notebook, page, stroke, text, image, attachment, and folder IDs; it keeps the existing library intact.

## Vector shapes and erasing

The existing notebook v2 `engine`, `filled` and `points` properties also represent drawing-tool output. `shape-v1` uses a constant-width vector outline, optionally filled. `fill-cut-v1` represents the remaining solid area after vector subtraction: closed contours retain their winding and are connected with zero-area bridges. It is rendered as fill only, keeping erased holes visible in the editor, thumbnails, PNG and PDF. These identifiers add rendering semantics to the existing extensible engine field; no SQLite migration or additional JSON property is needed.

Current Knotes preserves those vectors through backup/import and selection transformations. Older Knotes builds may not reproduce the appearance of new engine identifiers exactly. PDF and PNG are the interoperable visual exports for other applications; `.knotes` preserves editable Knotes data.

## Security TODO

- [ ] Add optional client-side encryption for exported backups and future sync. Define key derivation, recovery, wrong-password handling, and metadata leakage before implementation. Current `.knotes` exports are unencrypted ZIP packages.

