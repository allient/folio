---
title: "Local storage and portable backups"
---

Folio stores notes on the Android device. It has no application account, sync service, network storage, or Internet permission. The manifest sets `android:allowBackup="false"` to disable Android's ordinary automatic app backup; manual `.folio` exports provide the portable backup workflow. Device transfer behavior can also depend on the Android version and manufacturer.

## Saved data

| Location | Contents |
| --- | --- |
| App-private database `databases/knotes.db` | Notebook metadata, ordered page records, and migration state. Each page payload contains vector strokes, layers, typed text, image references, template, and outline. Notebook metadata contains tags and attachment references. |
| App-private `files/media/` | Copied images and attachments, rendered PDF page images, imported backup assets, and microphone recordings. |
| App-private preferences `editor` | Pressure profile, threshold, curve control points, finger-writing preference, and toolbar side. |
| App-private preferences `home-preferences` | Library look and behaviour: `accent`, `corners`, `view_mode`, `sort`, `start_fullscreen` and `trash_retention_days` (7, 30, or 0 for never). Unknown values fall back to the defaults. |
| App-private `cache/exports/` | Temporary PDF, PNG, and `.folio` export files before saving or sharing. Android may clear cache files. |
| App-private `cache/pdf-import-*.pdf` | Bounded temporary copy of a selected PDF, providing the seekable descriptor required by Android's PDF renderer. Deleted after import, including failure. |
| Legacy `files/knotes-notebooks.json` | Earlier library imported once into SQLite. Migration is transactional and leaves this original file in place. The database migration marker prevents reimporting it. |

`NotebookViewModel` retains in-memory notebooks across activity recreation. One background executor loads and writes local data. Edits enqueue immutable snapshots; the save queue replaces superseded pending snapshots and commits the newest one. SQLite transactions keep note/page updates together. The editor reports saving while the latest write is pending and exposes write errors.

Completed stroke segments and other page edits trigger saves. A live stroke is not durable until its segment has been committed to the model and the queued database write has completed. Undo/redo history and the lasso clipboard remain in memory.

If loading fails or legacy data is malformed, Knotes exposes an error and blocks saves rather than replacing the library with a newly created empty one. Unreferenced media is currently retained after deleting a notebook, image, or attachment; no media garbage collector runs yet. Clearing Android app data or uninstalling the app removes app-private notes and media.

## Trash

Deleting a notebook, folder or book from the library is a soft delete. Nothing is removed from disk; the item gets a `deletedAt` time (milliseconds) and is hidden from the library, search, Recent, Favorites, folder counts, editor note links and reader study notes.

| Item | Where `deletedAt` is stored |
| --- | --- |
| Notebook | The notebook JSON payload in `knotes.db` (`notebooks.payload`), next to `createdAt`. Pages and media stay in place. |
| Folder | The folder entry in the `library` row of the `settings` table. |
| Book | The document JSON payload in `knotes-reader.db` (`documents.payload`), next to `favorite` and `createdAt`. Annotations and the original file stay in place. |

All three are fields inside existing JSON payloads, so no SQLite schema migration was needed: both databases stay at version 1. Older payloads without the fields read as "not deleted".

- **Restore** clears `deletedAt`. A notebook or book keeps its folder.
- **Folders:** a deleted folder does not take its contents with it. Its notebooks and books keep their `folderId` but show in the main library, unfiled, while the folder is in Trash; restoring the folder puts them back inside. This way deleting a folder never starts a purge timer on notes the user did not delete. Deleting the folder permanently unfiles its contents for good.
- **Delete permanently** removes only items that are already in Trash. For notebooks it removes the database rows; as before, unreferenced media files are kept (there is no media garbage collector yet). For books it removes the document and its annotations; the content-addressed original in `files/media/reader/` is kept, as before.
- **Auto-delete:** when the app starts, after both databases load, items whose `deletedAt` is older than the retention setting (default 30 days) are deleted permanently. With "Never auto-delete" nothing is purged.
- **Backups:** `.folio` library backups (manual export and smart folder backups) include items in Trash with their `deletedAt`, so a backup taken after deleting something still contains it. On restore, these items are added to Trash again, with `deletedAt` set to the restore time so they get a full retention period instead of being purged at the next start. A single-notebook import is never placed in Trash.

## Exporting and importing

### Smart backups

Smart backups (Settings → Smart backups) copy the whole library to a folder the user picks through the Storage Access Framework. No storage permission and no network are used. Implementation: `LocalBackup.kt`.

**When a backup runs.** A WorkManager one-time job, unique name `folio-local-backup-change`:

- Every queued library save (`NotebookViewModel.saveNotes`) and every reader write (`ReaderStore`: documents, positions, annotations) calls `LocalBackupScheduler.changed`. It records the first change since the last backup (`firstUnbackedChange`) and, at most once a minute, enqueues the job with policy KEEP and a 15-minute delay. A long editing session therefore gets one backup at most every 15 minutes.
- Leaving a notebook (back to home or to another notebook, observed in `MainActivity` from `openNotebookId`), returning from the reader, and the app going to the background (`ProcessLifecycleOwner` ON_STOP) call `LocalBackupScheduler.soon`. If there are unbacked changes it enqueues the same job with policy REPLACE and a 2-minute delay, which supersedes the 15-minute one.
- A periodic job every 6 hours (`folio-local-backup-periodic`) is a safety net for missed triggers. **Back up now** uses `folio-local-backup-now` and always writes a new version. The old `knotes-local-backup-daily` job is cancelled when backups are enabled.

The worker computes a SHA-256 signature of all notebooks, folders, template selections and the reader snapshot. If it matches the last successful backup (and the last run had no error), nothing is written and the pending-change marker is cleared.

