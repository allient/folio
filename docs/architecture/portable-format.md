---
title: "Folio portable format"
---

`.folio` is a ZIP archive using the standard ZIP container and the `.folio` extension. Its manifest format ID is `application/vnd.folio.archive+zip`. The package deliberately keeps portable data separate from SQLite's internal schema.

## Version 1 archive

Every new archive contains `manifest.json` validated by `apps/docs/public/schemas/folio-archive-manifest-v1.schema.json`.

- Notebook archive: `notebook.json.zst` plus `assets/`.
- Library archive: `library.json.zst`, `notebooks/<notebook-id>/notebook.json.zst`, and `assets/`.
- After Zstandard decompression, notebook JSON uses the version 2 model described by `apps/docs/public/schemas/folio-notebook-v2.schema.json`.
- Library index uses the version 1 schema in `apps/docs/public/schemas/folio-library-v1.schema.json` and preserves folders and the template selection list.
- Asset paths inside JSON are relative archive paths. They never contain device-private absolute paths.
- The importer continues to accept legacy ZIP backups that contain only `notebook.json` and `assets/`.

JSON is compressed with Zstandard level 3 inside standard ZIP entries. Zstandard is specified in [RFC 8878](https://www.rfc-editor.org/rfc/rfc8878.html), and the ZIP container remains easy to list/extract with common desktop tools; reading notebook data requires Zstandard decompression before JSON parsing. Known already-compressed media (for example JPEG, PNG, WebP, MP3, MP4, and PDF) is stored without recompressing it. Existing legacy ZIP archives with plain `notebook.json` and `assets/` remain importable. The package is not encrypted.

The JSON schemas document the current portable contract. Format changes must add a new schema/version and an explicit import migration; do not reuse SQLite's schema version as a portable-format version. The importer tolerates additional JSON properties, but this version does not preserve unknown properties when importing and re-exporting.

The app can import either a single-notebook archive or a full-library archive. Importing a library backup merges it as fresh copies with new notebook, page, stroke, text, image, attachment, and folder IDs; it keeps the existing library intact.

## Object transforms and stacking order

Images, text boxes and blocks (tables, sticky notes) may carry `rotation` (degrees), `flipX`, `flipY` and `z`. Within a layer they are drawn from low to high `z`; ink is drawn at the fixed level 2, so objects with `z < 2` sit under the writing and objects with `z >= 2` cover it. Missing values keep the original order: images 0, blocks 1, text 3. Older readers ignore these properties.

## Legacy `.knotes` archives

Before the rename, archives were written as `.knotes` with the format ID `application/vnd.knotes.archive+zip` and schema IDs `urn:knotes:schema:…`. The content is identical. Folio imports them through the same path (`FolioFormat.archiveFormats` accepts both IDs), and the smart backup folder still lists and prunes `Knotes-Backup-*.knotes` files. Their schemas are kept in `apps/docs/public/schemas/legacy/`.

## Vector shapes and erasing

The existing notebook v2 `engine`, `filled` and `points` properties also represent drawing-tool output. `shape-v1` uses a constant-width vector outline, optionally filled. For 3D solids (cube, cuboid, pyramid, prism, cone, cylinder, frustum, sphere, hemisphere) each point's `pressure` is a code for the segment that reaches it: `1` visible edge, `0.5` hidden edge drawn dashed, `0` pen lift, `0.25` a silhouette point that only shapes the fill. Other shapes keep `1` everywhere. `fill-cut-v1` represents the remaining solid area after vector subtraction: closed contours retain their winding and are connected with zero-area bridges. It is rendered as fill only, keeping erased holes visible in the editor, thumbnails, PNG and PDF. These identifiers add rendering semantics to the existing extensible engine field; no SQLite migration or additional JSON property is needed.

Current Folio preserves those vectors through backup/import and selection transformations. Older builds may not reproduce the appearance of new engine identifiers exactly. PDF and PNG are the interoperable visual exports for other applications; `.folio` preserves editable Folio data.

## Security TODO

- [ ] Add optional client-side encryption for exported backups and future sync. Define key derivation, recovery, wrong-password handling, and metadata leakage before implementation. Current `.knotes` exports are unencrypted ZIP packages.

