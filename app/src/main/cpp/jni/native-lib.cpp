#include "../db/DatabaseManager.h"
#include "../utils/Logger.h"
#include "../core/Course.h" // <-- REEMPLAZADO PARA EVITAR ODR

#include <jni.h>
#include <memory>
#include <string>
#include <sstream>

namespace {

    // Extract jstring → std::string with guaranteed ReleaseStringUTFChars.
    // Returns empty string on failure (logged).
    inline std::string jstrToStd(JNIEnv* env, jstring jstr) {
        if (!jstr) return {};
        const char* utf = env->GetStringUTFChars(jstr, nullptr);
        if (!utf) {
            // Asumiendo que el Logger tiene un namespace tien::utils. Si da error, comenta esta línea.
            // tien::utils::e("JNI — GetStringUTFChars returned null"); 
            return {};
        }
        std::string result(utf);
        env->ReleaseStringUTFChars(jstr, utf);
        return result;
    }

    // Escape a string for JSON: \ → \\, " → \", newline → \n, etc.
    inline std::string jsonEscape(const std::string& s) {
        std::string out;
        out.reserve(s.size() + 8);
        for (char c : s) {
            switch (c) {
            case '"':  out += "\\\""; break;
            case '\\':  out += "\\\\"; break;
            case '\n':  out += "\\n";  break;
            case '\r':  out += "\\r";  break;
            case '\t':  out += "\\t";  break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                }
                else {
                    out += c;
                }
            }
        }
        return out;
    }

    // Serialize vector<Note> → JSON array string.
    inline std::string notesToJson(const std::vector<tien::core::Note>& notes) {
        std::ostringstream ss;
        ss << '[';
        for (size_t i = 0; i < notes.size(); ++i) {
            const auto& n = notes[i];
            if (i > 0) ss << ',';
            ss << "{\"id\":" << n.id
                << ",\"title\":\"" << jsonEscape(n.title) << '\"'
                << ",\"content\":\"" << jsonEscape(n.content) << '\"'
                << ",\"timestamp\":" << n.timestamp << '}';
        }
        ss << ']';
        return ss.str();
    }

    inline std::string tasksToJson(const std::vector<tien::core::Task>& tasks) {
        std::ostringstream ss;
        ss << '[';
        for (size_t i = 0; i < tasks.size(); ++i) {
            const auto& t = tasks[i];
            if (i > 0) ss << ',';
            ss << "{\"id\":" << t.id
                << ",\"title\":\"" << jsonEscape(t.title) << '\"'
                << ",\"details\":\"" << jsonEscape(t.details) << '\"'
                << ",\"dueAt\":" << t.due_at
                << ",\"createdAt\":" << t.created_at
                << ",\"priority\":" << t.priority
                << ",\"isDone\":" << (t.is_done ? "true" : "false")
                << '}';
        }
        ss << ']';
        return ss.str();
    }

} // anonymous namespace

// ── JNI: Java_com_tien_core_NativeLib_<method> ──────────────────────────────