**Writing safely.** Before writing, Folio estimates the archive size (notebook JSON plus each distinct media file). If free space, the smaller of app storage and the folder's volume when the provider reports it, is below max(2 × estimate, 200 MB), the backup is skipped and Settings shows an error. Existing backups are kept. Otherwise the archive is built in the app cache, then written to `Folio-Backup-yyyyMMdd-HHmmss-SSS.knotes.partial` in the folder. The copy is read back and checked: same size and SHA-256 as the source, a ZIP that reads to the end with valid CRCs and an intact end-of-central-directory record whose entry count matches, a library `manifest.json`, `library.json.zst`, and a notebook count that matches the snapshot. Only then is the file renamed to its final name. A failed or cancelled write deletes the temporary file.

**Version retention.** Pruning runs only after a new backup has been verified. It keeps:

- the 5 most recent backups;
- the newest backup of each of the last 7 calendar days (today included);
- the newest backup of each of the last 4 calendar weeks (weeks start on Monday);
- the newest backup of each of the last 6 calendar months.

Everything else is deleted, so at most 22 backups exist. There is no size cap: retention bounds the count. The newest backup is never deleted. Pruning only considers files named `Folio-Backup-<yyyyMMdd-HHmmss[-SSS]>.knotes` or the legacy `Knotes-Backup-…` form. Other files in the folder, including manual `Folio-Full-Library-…` exports, are never listed or touched. The app's own `.partial` files older than 24 hours (left by a crash) are removed.

**What a backup contains.** Notebooks, folders, template selections and notebook media (images, audio, attachments, rendered PDF page backgrounds), all from app-private storage. Reader books are metadata only: title, author, content hash, source URI, folder, position, and annotations. The `path` and `cover.path` fields are blank. PDF and EPUB originals, generated covers, thumbnails, render caches and temporary files are never included. JSON is Zstandard-compressed (level 3) and stored. Media that is already compressed (JPEG, PNG, WebP, GIF, HEIC, MP3, M4A, AAC, OGG/Opus, MP4, WebM, PDF, ZIP) is stored as is; other media is deflated. A media file used by several notebooks is stored once. The archive format is unchanged (`formatVersion` 1), so older backups stay importable.

**Restore.** Settings lists the kept versions with date and size. **Restore** imports the chosen file through the regular library import, which adds fresh copies and never erases the current library.

**Reminder.** The home banner appears when changes have gone unbacked for 3 days: the last backup or manual export is more than 3 days old (or, with no backup at all, the first unbacked change is) while changes are pending.

**Preferences** (`backup` SharedPreferences): `enabled`, `folderUri`, `folderLabel`, `generation`, `lastSuccess`, `lastFile`, `signature`, `error`, `lastExport`, `lastExportFile`, `firstUnbackedChange`.

### Notebook and library exports


Use **Share & export → Save Knotes backup** for an editable notebook copy. From Settings, choose a user-visible folder once for smart backups (above), or export a timestamped full-library archive containing notebooks, folders, template selections, and referenced notebook media. Reader backups keep titles, selected-file URI references, folder assignments, reading positions, and annotations without including PDF/EPUB originals or generated covers. After restore, reimport each original to reconnect it by content hash. Android's Storage Access Framework persists access only to the folder the user selected; Knotes does not request broad storage access. The archive is a ZIP with `manifest.json`, Zstandard-compressed JSON, and `assets/` entries for notebook media. The whole-library archive can be imported from the library's **Import notebook or document** action; restore merges fresh copies and retains the current library.

Single-notebook imports receive new notebook, page, stroke, text, image, and attachment IDs, so importing the same backup twice does not replace existing notes. Whole-library imports also generate fresh folder IDs and remap notebook and reader-book folder references. Imported assets are copied into a new private media directory and JSON references are rewritten to those copies. Legacy ZIP exports with plain `notebook.json` remain importable. This format is specific to Knotes and does not read or write BOOX `.note` files.

PDF export includes all pages; PNG export includes the current page. Both use the same renderer as the editor, including visible layers, paper template, ink, images, and typed text. Hidden layers are excluded from these visual exports but remain in editable backups. Exports use a fixed 1200 × 1920 page.

Image import copies the selected image and places it proportionally within a page. PDF import accepts 1–200 pages, renders each page into a PNG background, and allows new Knotes content above it. The source PDF's editable text, links, annotations, and vector objects are not preserved as editable document structures.

## File access and safeguards

Android's Storage Access Framework lets the user choose files to import and destinations to save. Knotes copies imported content into its own storage and does not require broad filesystem permission or permanent access to the original document. The only runtime permission in this implementation is microphone access for optional audio recording.

Sharing uses a non-exported FileProvider scoped to `files/media/` and `cache/exports/`. The receiving app gets temporary read access to the chosen file. Selecting a save destination or a share target delegates that user-selected file to Android and the chosen provider or receiving app.

Backup import rejects unexpected ZIP file entries, traversal names, duplicate media entries, and missing referenced assets. It limits entries to 1,000, notebook JSON to 32 MiB, each asset to 128 MiB, and combined extracted content to 2 GiB. Asset paths in imported JSON must refer to files supplied by the archive. Failed imports remove their staging directory. Copied attachments and PDF source files are capped at 128 MiB; failed image/PDF imports remove partially created media.

Exported files are ordinary unencrypted PDF, PNG, or `.folio` ZIP files. Private Android app storage protects the live library through the app sandbox; the application does not add a separate encryption or password layer. The app-level encryption work is tracked in `docs/portable-format.md` for a later phase.
