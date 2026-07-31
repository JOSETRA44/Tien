#pragma once

#include <string>
#include <memory>
#include <mutex>
#include <vector>
#include <cstdint>
#include <optional>

#include "../sqlite3/sqlite3.h"
#include "../core/Models.h"

namespace tien::db {

// ── Result codes crossing the JNI boundary ─────────────────────────────────
// Negative values are errors; non-negative values carry data (a rowid or an
// affected-row count). Kotlin maps these onto its own sealed error type, so a
// failure is never mistaken for "no results" — the bug this replaces.
enum class DbStatus : int64_t {
    Ok            =  0,
    ErrorGeneric  = -1,
    ErrorNotFound = -2,
    ErrorConflict = -3,
    ErrorClosed   = -4,
    ErrorInvalid  = -5
};

constexpr int64_t asCode(DbStatus s) noexcept { return static_cast<int64_t>(s); }

// ── RAII Prepared-Statement Wrapper ────────────────────────────────────────
// Guarantees sqlite3_finalize on every exit path. Move-only; the factory
// `prepare()` is the only way to construct one.
class Stmt {
public:
    static std::unique_ptr<Stmt> prepare(sqlite3* db, const std::string& sql);

    Stmt(Stmt&&) noexcept;
    Stmt& operator=(Stmt&&) noexcept;
    Stmt(const Stmt&)            = delete;
    Stmt& operator=(const Stmt&) = delete;

    ~Stmt();  // sqlite3_finalize

    // ── Bind (1-based indices, matching SQLite) ────────────────────────────
    bool bindText(int idx, const std::string& val);
    bool bindInt64(int idx, int64_t val);
    bool bindInt(int idx, int val);

    // Returns SQLITE_ROW, SQLITE_DONE, or an error code.
    int step();

    // Reset + clear bindings so the statement can be re-executed.
    void reset();

    // ── Column access (valid only after step() returned SQLITE_ROW) ────────
    int64_t     columnInt64(int col) const;
    int         columnInt(int col)   const;
    std::string columnText(int col)  const;
    bool        columnBool(int col)  const;

    sqlite3_stmt* raw() const noexcept { return stmt_; }

private:
    explicit Stmt(sqlite3_stmt* stmt);
    sqlite3_stmt* stmt_;
};

// ── RAII Transaction Guard ─────────────────────────────────────────────────
// Rolls back on destruction unless commit() was called. Makes multi-statement
// work atomic even when an early `return` bails out mid-way.
class Transaction {
public:
    explicit Transaction(sqlite3* db);
    ~Transaction();

    Transaction(const Transaction&)            = delete;
    Transaction& operator=(const Transaction&) = delete;

    bool begin();
    bool commit();

    bool isActive() const noexcept { return active_; }

private:
    sqlite3* db_;
    bool     active_;
};

// ── DatabaseManager ────────────────────────────────────────────────────────
// RAII wrapper around one long-lived SQLite connection.
//
// Design contract:
//   - `open()` creates the connection, applies pragmas, and runs migrations.
//     It is called ONCE per process; the handle is then held by Kotlin and
//     reused for every operation. Opening per-call (the previous design) cost
//     a file open, a WAL handshake and a schema check on every keystroke.
//   - The connection is opened SERIALIZED (FULLMUTEX) and every public method
//     additionally takes `mutex_`, so calls from any Dispatchers.IO thread are
//     safe.
//   - Errors are returned as DbStatus codes and logged; nothing is thrown
//     across the JNI boundary.
class DatabaseManager {
public:
    // Open / create the database at `db_path`, apply pragmas, run migrations.
    // Returns nullptr on failure (logged internally).
    static std::unique_ptr<DatabaseManager> open(const std::string& db_path);

    DatabaseManager(const DatabaseManager&)            = delete;
    DatabaseManager& operator=(const DatabaseManager&) = delete;
    DatabaseManager(DatabaseManager&&)                 = delete;
    DatabaseManager& operator=(DatabaseManager&&)      = delete;

    ~DatabaseManager();

    // ── Notes ──────────────────────────────────────────────────────────────
    // Returns the new rowid, or a negative DbStatus code.
    int64_t insertNote(const std::string& title, const std::string& content);

    // Returns the number of affected rows, or a negative DbStatus code.
    int64_t updateNote(int64_t id, const std::string& title, const std::string& content);
    int64_t setNotePinned(int64_t id, bool pinned);
    int64_t deleteNote(int64_t id);

    // Restores a previously deleted note, preserving its original identity and
    // timestamps so "undo" is a true undo rather than a re-insert.
    int64_t restoreNote(const core::Note& note);

    // Search + ordering are pushed down to SQL. An empty `query` means "all".
    std::vector<core::Note> queryNotes(const std::string& query, core::NoteSort sort);

    std::optional<core::Note> findNote(int64_t id);

    // ── Tasks ──────────────────────────────────────────────────────────────
    int64_t insertTask(const std::string& title, const std::string& details,
                       int64_t dueAt, int priority);
    int64_t updateTask(int64_t id, const std::string& title, const std::string& details,
                       int64_t dueAt, int priority);
    int64_t setTaskDone(int64_t id, bool done);
    int64_t deleteTask(int64_t id);
    int64_t restoreTask(const core::Task& task);

    // `dayStart`/`dayEnd` bound due_at to a single day when both are > 0.
    std::vector<core::Task> queryTasks(const std::string& query,
                                       core::TaskFilter filter,
                                       int64_t dayStart,
                                       int64_t dayEnd);

    std::optional<core::Task> findTask(int64_t id);

    // ── Diagnostics ────────────────────────────────────────────────────────
    const std::string& path() const noexcept { return db_path_; }
    bool isOpen() const noexcept { return db_ != nullptr; }
    std::string lastError() const;

private:
    explicit DatabaseManager(sqlite3* db, std::string path);

    // Connection tuning applied once, right after open.
    bool applyPragmas();

    // Runs every pending migration inside one transaction.
    bool runMigrations();

    int  readSchemaVersion();
    bool writeSchemaVersion(int version);

    // Executes a (possibly multi-statement) script. Caller must hold mutex_.
    bool executeLocked(const std::string& sql);

    // Shared implementation for UPDATE/DELETE-style statements that report
    // affected rows. Caller must hold mutex_.
    int64_t runMutationLocked(const std::string& label, Stmt& stmt);

    sqlite3*           db_;
    std::string        db_path_;
    mutable std::mutex mutex_;
};

} // namespace tien::db
