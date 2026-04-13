package com.tien.core

/**
 * JNI bridge to the native shared library **libtien_core.so**.
 *
 * All heavy I/O stays in C++; Kotlin only marshals primitive types across
 * the boundary.  Strings cross as UTF-8 (Android JNI default).
 *
 * Native implementation: app/src/main/cpp/jni/native-lib.cpp
 */
object NativeLib {

    init {
        System.loadLibrary("tien_core")
    }

    // ── Database lifecycle ────────────────────────────────────────────────────

    /**
     * Opens (or creates) an SQLite database at [path] and initialises the
     * required schema.
     *
     * @param path  Absolute path to the `.db` file, e.g.
     *              `context.getDatabasePath("notes.db").absolutePath`
     * @return `true` on success, `false` if the database could not be opened
     *         or the schema migration failed.
     */
    external fun createDb(path: String): Boolean

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Inserts a note into the database located at [path].
     *
     * @param path     Absolute path to the `.db` file.
     * @param title    Note title (non-empty).
     * @param content  Note body text.
     * @return `true` if the row was inserted, `false` on any SQL error.
     */
    external fun insertNote(path: String, title: String, content: String): Boolean

    /**
     * Updates an existing note.
     */
    external fun updateNote(path: String, id: Long, title: String, content: String): Boolean

    /**
     * Deletes a note by id.
     */
    external fun deleteNote(path: String, id: Long): Boolean

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Returns all notes stored in the database at [path] as a JSON string.
     *
     * The returned JSON is a top-level array:
     * ```json
     * [{"id":1,"title":"…","content":"…"}, …]
     * ```
     * An empty array `"[]"` is returned when there are no rows.
     * An empty string `""` signals a fatal database error.
     *
     * @param path  Absolute path to the `.db` file.
     */
    external fun getNotes(path: String): String
}
