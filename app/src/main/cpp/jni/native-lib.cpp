// ═══════════════════════════════════════════════════════════════════════════
//  Tien — JNI bridge
//
//  Contract with com.tien.core.data.nativedb.NativeDatabase:
//
//  1. HANDLE-BASED LIFECYCLE
//     `nativeOpen` returns an opaque jlong owning a DatabaseManager. Every
//     other entry point takes that handle. The previous design re-opened the
//     database file on every single call, paying a file open, a WAL handshake
//     and a schema check per keystroke.
//
//  2. UTF-8 IS CARRIED AS byte[], NEVER AS jstring
//     GetStringUTFChars/NewStringUTF speak *modified* UTF-8 (CESU-8), which
//     encodes astral-plane characters — emoji — as surrogate pairs. Round
//     tripping those through SQLite corrupts them. Kotlin therefore encodes to
//     real UTF-8 and hands over a ByteArray.
//
//  3. ERRORS ARE CODES, NOT EMPTY RESULTS
//     Mutations return a rowid / affected-row count, or a negative DbStatus.
//     Queries return a JSON envelope {"ok":…}. A failure can no longer be
//     mistaken for "no data".
// ═══════════════════════════════════════════════════════════════════════════

#include "../db/DatabaseManager.h"
#include "../core/Models.h"
#include "../utils/Logger.h"

#include <jni.h>
#include <cstdio>
#include <memory>
#include <optional>
#include <string>
#include <sstream>
#include <vector>

using tien::db::DatabaseManager;
using tien::db::DbStatus;
using tien::db::asCode;

namespace {

// ── Handle marshalling ─────────────────────────────────────────────────────

inline DatabaseManager* fromHandle(jlong handle) {
    return reinterpret_cast<DatabaseManager*>(handle);
}

inline jlong toHandle(DatabaseManager* mgr) {
    return reinterpret_cast<jlong>(mgr);
}

// ── String marshalling (real UTF-8 via byte[]) ─────────────────────────────

std::string bytesToStd(JNIEnv* env, jbyteArray array) {
    if (!array) return {};
    const jsize len = env->GetArrayLength(array);
    if (len <= 0) return {};

    std::string out;
    out.resize(static_cast<size_t>(len));
    env->GetByteArrayRegion(array, 0, len, reinterpret_cast<jbyte*>(&out[0]));
    return out;
}

jbyteArray stdToBytes(JNIEnv* env, const std::string& value) {
    const auto len = static_cast<jsize>(value.size());
    jbyteArray array = env->NewByteArray(len);
    if (!array) {
        tien::utils::e("JNI — NewByteArray(%d) failed", static_cast<int>(len));
        return nullptr;
    }
    if (len > 0) {
        env->SetByteArrayRegion(array, 0, len, reinterpret_cast<const jbyte*>(value.data()));
    }
    return array;
}

// ── JSON serialisation ─────────────────────────────────────────────────────

std::string jsonEscape(const std::string& s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (const char c : s) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            default: {
                const auto byte = static_cast<unsigned char>(c);
                if (byte < 0x20) {
                    char buf[8];
                    // %04x on the *unsigned* value — a signed char would sign
                    // extend into \uffxx.
                    std::snprintf(buf, sizeof(buf), "\\u%04x", byte);
                    out += buf;
                } else {
                    // Bytes >= 0x80 are passed through untouched: they are
                    // already valid UTF-8 continuation bytes and JSON permits
                    // raw UTF-8.
                    out += c;
                }
            }
        }
    }
    return out;
}

void appendNote(std::ostringstream& ss, const tien::core::Note& n) {
    ss << "{\"id\":" << n.id << ",\"title\":\"" << jsonEscape(n.title) << '"' << ",\"content\":\""
       << jsonEscape(n.content) << '"' << ",\"createdAt\":" << n.created_at
       << ",\"updatedAt\":" << n.updated_at << ",\"pinned\":" << (n.pinned ? "true" : "false")
       << '}';
}

void appendTask(std::ostringstream& ss, const tien::core::Task& t) {
    ss << "{\"id\":" << t.id << ",\"title\":\"" << jsonEscape(t.title) << '"' << ",\"details\":\""
       << jsonEscape(t.details) << '"' << ",\"dueAt\":" << t.due_at
       << ",\"createdAt\":" << t.created_at << ",\"updatedAt\":" << t.updated_at
       << ",\"priority\":" << t.priority << ",\"isDone\":" << (t.is_done ? "true" : "false") << '}';
}

