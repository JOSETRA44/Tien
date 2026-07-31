package com.tien.core.data.nativedb

import android.util.Log

/**
 * Owns the lifetime of the single native SQLite connection.
 *
 * One instance per process, created by the DI container and held for as long as
 * the app lives. Opening is lazy and retried: if the first attempt fails (disk
 * full, corrupt file) the handle stays unset and the next call tries again,
 * instead of wedging the app into a permanently broken state.
 *
 * Thread safety: `handle()` is guarded by a monitor so two coroutines racing on
 * first use cannot open — and leak — two connections. The connection itself is
 * opened SERIALIZED on the C++ side, so concurrent use of the returned handle is
 * safe.
 */
internal class NativeConnection(private val databasePath: String) {

    private val lock = Any()

    private var handle: Long = NO_HANDLE
    private var closed: Boolean = false

    /**
     * @return a usable native handle, or [NO_HANDLE] when the database cannot be
     *   opened. Callers must treat 0 as "unavailable", never as a valid handle.
     */
    fun handle(): Long = synchronized(lock) {
        if (closed) return NO_HANDLE
        if (handle == NO_HANDLE) {
            handle = NativeDatabase.nativeOpen(databasePath.toByteArray(Charsets.UTF_8))
            if (handle == NO_HANDLE) {
                Log.e(TAG, "Failed to open native database at $databasePath")
            } else {
                Log.i(TAG, "Native database opened")
            }
        }
        handle
    }

    val isOpen: Boolean get() = synchronized(lock) { !closed && handle != NO_HANDLE }

    /** Last error reported by SQLite, for logging and [DataError.Unknown]. */
    fun lastError(): String {
        val current = synchronized(lock) { handle }
        if (current == NO_HANDLE) return "database is not open"
        return NativeDatabase.nativeLastError(current)
            ?.toString(Charsets.UTF_8)
            ?: "unknown native error"
    }

    /**
     * Releases the native connection. Idempotent — the guard matters because a
     * double `nativeClose` would `delete` the same pointer twice.
     */
    fun close() = synchronized(lock) {
        if (closed) return
        closed = true
        if (handle != NO_HANDLE) {
            NativeDatabase.nativeClose(handle)
            handle = NO_HANDLE
            Log.i(TAG, "Native database closed")
        }
    }

    companion object {
        const val NO_HANDLE: Long = 0L
        private const val TAG = "NativeConnection"
    }
}
