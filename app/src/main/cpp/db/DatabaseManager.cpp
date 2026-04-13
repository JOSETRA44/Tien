#include "DatabaseManager.h"
#include "../utils/Logger.h"

#include <utility>
#include <ctime>

namespace tien::db {

// ═══════════════════════════════════════════════════════════════════════════
//  Stmt — RAII Prepared Statement
// ═══════════════════════════════════════════════════════════════════════════

// ── Factory ────────────────────────────────────────────────────────────────

std::unique_ptr<Stmt> Stmt::prepare(sqlite3* db, const std::string& sql) {
    if (!db) {
        utils::e("Stmt::prepare — null database handle");
        return nullptr;
    }

    sqlite3_stmt* raw = nullptr;
    // nByte = -1 → SQLite computes length up to first \0 (avoids strlen call)
    int rc = sqlite3_prepare_v2(db, sql.c_str(), -1, &raw, nullptr);
    if (rc != SQLITE_OK) {
        utils::e("Stmt::prepare — error %d: %s", rc, sqlite3_errmsg(db));
        // raw may be non-null on some error paths — always finalize
        if (raw) sqlite3_finalize(raw);
        return nullptr;
    }

    return std::unique_ptr<Stmt>(new Stmt(raw));
}

// ── Move / Destruction ─────────────────────────────────────────────────────

Stmt::Stmt(sqlite3_stmt* stmt) : stmt_(stmt) {}

Stmt::Stmt(Stmt&& other) noexcept : stmt_(other.stmt_) {
    other.stmt_ = nullptr;
}

Stmt& Stmt::operator=(Stmt&& other) noexcept {
    if (this != &other) {
        if (stmt_) sqlite3_finalize(stmt_);
        stmt_ = other.stmt_;
        other.stmt_ = nullptr;
    }
    return *this;
}

Stmt::~Stmt() {
    if (stmt_) {
        sqlite3_finalize(stmt_);
        stmt_ = nullptr;
    }
}

// ── Bind ───────────────────────────────────────────────────────────────────

bool Stmt::bindText(int idx, const std::string& val) {
    if (!stmt_) return false;
    // SQLITE_TRANSIENT → SQLite copies the string immediately,
    // so the caller's `val` can go out of scope safely.
    int rc = sqlite3_bind_text(stmt_, idx, val.c_str(),
                               static_cast<int>(val.size()), SQLITE_TRANSIENT);
    if (rc != SQLITE_OK) {
        utils::e("Stmt::bindText — error %d on index %d", rc, idx);
        return false;
    }
    return true;
}

bool Stmt::bindInt64(int idx, int64_t val) {
    if (!stmt_) return false;
    int rc = sqlite3_bind_int64(stmt_, idx, val);
    if (rc != SQLITE_OK) {
        utils::e("Stmt::bindInt64 — error %d on index %d", rc, idx);
        return false;
    }
    return true;
}

// ── Step / Reset ───────────────────────────────────────────────────────────

int Stmt::step() {
    if (!stmt_) return SQLITE_ERROR;
    return sqlite3_step(stmt_);
}

void Stmt::reset() {
    if (stmt_) {
        sqlite3_reset(stmt_);
        sqlite3_clear_bindings(stmt_);
    }
}

// ── Column access ──────────────────────────────────────────────────────────

int64_t Stmt::columnInt64(int col) const {
    return stmt_ ? sqlite3_column_int64(stmt_, col) : 0;
}

std::string Stmt::columnText(int col) const {
    if (!stmt_) return {};
    // reinterpret_cast is safe — SQLite guarantees UTF-8 when using
    // the _text accessor, and the pointer is valid until next step/reset.
    const auto* ptr = reinterpret_cast<const char*>(
        sqlite3_column_text(stmt_, col));
    return ptr ? std::string(ptr) : std::string{};
}

// ═══════════════════════════════════════════════════════════════════════════
//  DatabaseManager
// ═══════════════════════════════════════════════════════════════════════════

// ── Factory ────────────────────────────────────────────────────────────────

std::unique_ptr<DatabaseManager> DatabaseManager::open(const std::string& db_path) {
    sqlite3* raw = nullptr;

    const int flags = SQLITE_OPEN_READWRITE
                    | SQLITE_OPEN_CREATE
                    | SQLITE_OPEN_NOMUTEX;

    int rc = sqlite3_open_v2(db_path.c_str(), &raw, flags, nullptr);
    if (rc != SQLITE_OK) {
        utils::e("DatabaseManager::open — failed to open %s : %s",
                 db_path.c_str(),
                 raw ? sqlite3_errmsg(raw) : "unknown");
        if (raw) sqlite3_close(raw);
        return nullptr;
    }

    auto mgr = std::unique_ptr<DatabaseManager>(
        new DatabaseManager(raw, db_path));

    // Bootstrap schema automatically — no external call needed.
    if (!mgr->initSchema()) {
        utils::e("DatabaseManager::open — schema init failed for %s",
                 db_path.c_str());
        // mgr destroyed → RAII closes the connection
        return nullptr;
    }

    utils::i("DatabaseManager::open — vault ready: %s", db_path.c_str());
    return mgr;
}

// ── Construction / Move / Destruction ──────────────────────────────────────

DatabaseManager::DatabaseManager(sqlite3* db, std::string path)
    : db_(db), db_path_(std::move(path)) {}

DatabaseManager::DatabaseManager(DatabaseManager&& other) noexcept
    : db_(other.db_), db_path_(std::move(other.db_path_)) {
    other.db_ = nullptr;
}

DatabaseManager& DatabaseManager::operator=(DatabaseManager&& other) noexcept {
    if (this != &other) {
        if (db_) sqlite3_close(db_);
        db_      = other.db_;
        db_path_ = std::move(other.db_path_);
        other.db_ = nullptr;
    }
    return *this;
}

DatabaseManager::~DatabaseManager() {
    if (db_) {
        sqlite3_close(db_);
        utils::i("DatabaseManager — vault closed: %s", db_path_.c_str());
        db_ = nullptr;
    }
}

// ── Schema bootstrap (private) ─────────────────────────────────────────────

bool DatabaseManager::initSchema() {
    if (!db_) {
        utils::e("DatabaseManager::initSchema — no open connection");
        return false;
    }

    // notes table — the core entity of each course vault.
    // WAL journal + NORMAL sync are set at DB level in CMake compile flags.
    const char* kSql = R"(
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
    )";