template <typename T, typename Appender>
std::string successEnvelope(const std::vector<T>& items, Appender append) {
    std::ostringstream ss;
    ss << R"({"ok":true,"data":[)";
    for (size_t i = 0; i < items.size(); ++i) {
        if (i > 0) ss << ',';
        append(ss, items[i]);
    }
    ss << "]}";
    return ss.str();
}

// Single-row lookups reuse the list envelope (0 or 1 elements) so Kotlin has
// one decoder rather than two.
template <typename T, typename Appender>
std::string optionalEnvelope(const std::optional<T>& item, Appender append) {
    std::ostringstream ss;
    ss << R"({"ok":true,"data":[)";
    if (item.has_value()) append(ss, *item);
    ss << "]}";
    return ss.str();
}

std::string failureEnvelope(const std::string& message) {
    std::ostringstream ss;
    ss << R"({"ok":false,"error":")" << jsonEscape(message) << R"("})";
    return ss.str();
}

}  // anonymous namespace

// ═══════════════════════════════════════════════════════════════════════════
//  JNI entry points — com.tien.core.data.nativedb.NativeDatabase
// ═══════════════════════════════════════════════════════════════════════════

extern "C" {

#define TIEN_JNI(ret, name) \
    JNIEXPORT ret JNICALL Java_com_tien_core_data_nativedb_NativeDatabase_##name

// ── Lifecycle ──────────────────────────────────────────────────────────────

TIEN_JNI(jlong, nativeOpen)(JNIEnv* env, jobject, jbyteArray jPath) {
    const std::string path = bytesToStd(env, jPath);
    if (path.empty()) {
        tien::utils::e("nativeOpen — empty path");
        return 0;
    }

    auto mgr = DatabaseManager::open(path);
    if (!mgr) return 0;

    // Ownership moves to Kotlin, which must call nativeClose exactly once.
    return toHandle(mgr.release());
}

TIEN_JNI(void, nativeClose)(JNIEnv*, jobject, jlong handle) {
    if (handle == 0) return;
    delete fromHandle(handle);
}

TIEN_JNI(jbyteArray, nativeLastError)(JNIEnv* env, jobject, jlong handle) {
    auto*             db = fromHandle(handle);
    const std::string msg = db ? db->lastError() : "invalid handle";
    return stdToBytes(env, msg);
}

// ── Notes ──────────────────────────────────────────────────────────────────

TIEN_JNI(jlong, nativeInsertNote)
(JNIEnv* env, jobject, jlong handle, jbyteArray jTitle, jbyteArray jContent) {
    auto* db = fromHandle(handle);
    if (!db) return asCode(DbStatus::ErrorClosed);
    return db->insertNote(bytesToStd(env, jTitle), bytesToStd(env, jContent));
}

TIEN_JNI(jlong, nativeUpdateNote)
(JNIEnv* env, jobject, jlong handle, jlong id, jbyteArray jTitle, jbyteArray jContent) {
    auto* db = fromHandle(handle);
    if (!db) return asCode(DbStatus::ErrorClosed);
    return db->updateNote(id, bytesToStd(env, jTitle), bytesToStd(env, jContent));
}

TIEN_JNI(jlong, nativeSetNotePinned)(JNIEnv*, jobject, jlong handle, jlong id, jboolean pinned) {
    auto* db = fromHandle(handle);
    if (!db) return asCode(DbStatus::ErrorClosed);
    return db->setNotePinned(id, pinned == JNI_TRUE);
}

TIEN_JNI(jlong, nativeDeleteNote)(JNIEnv*, jobject, jlong handle, jlong id) {
    auto* db = fromHandle(handle);
    if (!db) return asCode(DbStatus::ErrorClosed);
    return db->deleteNote(id);
}

TIEN_JNI(jlong, nativeRestoreNote)
(JNIEnv* env, jobject, jlong handle, jlong id, jbyteArray jTitle, jbyteArray jContent,
 jlong createdAt, jlong updatedAt, jboolean pinned) {
    auto* db = fromHandle(handle);
    if (!db) return asCode(DbStatus::ErrorClosed);

    const tien::core::Note note{
        id,        bytesToStd(env, jTitle), bytesToStd(env, jContent), createdAt,
        updatedAt, pinned == JNI_TRUE};
    return db->restoreNote(note);
}

TIEN_JNI(jbyteArray, nativeQueryNotes)
(JNIEnv* env, jobject, jlong handle, jbyteArray jQuery, jint sort) {
    auto* db = fromHandle(handle);
    if (!db) return stdToBytes(env, failureEnvelope("database is closed"));

    const auto notes =
        db->queryNotes(bytesToStd(env, jQuery), static_cast<tien::core::NoteSort>(sort));
    return stdToBytes(env, successEnvelope(notes, appendNote));
}

TIEN_JNI(jbyteArray, nativeFindNote)(JNIEnv* env, jobject, jlong handle, jlong id) {
    auto* db = fromHandle(handle);
    if (!db) return stdToBytes(env, failureEnvelope("database is closed"));

    return stdToBytes(env, optionalEnvelope(db->findNote(id), appendNote));
}

// ── Tasks ──────────────────────────────────────────────────────────────────

TIEN_JNI(jlong, nativeInsertTask)
(JNIEnv* env, jobject, jlong handle, jbyteArray jTitle, jbyteArray jDetails, jlong dueAt,
 jint priority) {
    auto* db = fromHandle(handle);
    if (!db) return asCode(DbStatus::ErrorClosed);
    return db->insertTask(bytesToStd(env, jTitle), bytesToStd(env, jDetails), dueAt,
                          static_cast<int>(priority));
}

TIEN_JNI(jlong, nativeUpdateTask)
(JNIEnv* env, jobject, jlong handle, jlong id, jbyteArray jTitle, jbyteArray jDetails, jlong dueAt,
 jint priority) {
    auto* db = fromHandle(handle);
    if (!db) return asCode(DbStatus::ErrorClosed);
    return db->updateTask(id, bytesToStd(env, jTitle), bytesToStd(env, jDetails), dueAt,
                          static_cast<int>(priority));
}

TIEN_JNI(jlong, nativeSetTaskDone)(JNIEnv*, jobject, jlong handle, jlong id, jboolean done) {
    auto* db = fromHandle(handle);
    if (!db) return asCode(DbStatus::ErrorClosed);
    return db->setTaskDone(id, done == JNI_TRUE);
}

TIEN_JNI(jlong, nativeDeleteTask)(JNIEnv*, jobject, jlong handle, jlong id) {
    auto* db = fromHandle(handle);
    if (!db) return asCode(DbStatus::ErrorClosed);
    return db->deleteTask(id);
}

TIEN_JNI(jlong, nativeRestoreTask)
(JNIEnv* env, jobject, jlong handle, jlong id, jbyteArray jTitle, jbyteArray jDetails, jlong dueAt,
 jlong createdAt, jlong updatedAt, jint priority, jboolean isDone) {
    auto* db = fromHandle(handle);
    if (!db) return asCode(DbStatus::ErrorClosed);

    const tien::core::Task task{
        id,        bytesToStd(env, jTitle),    bytesToStd(env, jDetails), dueAt, createdAt,
        updatedAt, static_cast<int>(priority), isDone == JNI_TRUE};
    return db->restoreTask(task);
}

TIEN_JNI(jbyteArray, nativeQueryTasks)
(JNIEnv* env, jobject, jlong handle, jbyteArray jQuery, jint filter, jlong dayStart, jlong dayEnd) {
    auto* db = fromHandle(handle);
    if (!db) return stdToBytes(env, failureEnvelope("database is closed"));

    const auto tasks = db->queryTasks(
        bytesToStd(env, jQuery), static_cast<tien::core::TaskFilter>(filter), dayStart, dayEnd);
    return stdToBytes(env, successEnvelope(tasks, appendTask));
}

TIEN_JNI(jbyteArray, nativeFindTask)(JNIEnv* env, jobject, jlong handle, jlong id) {
    auto* db = fromHandle(handle);
    if (!db) return stdToBytes(env, failureEnvelope("database is closed"));

    return stdToBytes(env, optionalEnvelope(db->findTask(id), appendTask));
}

#undef TIEN_JNI

}  // extern "C"
