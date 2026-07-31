#include "DatabaseManager.h"
#include "Migrations.h"
#include "../utils/Logger.h"

#include <utility>
#include <ctime>

namespace tien::db {

namespace {

// Current wall-clock time as Unix epoch seconds. Computed here rather than via
// strftime('%s','now') so timestamps are deterministic and independent of the
// SQL dialect.
inline int64_t nowEpochSeconds() {
    return static_cast<int64_t>(std::time(nullptr));
}

// Escape the LIKE wildcards a user can type, so searching for "50%" matches
// the literal text instead of every row. Pairs with ESCAPE '\' in the SQL.
inline std::string escapeLikePattern(const std::string& raw) {
    std::string out;
    out.reserve(raw.size() + 8);
    for (char c : raw) {
        if (c == '%' || c == '_' || c == '\\') out += '\\';
        out += c;
    }
    return out;
}

inline std::string likeContains(const std::string& raw) {
    return "%" + escapeLikePattern(raw) + "%";
}

const char* orderClauseFor(core::NoteSort sort) {
    switch (sort) {
        case core::NoteSort::OldestFirst:
            return " ORDER BY pinned DESC, updated_at ASC";
        case core::NoteSort::TitleAsc:
            return " ORDER BY pinned DESC, title COLLATE NOCASE ASC";
        case core::NoteSort::RecentlyUpdated:
        default:
            return " ORDER BY pinned DESC, updated_at DESC";
    }
}

} // namespace

// ═══════════════════════════════════════════════════════════════════════════
//  Stmt — RAII Prepared Statement
// ═══════════════════════════════════════════════════════════════════════════

std::unique_ptr<Stmt> Stmt::prepare(sqlite3* db, const std::string& sql) {
    if (!db) {
        utils::e("Stmt::prepare — null database handle");
        return nullptr;
    }

    sqlite3_stmt* raw = nullptr;
    int rc = sqlite3_prepare_v2(db, sql.c_str(),
                                static_cast<int>(sql.size()) + 1, &raw, nullptr);
    if (rc != SQLITE_OK) {
        utils::e("Stmt::prepare — error %d: %s", rc, sqlite3_errmsg(db));
        if (raw) sqlite3_finalize(raw);  // non-null on some error paths
        return nullptr;
    }

    return std::unique_ptr<Stmt>(new Stmt(raw));
}

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

bool Stmt::bindText(int idx, const std::string& val) {
    if (!stmt_) return false;
    // SQLITE_TRANSIENT → SQLite copies immediately, so `val` may go out of
    // scope safely.
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

bool Stmt::bindInt(int idx, int val) {
    return bindInt64(idx, static_cast<int64_t>(val));
}

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

int64_t Stmt::columnInt64(int col) const {
    return stmt_ ? sqlite3_column_int64(stmt_, col) : 0;
}

int Stmt::columnInt(int col) const {
    return static_cast<int>(columnInt64(col));
}

bool Stmt::columnBool(int col) const {
    return columnInt64(col) != 0;
}

std::string Stmt::columnText(int col) const {
    if (!stmt_) return {};
    // SQLite guarantees UTF-8 from the _text accessor; the pointer stays valid
    // until the next step/reset, so copy it now.
    const auto* ptr = reinterpret_cast<const char*>(sqlite3_column_text(stmt_, col));
    if (!ptr) return {};
    const int len = sqlite3_column_bytes(stmt_, col);
    return std::string(ptr, static_cast<size_t>(len));
}

// ═══════════════════════════════════════════════════════════════════════════
//  Transaction
// ═══════════════════════════════════════════════════════════════════════════

Transaction::Transaction(sqlite3* db) : db_(db), active_(false) {}

bool Transaction::begin() {
    if (!db_ || active_) return false;
    if (sqlite3_exec(db_, "BEGIN IMMEDIATE", nullptr, nullptr, nullptr) != SQLITE_OK) {
        utils::e("Transaction::begin — %s", sqlite3_errmsg(db_));
        return false;
    }
    active_ = true;
    return true;
}

bool Transaction::commit() {
    if (!db_ || !active_) return false;
    if (sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr) != SQLITE_OK) {
        utils::e("Transaction::commit — %s", sqlite3_errmsg(db_));
        return false;
    }
    active_ = false;
    return true;
}

Transaction::~Transaction() {
    if (db_ && active_) {
        // Never left half-applied: an early return rolls the work back here.
        sqlite3_exec(db_, "ROLLBACK", nullptr, nullptr, nullptr);
        active_ = false;
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  DatabaseManager
// ═══════════════════════════════════════════════════════════════════════════

std::unique_ptr<DatabaseManager> DatabaseManager::open(const std::string& db_path) {
    if (db_path.empty()) {
        utils::e("DatabaseManager::open — empty path");
        return nullptr;
    }

    sqlite3* raw = nullptr;

    // FULLMUTEX (serialized) rather than NOMUTEX: this single connection is
    // shared by every coroutine on Dispatchers.IO, so SQLite must serialise
    // access itself.
    const int flags = SQLITE_OPEN_READWRITE
                    | SQLITE_OPEN_CREATE
                    | SQLITE_OPEN_FULLMUTEX;

    int rc = sqlite3_open_v2(db_path.c_str(), &raw, flags, nullptr);
    if (rc != SQLITE_OK) {
        utils::e("DatabaseManager::open — failed to open %s : %s",
                 db_path.c_str(), raw ? sqlite3_errmsg(raw) : "unknown");
        if (raw) sqlite3_close_v2(raw);
        return nullptr;
    }

    auto mgr = std::unique_ptr<DatabaseManager>(new DatabaseManager(raw, db_path));

    if (!mgr->applyPragmas()) {
        utils::e("DatabaseManager::open — pragma setup failed for %s", db_path.c_str());
        return nullptr;
    }

    if (!mgr->runMigrations()) {
        utils::e("DatabaseManager::open — migration failed for %s", db_path.c_str());
        return nullptr;  // RAII closes the connection
    }

    utils::i("DatabaseManager::open — ready at schema v%d", kSchemaVersion);
    return mgr;
}

DatabaseManager::DatabaseManager(sqlite3* db, std::string path)
    : db_(db), db_path_(std::move(path)) {}

DatabaseManager::~DatabaseManager() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (db_) {
        // close_v2 (not close) so any statement that outlived us is reclaimed
        // rather than leaking the connection.
        sqlite3_close_v2(db_);
        utils::i("DatabaseManager — closed: %s", db_path_.c_str());
        db_ = nullptr;
    }
}

std::string DatabaseManager::lastError() const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return "database is closed";
    const char* msg = sqlite3_errmsg(db_);
    return msg ? std::string(msg) : std::string("unknown error");
}

// ── Connection tuning ──────────────────────────────────────────────────────

bool DatabaseManager::applyPragmas() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return false;