    return execute(kSql);
}

// ── CRUD — Notes ───────────────────────────────────────────────────────────

bool DatabaseManager::insertNote(const std::string& title,
                                 const std::string& content) {
    if (!db_) {
        utils::e("DatabaseManager::insertNote — no open connection");
        return false;
    }

    auto stmt = Stmt::prepare(db_,
        "INSERT INTO notes (title, content, timestamp) VALUES (?, ?, strftime('%s','now'))");
    if (!stmt) {
        utils::e("DatabaseManager::insertNote — prepare failed");
        return false;
    }

    if (!stmt->bindText(1, title) || !stmt->bindText(2, content)) {
        utils::e("DatabaseManager::insertNote — bind failed");
        return false;
    }

    int rc = stmt->step();
    if (rc != SQLITE_DONE) {
        utils::e("DatabaseManager::insertNote — step returned %d: %s",
                 rc, sqlite3_errmsg(db_));
        return false;
    }

    utils::d("DatabaseManager::insertNote — inserted: %s", title.c_str());
    return true;
}

bool DatabaseManager::updateNote(int64_t id,
                                 const std::string& title,
                                 const std::string& content) {
    if (!db_) {
        utils::e("DatabaseManager::updateNote — no open connection");
        return false;
    }

    auto stmt = Stmt::prepare(db_,
        "UPDATE notes SET title = ?, content = ?, timestamp = strftime('%s','now') WHERE id = ?");
    if (!stmt) {
        utils::e("DatabaseManager::updateNote — prepare failed");
        return false;
    }

    if (!stmt->bindText(1, title) || !stmt->bindText(2, content) || !stmt->bindInt64(3, id)) {
        utils::e("DatabaseManager::updateNote — bind failed");
        return false;
    }

    int rc = stmt->step();
    if (rc != SQLITE_DONE) {
        utils::e("DatabaseManager::updateNote — step returned %d: %s",
                 rc, sqlite3_errmsg(db_));
        return false;
    }

    if (sqlite3_changes(db_) <= 0) {
        utils::e("DatabaseManager::updateNote — note id %lld not found", static_cast<long long>(id));
        return false;
    }

    return true;
}

bool DatabaseManager::deleteNote(int64_t id) {
    if (!db_) {
        utils::e("DatabaseManager::deleteNote — no open connection");
        return false;
    }

    auto stmt = Stmt::prepare(db_, "DELETE FROM notes WHERE id = ?");
    if (!stmt) {
        utils::e("DatabaseManager::deleteNote — prepare failed");
        return false;
    }

    if (!stmt->bindInt64(1, id)) {
        utils::e("DatabaseManager::deleteNote — bind failed");
        return false;
    }

    int rc = stmt->step();
    if (rc != SQLITE_DONE) {
        utils::e("DatabaseManager::deleteNote — step returned %d: %s",
                 rc, sqlite3_errmsg(db_));
        return false;
    }

    if (sqlite3_changes(db_) <= 0) {
        utils::e("DatabaseManager::deleteNote — note id %lld not found", static_cast<long long>(id));
        return false;
    }

    return true;
}

