---
title: "SQLite schema migrations"
---

Knotes uses Android `SQLiteOpenHelper` and a private local SQLite database (`knotes.db`) as its source of truth. SQLite stores notebook metadata and page JSON payloads; attachment bytes live in the app-private `files/media/` directory. This is local-first storage: edits are committed on-device without requiring an account or network.

Migration practices for this database:

1. Treat `SQLiteOpenHelper.version` as the database schema version. Increase it for every schema change; do not use the `.knotes` archive version for this.
2. Implement each upgrade as ordered, transactional SQL. Preserve the existing `onCreate` path for fresh installs and use explicit `oldVersion` steps in `onUpgrade` so users can upgrade across multiple releases.
3. Prefer additive changes first (new nullable/defaulted columns or new tables), then backfill in bounded batches where appropriate. Avoid dropping or rewriting notebook payloads unless an upgrade is unavoidable.
4. Keep migration inputs and conversions deterministic and idempotent where possible. SQLiteOpenHelper wraps upgrade callbacks in a transaction; do not start nested transactions or perform long media/file processing inside the DB upgrade.
5. Before destructive data migrations, create a recoverable database snapshot or design a staged copy-and-swap migration. Never overwrite an unreadable database with an empty library.
6. Update the schema and data access together, and verify migrations from every supported previous schema version using representative data: empty library, many pages, large page payloads, folders, and attachments.
7. Keep portable archive migrations separate. Import old `.knotes` schema versions into the current in-memory model, then persist through the current repository. SQLite migrations should not be required just to open a portable archive.
8. Keep the local transaction and serialized save queue as the durability boundary. Report write failures, preserve the last valid database, and never mark unsaved edits as durable.

Current database schema is version 1. Trash (`deletedAt`) and `createdAt` were added as optional properties inside existing JSON payloads, so they did not need a schema change. `onUpgrade` intentionally fails closed for any unknown upgrade instead of deleting user data. Add explicit migration steps when the database schema next changes; the present schema does not need a speculative migration. Device-level Android file-based encryption and app sandboxing protect private internal storage. Knotes does not currently encrypt SQLite or backup contents at the application layer.

