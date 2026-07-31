package com.tien.core.domain.model

/**
 * A note, exactly as the domain understands it.
 *
 * Mirrors `tien::core::Note` in `cpp/core/Models.h`. Timestamps are Unix epoch
 * **seconds** (not millis) to match the SQLite columns.
 *
 * `createdAt` and `updatedAt` used to be a single `timestamp` column, which made
 * "sort by when I wrote it" and "sort by when I last touched it" the same query.
 */
data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false
) {
    /** True once the note has been persisted and owns a real rowid. */
    val isPersisted: Boolean get() = id > 0

    val hasBody: Boolean get() = content.isNotBlank()

    companion object {
        /** Sentinel id for a note that has not been written yet. */
        const val UNSAVED_ID: Long = 0L
    }
}

/** Ordering options for the notes list. Applied in SQL, never in memory. */
enum class NoteSort(val nativeValue: Int) {
    RECENTLY_UPDATED(0),
    OLDEST_FIRST(1),
    TITLE_ASC(2);

    companion object {
        val DEFAULT = RECENTLY_UPDATED
    }
}