std::vector<tien::core::Note> DatabaseManager::getAllNotes() {
    if (!db_) {
        utils::e("DatabaseManager::getAllNotes — no open connection");
        return {};
    }

    auto stmt = Stmt::prepare(db_,
        "SELECT id, title, content, timestamp FROM notes ORDER BY timestamp DESC");
    if (!stmt) {
        utils::e("DatabaseManager::getAllNotes — prepare failed");
        return {};
    }

    std::vector<tien::core::Note> notes;
    notes.reserve(32);  // reasonable initial capacity for a course

    while (stmt->step() == SQLITE_ROW) {
        notes.emplace_back(tien::core::Note{
            stmt->columnInt64(0),         // id (int64_t map 1:1 con SQLite ROWID)
            stmt->columnText(1),          // title (rvalue std::string se mueve implícitamente)
            stmt->columnText(2),          // content
            stmt->columnInt64(3)          // timestamp
        });
    }

    utils::d("DatabaseManager::getAllNotes — returned %zu notes", notes.size());
    return notes;
}

bool DatabaseManager::insertTask(const std::string& title,
                                 const std::string& details,
                                 int64_t dueAt,
                                 int priority) {
    if (!db_) {
        utils::e("DatabaseManager::insertTask — no open connection");
        return false;
    }

    auto stmt = Stmt::prepare(db_,
        "INSERT INTO tasks (title, details, due_at, priority, is_done, created_at) "
        "VALUES (?, ?, ?, ?, 0, strftime('%s','now'))");
    if (!stmt) {
        utils::e("DatabaseManager::insertTask — prepare failed");
        return false;
    }

    if (!stmt->bindText(1, title) ||
        !stmt->bindText(2, details) ||
        !stmt->bindInt64(3, dueAt) ||
        !stmt->bindInt64(4, static_cast<int64_t>(priority))) {
        utils::e("DatabaseManager::insertTask — bind failed");
        return false;
    }

    return stmt->step() == SQLITE_DONE;
}

bool DatabaseManager::toggleTaskDone(int64_t id, bool done) {
    if (!db_) {
        utils::e("DatabaseManager::toggleTaskDone — no open connection");
        return false;
    }

    auto stmt = Stmt::prepare(db_, "UPDATE tasks SET is_done = ? WHERE id = ?");
    if (!stmt) {
        utils::e("DatabaseManager::toggleTaskDone — prepare failed");
        return false;
    }

    if (!stmt->bindInt64(1, done ? 1 : 0) || !stmt->bindInt64(2, id)) {
        utils::e("DatabaseManager::toggleTaskDone — bind failed");
        return false;
    }

    const int rc = stmt->step();
    if (rc != SQLITE_DONE) return false;
    return sqlite3_changes(db_) > 0;
}

bool DatabaseManager::deleteTask(int64_t id) {
    if (!db_) {
        utils::e("DatabaseManager::deleteTask — no open connection");
        return false;
    }

    auto stmt = Stmt::prepare(db_, "DELETE FROM tasks WHERE id = ?");
    if (!stmt) {
        utils::e("DatabaseManager::deleteTask — prepare failed");
        return false;
    }

    if (!stmt->bindInt64(1, id)) {
        utils::e("DatabaseManager::deleteTask — bind failed");
        return false;
    }

    const int rc = stmt->step();
    if (rc != SQLITE_DONE) return false;
    return sqlite3_changes(db_) > 0;
}

std::vector<tien::core::Task> DatabaseManager::getAllTasks() {
    if (!db_) {
        utils::e("DatabaseManager::getAllTasks — no open connection");
        return {};
    }

    auto stmt = Stmt::prepare(db_,
        "SELECT id, title, details, due_at, created_at, priority, is_done "
        "FROM tasks ORDER BY due_at ASC");
    if (!stmt) {
        utils::e("DatabaseManager::getAllTasks — prepare failed");
        return {};
    }

    std::vector<tien::core::Task> tasks;
    while (stmt->step() == SQLITE_ROW) {
        tasks.emplace_back(tien::core::Task{
            stmt->columnInt64(0),
            stmt->columnText(1),
            stmt->columnText(2),
            stmt->columnInt64(3),
            stmt->columnInt64(4),
            static_cast<int>(stmt->columnInt64(5)),
            stmt->columnInt64(6) == 1
        });
    }

    return tasks;
}

// ── Generic execution ──────────────────────────────────────────────────────

bool DatabaseManager::execute(const std::string& sql) {
    if (!db_) {
        utils::e("DatabaseManager::execute — no open connection");
        return false;
    }

    char* err = nullptr;
    int rc = sqlite3_exec(db_, sql.c_str(), nullptr, nullptr, &err);
    if (rc != SQLITE_OK) {
        utils::e("DatabaseManager::execute — SQL error: %s", err ? err : "unknown");
        if (err) sqlite3_free(err);
        return false;
    }
    return true;
}

} // namespace tien::db
