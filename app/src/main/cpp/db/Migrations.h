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

// ── v3 — the board: papers pinned on a wall ────────────────────────────────
// A board is an unbounded 2D surface. Each note carries its own position,
// rotation and stacking order, because those are properties of *this* piece of
// paper on *this* wall, not of the idea written on it.
//
// `rotation` is persisted rather than randomised at render time: a paper that
// re-tilts itself every time the screen redraws is unmistakably digital. Pinned
// once, it stays where it was put.
constexpr const char* kMigration_003 = R"SQL(
    CREATE TABLE IF NOT EXISTS boards (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        name        TEXT    NOT NULL,
        created_at  INTEGER NOT NULL,
        updated_at  INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS board_notes (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        board_id    INTEGER NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
        text        TEXT    NOT NULL DEFAULT '',

        -- Board coordinates, in density-independent pixels. REAL rather than
        -- INTEGER so a paper keeps its exact spot across zoom levels instead of
        -- snapping to a whole pixel every time it is dragged.
        x           REAL    NOT NULL DEFAULT 0,
        y           REAL    NOT NULL DEFAULT 0,
        width       REAL    NOT NULL DEFAULT 180,
        height      REAL    NOT NULL DEFAULT 180,

        -- Degrees. Small and persisted: this is the tilt it was pinned at.
        rotation    REAL    NOT NULL DEFAULT 0,

        color_index INTEGER NOT NULL DEFAULT 0,

        -- Stacking order. Picking a paper up raises it, like lifting one off a
        -- pile, so the most recently touched note is never buried.
        z           INTEGER NOT NULL DEFAULT 0,

        -- Optional link back to a note in the notes table, so an existing note
        -- can be pinned to the wall without duplicating its text.
        source_note_id INTEGER REFERENCES notes(id) ON DELETE SET NULL,

        created_at  INTEGER NOT NULL,
        updated_at  INTEGER NOT NULL
    );

    -- The thread between two papers. Undirected in meaning, but stored with a
    -- from/to pair so the renderer has a stable order to draw the curve in.
    CREATE TABLE IF NOT EXISTS board_links (
        id           INTEGER PRIMARY KEY AUTOINCREMENT,
        board_id     INTEGER NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
        from_note_id INTEGER NOT NULL REFERENCES board_notes(id) ON DELETE CASCADE,
        to_note_id   INTEGER NOT NULL REFERENCES board_notes(id) ON DELETE CASCADE,
        created_at   INTEGER NOT NULL,

        -- A thread from a paper to itself is meaningless, and pinning the same
        -- pair twice would just draw the same curve on top of itself.
        CHECK (from_note_id <> to_note_id),
        UNIQUE (from_note_id, to_note_id)
    );

    -- Every query is "give me this board's contents", so board_id leads.
    CREATE INDEX IF NOT EXISTS idx_board_notes_board ON board_notes (board_id, z);
    CREATE INDEX IF NOT EXISTS idx_board_links_board ON board_links (board_id);
    CREATE INDEX IF NOT EXISTS idx_board_links_from  ON board_links (from_note_id);
    CREATE INDEX IF NOT EXISTS idx_board_links_to    ON board_links (to_note_id);

    -- The app always has somewhere to pin things. Without this the first launch
    -- would open an empty screen with no board to put anything on.
    INSERT INTO boards (id, name, created_at, updated_at)
        SELECT 1, 'Mi pizarra',
               CAST(strftime('%s','now') AS INTEGER),
               CAST(strftime('%s','now') AS INTEGER)
        WHERE NOT EXISTS (SELECT 1 FROM boards WHERE id = 1);
)SQL";

// Append-only registry. Index i holds the migration that takes the schema
// from version i to version i+1.
constexpr std::array<const char*, 3> kMigrations{kMigration_001, kMigration_002, kMigration_003};

// Current schema version — must equal kMigrations.size().
constexpr int kSchemaVersion = static_cast<int>(kMigrations.size());

}  // namespace tien::db