    // Wait instead of failing instantly when another thread holds the write
    // lock. Without this, concurrent writes surface as SQLITE_BUSY.
    sqlite3_busy_timeout(db_, 5'000);

    // journal_mode returns a row, so it cannot go through sqlite3_exec's
    // "expect no output" path cleanly — run it as a query.
    auto stmt = Stmt::prepare(db_, "PRAGMA journal_mode=WAL");
    if (stmt) {
        stmt->step();
        utils::i("DatabaseManager — journal_mode=%s", stmt->columnText(0).c_str());
    }

    return executeLocked(
        "PRAGMA synchronous=NORMAL;"   // durable enough under WAL, far fewer fsyncs
        "PRAGMA foreign_keys=ON;"
        "PRAGMA temp_store=MEMORY;"
    );
}

// ── Migrations ─────────────────────────────────────────────────────────────

int DatabaseManager::readSchemaVersion() {
    auto stmt = Stmt::prepare(db_, "PRAGMA user_version");
    if (!stmt || stmt->step() != SQLITE_ROW) return 0;
    return stmt->columnInt(0);
}

bool DatabaseManager::writeSchemaVersion(int version) {
    // PRAGMA does not accept bound parameters, so the value is interpolated.
    // It is an int we control, never user input.
    return executeLocked("PRAGMA user_version=" + std::to_string(version));
}

bool DatabaseManager::runMigrations() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return false;

