package com.tien.core.domain.model

/**
 * A scheduled task. Mirrors `tien::core::Task` in `cpp/core/Models.h`.
 *
 * Timestamps are Unix epoch **seconds**.
 */
data class Task(
    val id: Long,
    val title: String,
    val details: String,
    val dueAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val priority: Priority,
    val isDone: Boolean
) {
    val hasDetails: Boolean get() = details.isNotBlank()

    /** True when the deadline has passed and the task is still open. */
    fun isOverdue(nowEpochSeconds: Long): Boolean = !isDone && dueAt < nowEpochSeconds

    /** True when the task is due within [withinSeconds] and still open. */
    fun isDueSoon(nowEpochSeconds: Long, withinSeconds: Long = SOON_WINDOW_SECONDS): Boolean =
        !isDone && dueAt >= nowEpochSeconds && dueAt - nowEpochSeconds <= withinSeconds

    companion object {
        const val UNSAVED_ID: Long = 0L

        /** "Due soon" means inside the next 24 hours. */
        const val SOON_WINDOW_SECONDS: Long = 24 * 60 * 60
    }
}

/**
 * Task urgency. The integer is the persisted representation — it is part of the
 * on-disk contract, so the values must never be reordered.
 */
enum class Priority(val nativeValue: Int) {
    LOW(0),
    MEDIUM(1),
    HIGH(2);

    companion object {
        val DEFAULT = MEDIUM

        /** Maps a persisted integer back, falling back rather than throwing. */
        fun fromNative(value: Int): Priority =
            entries.firstOrNull { it.nativeValue == value } ?: DEFAULT
    }
}

/** Completion filter for the agenda. Applied in SQL. */
enum class TaskFilter(val nativeValue: Int) {
    ALL(0),
    PENDING(1),
    COMPLETED(2);

    companion object {
        val DEFAULT = ALL
    }
}
