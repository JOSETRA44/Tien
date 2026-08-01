package com.tien.dutic.core

/**
 * Outcome of a call against the aula virtual.
 *
 * Mirrors the CLI's typed errors (`src/core/errors.ts`). The distinction that
 * actually matters is [DuticError.SessionExpired]: it is the one failure the app
 * can *fix*, by sending the user back through the login WebView. Collapsing it
 * into a generic "network error" would leave the user staring at a retry button
 * that can never succeed.
 */
sealed interface DuticResult<out T> {
    data class Ok<T>(val value: T) : DuticResult<T>
    data class Err(val error: DuticError) : DuticResult<Nothing>
}

sealed interface DuticError {

    /** Moodle rejected the session. Recoverable: sign in again. */
    data object SessionExpired : DuticError

    /** No session has ever been captured on this device. */
    data object NotSignedIn : DuticError

    /** Moodle answered with an application-level error code. */
    data class MoodleApi(val message: String, val code: String? = null) : DuticError

    /** Network failed after the retries were exhausted. */
    data class Network(val message: String) : DuticError

    /** The server answered, but not with anything this build can read. */
    data class Unreadable(val message: String) : DuticError
}

// ── Construction ────────────────────────────────────────────────────────────

fun <T> T.asOk(): DuticResult<T> = DuticResult.Ok(this)

fun DuticError.asErr(): DuticResult<Nothing> = DuticResult.Err(this)

// ── Transformation ──────────────────────────────────────────────────────────

inline fun <T, R> DuticResult<T>.map(transform: (T) -> R): DuticResult<R> = when (this) {
    is DuticResult.Ok -> DuticResult.Ok(transform(value))
    is DuticResult.Err -> this
}

inline fun <T, R> DuticResult<T>.flatMap(
    transform: (T) -> DuticResult<R>
): DuticResult<R> = when (this) {
    is DuticResult.Ok -> transform(value)
    is DuticResult.Err -> this
}

fun <T> DuticResult<T>.getOrNull(): T? = (this as? DuticResult.Ok)?.value

fun <T> DuticResult<T>.errorOrNull(): DuticError? = (this as? DuticResult.Err)?.error

val DuticResult<*>.isOk: Boolean get() = this is DuticResult.Ok

/**
 * Internal signal for "the session died mid-call".
 *
 * Thrown rather than returned because it has to unwind out of deeply nested
 * parsing code, and every layer in between would otherwise have to thread the
 * failure back by hand. [com.tien.dutic.auth.DuticAuthenticator] catches it at
 * the boundary and converts it back into a [DuticResult.Err].
 */
class SessionExpiredException : Exception("La sesión del aula virtual caducó")

/** Internal signal for a Moodle application error. */
class MoodleApiException(
    message: String,
    val code: String? = null
) : Exception(message)