    const int current = readSchemaVersion();
    if (current == kSchemaVersion) {
        utils::d("DatabaseManager — schema already at v%d", current);
        return true;
    }

    if (current > kSchemaVersion) {
        // The file was written by a newer build. Refusing beats corrupting it.
        utils::e("DatabaseManager — database is v%d but this build only knows v%d",
                 current, kSchemaVersion);
        return false;
    }

    utils::i("DatabaseManager — migrating schema v%d → v%d", current, kSchemaVersion);

    Transaction tx(db_);
    if (!tx.begin()) return false;

    for (int version = current; version < kSchemaVersion; ++version) {
        if (!executeLocked(kMigrations[static_cast<size_t>(version)])) {
            utils::e("DatabaseManager — migration %d → %d failed", version, version + 1);
            return false;  // ~Transaction rolls back
        }
    }

    if (!writeSchemaVersion(kSchemaVersion)) return false;
    return tx.commit();
}

// ── Internal helpers ───────────────────────────────────────────────────────

bool DatabaseManager::executeLocked(const std::string& sql) {
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

int64_t DatabaseManager::runMutationLocked(const std::string& label, Stmt& stmt) {
    const int rc = stmt.step();
    if (rc != SQLITE_DONE) {
        utils::e("%s — step returned %d: %s", label.c_str(), rc, sqlite3_errmsg(db_));
        return asCode(rc == SQLITE_CONSTRAINT ? DbStatus::ErrorConflict
                                              : DbStatus::ErrorGeneric);
    }
    const int changed = sqlite3_changes(db_);
    if (changed <= 0) {
        utils::w("%s — no row matched", label.c_str());
        return asCode(DbStatus::ErrorNotFound);
    }
    return static_cast<int64_t>(changed);
}

// ── Notes ──────────────────────────────────────────────────────────────────

int64_t DatabaseManager::insertNote(const std::string& title, const std::string& content) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return asCode(DbStatus::ErrorClosed);
    if (title.empty()) return asCode(DbStatus::ErrorInvalid);

    auto stmt = Stmt::prepare(db_,
        "INSERT INTO notes (title, content, created_at, updated_at, pinned) "
        "VALUES (?, ?, ?, ?, 0)");
    if (!stmt) return asCode(DbStatus::ErrorGeneric);

    const int64_t now = nowEpochSeconds();
    if (!stmt->bindText(1, title) || !stmt->bindText(2, content) ||
        !stmt->bindInt64(3, now)  || !stmt->bindInt64(4, now)) {
        return asCode(DbStatus::ErrorGeneric);
    }

    if (stmt->step() != SQLITE_DONE) {
        utils::e("insertNote — %s", sqlite3_errmsg(db_));
        return asCode(DbStatus::ErrorGeneric);
    }

    // The new rowid lets the repository patch its cache instead of re-reading
    // the whole table.
    return sqlite3_last_insert_rowid(db_);
}

int64_t DatabaseManager::updateNote(int64_t id, const std::string& title,
                                    const std::string& content) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return asCode(DbStatus::ErrorClosed);
    if (id <= 0 || title.empty()) return asCode(DbStatus::ErrorInvalid);

    auto stmt = Stmt::prepare(db_,
        "UPDATE notes SET title = ?, content = ?, updated_at = ? WHERE id = ?");
    if (!stmt) return asCode(DbStatus::ErrorGeneric);

    if (!stmt->bindText(1, title) || !stmt->bindText(2, content) ||
        !stmt->bindInt64(3, nowEpochSeconds()) || !stmt->bindInt64(4, id)) {
        return asCode(DbStatus::ErrorGeneric);
    }
    return runMutationLocked("updateNote", *stmt);
}