extern "C" {

    JNIEXPORT jboolean JNICALL
        Java_com_tien_core_NativeLib_createDb(
            JNIEnv* env, jobject /* thiz */, jstring jPath) {

        const std::string path = jstrToStd(env, jPath);
        if (path.empty()) {
            return JNI_FALSE;
        }

        auto db = tien::db::DatabaseManager::open(path);
        // open() auto-calls initSchema(). RAII closes on scope exit.
        return db ? JNI_TRUE : JNI_FALSE;
    }

    JNIEXPORT jboolean JNICALL
        Java_com_tien_core_NativeLib_insertNote(
            JNIEnv* env, jobject /* thiz */,
            jstring jPath, jstring jTitle, jstring jContent) {

        const std::string path = jstrToStd(env, jPath);
        const std::string title = jstrToStd(env, jTitle);
        const std::string content = jstrToStd(env, jContent);
        if (path.empty() || title.empty()) {
            return JNI_FALSE;
        }

        auto db = tien::db::DatabaseManager::open(path);
        if (!db) return JNI_FALSE;

        return db->insertNote(title, content) ? JNI_TRUE : JNI_FALSE;
    }

    JNIEXPORT jboolean JNICALL
        Java_com_tien_core_NativeLib_updateNote(
            JNIEnv* env, jobject /* thiz */,
            jstring jPath, jlong jId, jstring jTitle, jstring jContent) {

        const std::string path = jstrToStd(env, jPath);
        const std::string title = jstrToStd(env, jTitle);
        const std::string content = jstrToStd(env, jContent);
        if (path.empty() || title.empty() || jId <= 0) {
            return JNI_FALSE;
        }

        auto db = tien::db::DatabaseManager::open(path);
        if (!db) return JNI_FALSE;

        return db->updateNote(static_cast<int64_t>(jId), title, content) ? JNI_TRUE : JNI_FALSE;
    }

    JNIEXPORT jboolean JNICALL
        Java_com_tien_core_NativeLib_deleteNote(
            JNIEnv* env, jobject /* thiz */, jstring jPath, jlong jId) {

        const std::string path = jstrToStd(env, jPath);
        if (path.empty() || jId <= 0) {
            return JNI_FALSE;
        }

        auto db = tien::db::DatabaseManager::open(path);
        if (!db) return JNI_FALSE;

        return db->deleteNote(static_cast<int64_t>(jId)) ? JNI_TRUE : JNI_FALSE;
    }

    JNIEXPORT jstring JNICALL
        Java_com_tien_core_NativeLib_getNotes(
            JNIEnv* env, jobject /* thiz */, jstring jPath) {

        const std::string path = jstrToStd(env, jPath);
        if (path.empty()) {
            return env->NewStringUTF("[]");
        }

        auto db = tien::db::DatabaseManager::open(path);
        if (!db) return env->NewStringUTF("[]");

        auto notes = db->getAllNotes();
        std::string json = notesToJson(notes);
        return env->NewStringUTF(json.c_str());
    }

    JNIEXPORT jboolean JNICALL
        Java_com_tien_core_NativeLib_insertTask(
            JNIEnv* env, jobject /* thiz */,
            jstring jPath, jstring jTitle, jstring jDetails, jlong jDueAt, jint jPriority) {

        const std::string path = jstrToStd(env, jPath);
        const std::string title = jstrToStd(env, jTitle);
        const std::string details = jstrToStd(env, jDetails);
        if (path.empty() || title.empty() || jDueAt <= 0) {
            return JNI_FALSE;
        }

        auto db = tien::db::DatabaseManager::open(path);
        if (!db) return JNI_FALSE;

        int priority = static_cast<int>(jPriority);
        if (priority < 0) priority = 0;
        if (priority > 2) priority = 2;
        return db->insertTask(title, details, static_cast<int64_t>(jDueAt), priority) ? JNI_TRUE : JNI_FALSE;
    }

    JNIEXPORT jboolean JNICALL
        Java_com_tien_core_NativeLib_toggleTaskDone(
            JNIEnv* env, jobject /* thiz */,
            jstring jPath, jlong jId, jboolean jDone) {

        const std::string path = jstrToStd(env, jPath);
        if (path.empty() || jId <= 0) return JNI_FALSE;

        auto db = tien::db::DatabaseManager::open(path);
        if (!db) return JNI_FALSE;

        return db->toggleTaskDone(static_cast<int64_t>(jId), jDone == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
    }

    JNIEXPORT jboolean JNICALL
        Java_com_tien_core_NativeLib_deleteTask(
            JNIEnv* env, jobject /* thiz */, jstring jPath, jlong jId) {

        const std::string path = jstrToStd(env, jPath);
        if (path.empty() || jId <= 0) return JNI_FALSE;

        auto db = tien::db::DatabaseManager::open(path);
        if (!db) return JNI_FALSE;

        return db->deleteTask(static_cast<int64_t>(jId)) ? JNI_TRUE : JNI_FALSE;
    }

    JNIEXPORT jstring JNICALL
        Java_com_tien_core_NativeLib_getTasks(
            JNIEnv* env, jobject /* thiz */, jstring jPath) {

        const std::string path = jstrToStd(env, jPath);
        if (path.empty()) return env->NewStringUTF("[]");

        auto db = tien::db::DatabaseManager::open(path);
        if (!db) return env->NewStringUTF("[]");

        auto tasks = db->getAllTasks();
        std::string json = tasksToJson(tasks);
        return env->NewStringUTF(json.c_str());
    }

} // extern "C"
