#pragma once

#include <string>
#include <memory>
#include <vector>
#include <cstdint>

#include "../sqlite3/sqlite3.h"
#include "../core/Course.h"

namespace tien::db {

// ── RAII Prepared-Statement Wrapper ─────────────────────────────────────────
// Guarantees sqlite3_finalize on every exit path.
// Move-only; the factory method `prepare()` is the only way to construct.
class Stmt {
public:
    // Prepare a statement against `db`. Returns nullptr on failure.
    static std::unique_ptr<Stmt> prepare(sqlite3* db, const std::string& sql);

    Stmt(Stmt&&) noexcept;
    Stmt& operator=(Stmt&&) noexcept;
    Stmt(const Stmt&)            = delete;
    Stmt& operator=(const Stmt&) = delete;

    ~Stmt();  // sqlite3_finalize

    // ── Bind ────────────────────────────────────────────────────────────
    bool bindText(int idx, const std::string& val);
    bool bindInt64(int idx, int64_t val);

    // ── Step ────────────────────────────────────────────────────────────
    // Returns SQLITE_ROW, SQLITE_DONE, or an error code.
    int step();

    // Reset + clear bindings so the statement can be re-executed.
    void reset();

    // ── Column access (call only after step() returned SQLITE_ROW) ─────
    int64_t      columnInt64(int col) const;
    std::string  columnText(int col)  const;

    // Raw handle — use sparingly.
    sqlite3_stmt* raw() const noexcept { return stmt_; }

private:
    explicit Stmt(sqlite3_stmt* stmt);
    sqlite3_stmt* stmt_;
};

// ── DatabaseManager ────────────────────────────────────────────────────────
// RAII wrapper around a single SQLite connection.
// One instance ↔ one .db file (one course vault).
//
// Design contract:
//   - Factory `open()` creates the connection AND initialises the schema.
//   - Destructor closes it — no leak possible.
//   - Copy is deleted; move is allowed for ownership transfer.
//   - All errors are routed through tien::utils::Logger, never thrown
//     across the JNI boundary.
class DatabaseManager {
public:
    // Open / create a vault at `db_path` and bootstrap its schema.
    // If the file does not exist SQLite will create it.
    // Returns nullptr on failure (logged internally).
    static std::unique_ptr<DatabaseManager> open(const std::string& db_path);

    // Move-only type
    DatabaseManager(DatabaseManager&&) noexcept;
    DatabaseManager& operator=(DatabaseManager&&) noexcept;
    DatabaseManager(const DatabaseManager&)            = delete;
    DatabaseManager& operator=(const DatabaseManager&) = delete;

    // RAII — closes the connection automatically.
    ~DatabaseManager();

    // ── CRUD — Notes ───────────────────────────────────────────────────
    bool insertNote(const std::string& title, const std::string& content);
    bool updateNote(int64_t id, const std::string& title, const std::string& content);
    bool deleteNote(int64_t id);
    std::vector<tien::core::Note> getAllNotes();

    // ── CRUD — Tasks ───────────────────────────────────────────────────
    bool insertTask(const std::string& title, const std::string& details, int64_t dueAt, int priority);
    bool toggleTaskDone(int64_t id, bool done);
    bool deleteTask(int64_t id);
    std::vector<tien::core::Task> getAllTasks();

    // Execute a raw SQL statement.  Returns true on success.
    bool execute(const std::string& sql);

    // Access the underlying connection — use sparingly.
    sqlite3* raw() const noexcept { return db_; }

    // Path of the currently open vault.
    const std::string& path() const noexcept { return db_path_; }

    // True if the connection is usable.
    bool isOpen() const noexcept { return db_ != nullptr; }

private:
    // Private ctor — use the static factory `open()`.
    explicit DatabaseManager(sqlite3* db, std::string path);

    // Idempotent schema bootstrap — called automatically by open().
    bool initSchema();

    sqlite3*    db_;       // Owned — freed in destructor
    std::string db_path_;  // For diagnostics / re-open
};

} // namespace tien::db