int64_t DatabaseManager::setNotePinned(int64_t id, bool pinned) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return asCode(DbStatus::ErrorClosed);
    if (id <= 0) return asCode(DbStatus::ErrorInvalid);

    auto stmt = Stmt::prepare(db_, "UPDATE notes SET pinned = ? WHERE id = ?");
    if (!stmt) return asCode(DbStatus::ErrorGeneric);

    if (!stmt->bindInt(1, pinned ? 1 : 0) || !stmt->bindInt64(2, id)) {
        return asCode(DbStatus::ErrorGeneric);
    }
    return runMutationLocked("setNotePinned", *stmt);
}

int64_t DatabaseManager::deleteNote(int64_t id) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return asCode(DbStatus::ErrorClosed);
    if (id <= 0) return asCode(DbStatus::ErrorInvalid);

    auto stmt = Stmt::prepare(db_, "DELETE FROM notes WHERE id = ?");
    if (!stmt) return asCode(DbStatus::ErrorGeneric);
    if (!stmt->bindInt64(1, id)) return asCode(DbStatus::ErrorGeneric);

    return runMutationLocked("deleteNote", *stmt);
}

int64_t DatabaseManager::restoreNote(const core::Note& note) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return asCode(DbStatus::ErrorClosed);
    if (note.id <= 0 || note.title.empty()) return asCode(DbStatus::ErrorInvalid);

    // Explicit id keeps undo idempotent — the restored note is the same note,
    // not a copy at the top of the list.
    auto stmt = Stmt::prepare(db_,
        "INSERT INTO notes (id, title, content, created_at, updated_at, pinned) "
        "VALUES (?, ?, ?, ?, ?, ?)");
    if (!stmt) return asCode(DbStatus::ErrorGeneric);

    if (!stmt->bindInt64(1, note.id)      || !stmt->bindText(2, note.title) ||
        !stmt->bindText(3, note.content)  || !stmt->bindInt64(4, note.created_at) ||
        !stmt->bindInt64(5, note.updated_at) ||
        !stmt->bindInt(6, note.pinned ? 1 : 0)) {
        return asCode(DbStatus::ErrorGeneric);
    }

    const int rc = stmt->step();
    if (rc != SQLITE_DONE) {
        if (rc == SQLITE_CONSTRAINT) return asCode(DbStatus::ErrorConflict);
        utils::e("restoreNote — %s", sqlite3_errmsg(db_));
        return asCode(DbStatus::ErrorGeneric);
    }
    return note.id;
}

std::vector<core::Note> DatabaseManager::queryNotes(const std::string& query,
                                                    core::NoteSort sort) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return {};

    std::string sql =
        "SELECT id, title, content, created_at, updated_at, pinned FROM notes";
    const bool filtered = !query.empty();
    if (filtered) {
        sql += R"( WHERE (title LIKE ?1 ESCAPE '\' OR content LIKE ?1 ESCAPE '\'))";
    }
    sql += orderClauseFor(sort);

    auto stmt = Stmt::prepare(db_, sql);
    if (!stmt) return {};
    if (filtered && !stmt->bindText(1, likeContains(query))) return {};

    std::vector<core::Note> notes;
    notes.reserve(32);
    while (stmt->step() == SQLITE_ROW) {
        notes.push_back(core::Note{
            stmt->columnInt64(0),
            stmt->columnText(1),
            stmt->columnText(2),
            stmt->columnInt64(3),
            stmt->columnInt64(4),
            stmt->columnBool(5)
        });
    }
    return notes;
}

std::optional<core::Note> DatabaseManager::findNote(int64_t id) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_ || id <= 0) return std::nullopt;

    auto stmt = Stmt::prepare(db_,
        "SELECT id, title, content, created_at, updated_at, pinned "
        "FROM notes WHERE id = ?");
    if (!stmt || !stmt->bindInt64(1, id)) return std::nullopt;
    if (stmt->step() != SQLITE_ROW) return std::nullopt;

    return core::Note{
        stmt->columnInt64(0),
        stmt->columnText(1),
        stmt->columnText(2),
        stmt->columnInt64(3),
        stmt->columnInt64(4),
        stmt->columnBool(5)
    };
}

