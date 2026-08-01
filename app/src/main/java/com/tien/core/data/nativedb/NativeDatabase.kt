package com.tien.core.data.nativedb

/**
 * Raw JNI surface of **libtien_core.so**. Nothing outside this package should
 * touch it — [NativeConnection] is the supported entry point.
 *
 * Two deliberate shapes in this contract:
 *
 * **1. Handles, not paths.** Every call takes the `long` returned by
 * [nativeOpen]. The previous bridge passed a file path to each function and the
 * C++ side re-opened the database every time, paying a file open, a WAL
 * handshake and a schema check per call.
 *
 * **2. `ByteArray`, not `String`.** JNI's `GetStringUTFChars` / `NewStringUTF`
 * speak *modified* UTF-8, which encodes astral-plane characters — emoji — as
 * surrogate pairs rather than as the 4-byte sequence real UTF-8 uses. Round
 * tripping those through SQLite mangles them. Passing bytes we encoded
 * ourselves keeps the text intact.
 *
 * Native implementation: `app/src/main/cpp/jni/native-lib.cpp`
 */
internal object NativeDatabase {

    init {
        System.loadLibrary("tien_core")
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** @return an owning handle, or 0 on failure. Must be closed exactly once. */
    external fun nativeOpen(path: ByteArray): Long

    /** Releases the connection. Passing 0 is a no-op. */
    external fun nativeClose(handle: Long)

    /** Last SQLite error message for diagnostics, as UTF-8 bytes. */
    external fun nativeLastError(handle: Long): ByteArray?

    // ── Notes ─────────────────────────────────────────────────────────────────
    // Mutations return the new rowid or the affected-row count; negative values
    // are `DbStatus` codes (see NativeStatus).

    external fun nativeInsertNote(handle: Long, title: ByteArray, content: ByteArray): Long

    external fun nativeUpdateNote(
        handle: Long,
        id: Long,
        title: ByteArray,
        content: ByteArray
    ): Long

    external fun nativeSetNotePinned(handle: Long, id: Long, pinned: Boolean): Long

    external fun nativeDeleteNote(handle: Long, id: Long): Long

    external fun nativeRestoreNote(
        handle: Long,
        id: Long,
        title: ByteArray,
        content: ByteArray,
        createdAt: Long,
        updatedAt: Long,
        pinned: Boolean
    ): Long

    /** @return a UTF-8 JSON envelope: `{"ok":true,"data":[…]}` or `{"ok":false,…}`. */
    external fun nativeQueryNotes(handle: Long, query: ByteArray, sort: Int): ByteArray?

    /**
     * Single-row lookup by primary key. Returns the same envelope shape with
     * zero or one element, so one decoder covers both cases.
     */
    external fun nativeFindNote(handle: Long, id: Long): ByteArray?

    // ── Tasks ─────────────────────────────────────────────────────────────────

    external fun nativeInsertTask(
        handle: Long,
        title: ByteArray,
        details: ByteArray,
        dueAt: Long,
        priority: Int
    ): Long

    external fun nativeUpdateTask(
        handle: Long,
        id: Long,
        title: ByteArray,
        details: ByteArray,
        dueAt: Long,
        priority: Int
    ): Long

    external fun nativeSetTaskDone(handle: Long, id: Long, done: Boolean): Long

    external fun nativeDeleteTask(handle: Long, id: Long): Long

    /**
     * Restores a deleted task with its original identity intact.
     *
     * The parameter list mirrors every persisted column because that is what
     * "restore exactly what was there" means. Wrapping them in a data class
     * would not help: JNI cannot read Kotlin objects without reflection from
     * C++, which is both slower and far more fragile than passing primitives.
     */
    @Suppress("LongParameterList")
    external fun nativeRestoreTask(
        handle: Long,
        id: Long,
        title: ByteArray,
        details: ByteArray,
        dueAt: Long,
        createdAt: Long,
        updatedAt: Long,
        priority: Int,
        isDone: Boolean
    ): Long

    external fun nativeQueryTasks(
        handle: Long,
        query: ByteArray,
        filter: Int,
        dayStart: Long,
        dayEnd: Long
    ): ByteArray?

    /** Single-row lookup by primary key. See [nativeFindNote]. */
    external fun nativeFindTask(handle: Long, id: Long): ByteArray?
}

/**
 * Negative return codes from the native layer, mirroring `tien::db::DbStatus`.
 *
 * Keeping these as named constants means a caller reads `NativeStatus.NOT_FOUND`
 * instead of testing for a bare `-2`.
 */
internal object NativeStatus {
    const val ERROR_GENERIC = -1L
    const val ERROR_NOT_FOUND = -2L
    const val ERROR_CONFLICT = -3L
    const val ERROR_CLOSED = -4L
    const val ERROR_INVALID = -5L

    /** Native contract: any negative value is an error, everything else is data. */
    fun isError(code: Long): Boolean = code < 0
}
