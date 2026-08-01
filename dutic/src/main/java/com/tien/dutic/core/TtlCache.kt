package com.tien.dutic.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Short-lived in-memory cache, mirroring `src/core/cache.ts`.
 *
 * A full task sweep asks for the same course state several times — once to list
 * modules, again to resolve each assignment. Without this the app would re-fetch
 * the same page a dozen times per refresh, over a phone connection.
 *
 * Memory only, and short: this caches *this session's* reads, and is not a
 * substitute for real offline storage. It is deliberately not persisted, because
 * stale grades shown as current are worse than a spinner.
 */
internal class TtlCache(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val now: () -> Long = System::currentTimeMillis
) {

    private data class Entry(val value: Any?, val storedAt: Long)

    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()

    /**
     * Returns the cached value for [key], or computes and stores it.
     *
     * The lock is held across [compute] on purpose: it collapses the concurrent
     * sweeps that would otherwise all miss at once and fire the same request in
     * parallel. Requests here are network-bound and idempotent, so serialising
     * them costs nothing and saves duplicate round trips.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> getOrPut(key: String, compute: suspend () -> T): T = mutex.withLock {
        val cached = entries[key]
        if (cached != null && now() - cached.storedAt < ttlMillis) {
            return@withLock cached.value as T
        }
        val fresh = compute()
        entries[key] = Entry(fresh, now())
        fresh
    }

    suspend fun invalidate(prefix: String? = null) = mutex.withLock {
        if (prefix == null) {
            entries.clear()
        } else {
            entries.keys.filter { it.startsWith(prefix) }.forEach(entries::remove)
        }
    }

    companion object {
        /**
         * Two minutes: long enough to cover one refresh's worth of repeated
         * reads, short enough that pulling to refresh really does refresh.
         */
        const val DEFAULT_TTL_MILLIS: Long = 2 * 60 * 1000

        fun key(vararg parts: Any?): String = parts.joinToString(separator = ":")
    }
}