// ── Tasks ──────────────────────────────────────────────────────────────────

int64_t DatabaseManager::insertTask(const std::string& title, const std::string& details,
                                    int64_t dueAt, int priority) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return asCode(DbStatus::ErrorClosed);
    if (title.empty() || dueAt <= 0) return asCode(DbStatus::ErrorInvalid);

    auto stmt = Stmt::prepare(db_,
        "INSERT INTO tasks (title, details, due_at, created_at, updated_at, priority, is_done) "
        "VALUES (?, ?, ?, ?, ?, ?, 0)");
    if (!stmt) return asCode(DbStatus::ErrorGeneric);

    const int64_t now = nowEpochSeconds();
    if (!stmt->bindText(1, title)   || !stmt->bindText(2, details) ||
        !stmt->bindInt64(3, dueAt)  || !stmt->bindInt64(4, now) ||
        !stmt->bindInt64(5, now)    || !stmt->bindInt(6, core::clampPriority(priority))) {
        return asCode(DbStatus::ErrorGeneric);
    }

    if (stmt->step() != SQLITE_DONE) {
        utils::e("insertTask — %s", sqlite3_errmsg(db_));
        return asCode(DbStatus::ErrorGeneric);
    }
    return sqlite3_last_insert_rowid(db_);
}

int64_t DatabaseManager::updateTask(int64_t id, const std::string& title,
                                    const std::string& details, int64_t dueAt, int priority) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return asCode(DbStatus::ErrorClosed);
    if (id <= 0 || title.empty() || dueAt <= 0) return asCode(DbStatus::ErrorInvalid);

    auto stmt = Stmt::prepare(db_,
        "UPDATE tasks SET title = ?, details = ?, due_at = ?, priority = ?, updated_at = ? "
        "WHERE id = ?");
    if (!stmt) return asCode(DbStatus::ErrorGeneric);

    if (!stmt->bindText(1, title)  || !stmt->bindText(2, details) ||
        !stmt->bindInt64(3, dueAt) || !stmt->bindInt(4, core::clampPriority(priority)) ||
        !stmt->bindInt64(5, nowEpochSeconds()) || !stmt->bindInt64(6, id)) {
        return asCode(DbStatus::ErrorGeneric);
    }
    return runMutationLocked("updateTask", *stmt);
}

int64_t DatabaseManager::setTaskDone(int64_t id, bool done) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return asCode(DbStatus::ErrorClosed);
    if (id <= 0) return asCode(DbStatus::ErrorInvalid);

    auto stmt = Stmt::prepare(db_,
        "UPDATE tasks SET is_done = ?, updated_at = ? WHERE id = ?");
    if (!stmt) return asCode(DbStatus::ErrorGeneric);

    if (!stmt->bindInt(1, done ? 1 : 0) ||
        !stmt->bindInt64(2, nowEpochSeconds()) || !stmt->bindInt64(3, id)) {
        return asCode(DbStatus::ErrorGeneric);
    }
    return runMutationLocked("setTaskDone", *stmt);
}

int64_t DatabaseManager::deleteTask(int64_t id) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return asCode(DbStatus::ErrorClosed);
    if (id <= 0) return asCode(DbStatus::ErrorInvalid);

    auto stmt = Stmt::prepare(db_, "DELETE FROM tasks WHERE id = ?");
    if (!stmt || !stmt->bindInt64(1, id)) return asCode(DbStatus::ErrorGeneric);

    return runMutationLocked("deleteTask", *stmt);
}

