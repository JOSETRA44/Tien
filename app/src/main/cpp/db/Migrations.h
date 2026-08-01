#pragma once

#include <array>
#include <cstddef>

// ═══════════════════════════════════════════════════════════════════════════
//  Tien — Versioned schema migrations
//
//  The database records its schema version in `PRAGMA user_version`. On open,
//  DatabaseManager runs every migration whose index is >= the stored version,
//  inside a single transaction, then bumps user_version to kSchemaVersion.
//
//  RULES
//    1. Migrations are append-only. Never edit a shipped migration — a device
//       that already ran it will not run it again, so an edit silently
//       produces two different schemas in the wild.
//    2. To change the schema, append a new entry and bump kSchemaVersion.
//    3. Every statement must be safe to run on a fresh, empty database.
// ═══════════════════════════════════════════════════════════════════════════

namespace tien::db {

// ── v1 — original shape (also the baseline for legacy installs) ─────────────
// Legacy databases were created without a user_version, so they read back as
// 0 and replay this migration. CREATE TABLE IF NOT EXISTS makes that a no-op
// for them while still bootstrapping a brand-new file.
constexpr const char* kMigration_001 = R"SQL(
    CREATE TABLE IF NOT EXISTS notes (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        title      TEXT    NOT NULL,
        content    TEXT    NOT NULL DEFAULT '',
        timestamp  INTEGER NOT NULL DEFAULT (strftime('%s','now'))
    );

    CREATE TABLE IF NOT EXISTS tasks (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        title       TEXT    NOT NULL,
        details     TEXT    NOT NULL DEFAULT '',
        due_at      INTEGER NOT NULL,
        created_at  INTEGER NOT NULL DEFAULT (strftime('%s','now')),
        priority    INTEGER NOT NULL DEFAULT 1,
        is_done     INTEGER NOT NULL DEFAULT 0
    );
)SQL";

// ── v2 — split timestamps, add pinning, add covering indices ────────────────
// `notes.timestamp` conflated "created" and "updated". Rebuilding the table is
// the portable way to reshape it (ALTER TABLE ... DROP COLUMN needs SQLite
// 3.35+, and ADD COLUMN cannot take a non-constant default).
constexpr const char* kMigration_002 = R"SQL(
    CREATE TABLE notes_v2 (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        title       TEXT    NOT NULL,
        content     TEXT    NOT NULL DEFAULT '',
        created_at  INTEGER NOT NULL,
        updated_at  INTEGER NOT NULL,
        pinned      INTEGER NOT NULL DEFAULT 0 CHECK (pinned IN (0, 1))
    );

    INSERT INTO notes_v2 (id, title, content, created_at, updated_at, pinned)
        SELECT id, title, content, timestamp, timestamp, 0 FROM notes;

    DROP TABLE notes;
    ALTER TABLE notes_v2 RENAME TO notes;

    CREATE TABLE tasks_v2 (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        title       TEXT    NOT NULL,
        details     TEXT    NOT NULL DEFAULT '',
        due_at      INTEGER NOT NULL,
        created_at  INTEGER NOT NULL,
        updated_at  INTEGER NOT NULL,
        priority    INTEGER NOT NULL DEFAULT 1 CHECK (priority BETWEEN 0 AND 2),
        is_done     INTEGER NOT NULL DEFAULT 0 CHECK (is_done IN (0, 1))
    );

    INSERT INTO tasks_v2 (id, title, details, due_at, created_at, updated_at, priority, is_done)
        SELECT id, title, details, due_at, created_at, created_at,
               MAX(0, MIN(2, priority)), CASE WHEN is_done <> 0 THEN 1 ELSE 0 END
        FROM tasks;

    DROP TABLE tasks;
    ALTER TABLE tasks_v2 RENAME TO tasks;

    -- Indices backing the exact ORDER BY / WHERE clauses issued by
    -- DatabaseManager. Without these every list query is a full table scan.
    CREATE INDEX IF NOT EXISTS idx_notes_pinned_updated
        ON notes (pinned DESC, updated_at DESC);
    CREATE INDEX IF NOT EXISTS idx_notes_title
        ON notes (title COLLATE NOCASE);
    CREATE INDEX IF NOT EXISTS idx_tasks_due
        ON tasks (due_at);
    CREATE INDEX IF NOT EXISTS idx_tasks_done_due
        ON tasks (is_done, due_at);
)SQL";

// Append-only registry. Index i holds the migration that takes the schema
// from version i to version i+1.
constexpr std::array<const char*, 2> kMigrations{kMigration_001, kMigration_002};

// Current schema version — must equal kMigrations.size().
constexpr int kSchemaVersion = static_cast<int>(kMigrations.size());

}  // namespace tien::db
