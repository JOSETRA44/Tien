package com.tien.core.core.result

/**
 * Outcome of an operation that can fail in a way the UI must react to.
 *
 * The previous data layer returned `Boolean` for writes and `"[]"` for failed
 * reads, so "the database is broken" and "there is nothing here" were
 * indistinguishable — the app rendered an empty-state screen on hard failures.
 * Making the failure branch part of the type removes that whole class of bug.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: DataError) : AppResult<Nothing>

    val isSuccess: Boolean get() = this is Success
}

/** Everything the data layer can fail with, as a closed set. */
sealed interface DataError {
    /** The native database could not be opened or has already been closed. */
    data object Unavailable : DataError

    /** The row the operation targeted no longer exists. */
    data object NotFound : DataError

    /** A uniqueness / constraint violation, e.g. restoring an id that came back. */
    data object Conflict : DataError

    /** Input rejected before it reached SQLite (blank title, invalid due date…). */
    data class Validation(val field: Field) : DataError {
        enum class Field { TITLE, DUE_DATE }
    }

    /** The native layer returned a payload this build cannot parse. */
    data object Corrupted : DataError

    /** Anything else, carrying whatever the native layer reported. */
    data class Unknown(val message: String) : DataError
}

// ── Construction helpers ───────────────────────────────────────────────────

fun <T> T.asSuccess(): AppResult<T> = AppResult.Success(this)

fun DataError.asFailure(): AppResult<Nothing> = AppResult.Failure(this)

// ── Transformation ─────────────────────────────────────────────────────────

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> = apply {
    if (this is AppResult.Success) action(data)
}

inline fun <T> AppResult<T>.onFailure(action: (DataError) -> Unit): AppResult<T> = apply {
    if (this is AppResult.Failure) action(error)
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

fun <T> AppResult<T>.errorOrNull(): DataError? = (this as? AppResult.Failure)?.error