int64_t DatabaseManager::restoreTask(const core::Task& task) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return asCode(DbStatus::ErrorClosed);
    if (task.id <= 0 || task.title.empty()) return asCode(DbStatus::ErrorInvalid);

    auto stmt = Stmt::prepare(db_,
        "INSERT INTO tasks (id, title, details, due_at, created_at, updated_at, priority, is_done) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
    if (!stmt) return asCode(DbStatus::ErrorGeneric);

    if (!stmt->bindInt64(1, task.id)         || !stmt->bindText(2, task.title) ||
        !stmt->bindText(3, task.details)     || !stmt->bindInt64(4, task.due_at) ||
        !stmt->bindInt64(5, task.created_at) || !stmt->bindInt64(6, task.updated_at) ||
        !stmt->bindInt(7, core::clampPriority(task.priority)) ||
        !stmt->bindInt(8, task.is_done ? 1 : 0)) {
        return asCode(DbStatus::ErrorGeneric);
    }

    const int rc = stmt->step();
    if (rc != SQLITE_DONE) {
        if (rc == SQLITE_CONSTRAINT) return asCode(DbStatus::ErrorConflict);
        utils::e("restoreTask — %s", sqlite3_errmsg(db_));
        return asCode(DbStatus::ErrorGeneric);
    }
    return task.id;
}

std::vector<core::Task> DatabaseManager::queryTasks(const std::string& query,
                                                    core::TaskFilter filter,
                                                    int64_t dayStart,
                                                    int64_t dayEnd) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_) return {};

    std::string sql =
        "SELECT id, title, details, due_at, created_at, updated_at, priority, is_done FROM tasks";

    // Bind indices are assigned in the same order the clauses are appended, so
    // the two stay in step no matter which filters are active.
    std::vector<std::string> textBinds;
    std::vector<int64_t>     intBinds;
    std::string              where;

    auto addClause = [&where](const std::string& clause) {
        where += where.empty() ? " WHERE " : " AND ";
        where += clause;
    };

    int nextIndex = 1;

    if (!query.empty()) {
        const int idx = nextIndex++;
        addClause("(title LIKE ?" + std::to_string(idx) + R"( ESCAPE '\' OR details LIKE ?)"
                  + std::to_string(idx) + R"( ESCAPE '\'))");
        textBinds.push_back(likeContains(query));
    }

    if (filter == core::TaskFilter::Pending) {
        addClause("is_done = 0");
    } else if (filter == core::TaskFilter::Completed) {
        addClause("is_done = 1");
    }

    if (dayStart > 0 && dayEnd > dayStart) {
        const int startIdx = nextIndex++;
        const int endIdx   = nextIndex++;
        addClause("due_at >= ?" + std::to_string(startIdx) +
                  " AND due_at < ?" + std::to_string(endIdx));
        intBinds.push_back(dayStart);
        intBinds.push_back(dayEnd);
    }

    // Pending work first, then chronologically, then most urgent.
    sql += where + " ORDER BY is_done ASC, due_at ASC, priority DESC";

    auto stmt = Stmt::prepare(db_, sql);
    if (!stmt) return {};

    int bindIdx = 1;
    for (const auto& t : textBinds) {
        if (!stmt->bindText(bindIdx++, t)) return {};
    }
    for (const auto v : intBinds) {
        if (!stmt->bindInt64(bindIdx++, v)) return {};
    }

    std::vector<core::Task> tasks;
    tasks.reserve(32);
    while (stmt->step() == SQLITE_ROW) {
        tasks.push_back(core::Task{
            stmt->columnInt64(0),
            stmt->columnText(1),
            stmt->columnText(2),
            stmt->columnInt64(3),
            stmt->columnInt64(4),
            stmt->columnInt64(5),
            stmt->columnInt(6),
            stmt->columnBool(7)
        });
    }
    return tasks;
}

std::optional<core::Task> DatabaseManager::findTask(int64_t id) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!db_ || id <= 0) return std::nullopt;

    auto stmt = Stmt::prepare(db_,
        "SELECT id, title, details, due_at, created_at, updated_at, priority, is_done "
        "FROM tasks WHERE id = ?");
    if (!stmt || !stmt->bindInt64(1, id)) return std::nullopt;
    if (stmt->step() != SQLITE_ROW) return std::nullopt;

    return core::Task{
        stmt->columnInt64(0),
        stmt->columnText(1),
        stmt->columnText(2),
        stmt->columnInt64(3),
        stmt->columnInt64(4),
        stmt->columnInt64(5),
        stmt->columnInt(6),
        stmt->columnBool(7)
    };
}

} // namespace tien::db
