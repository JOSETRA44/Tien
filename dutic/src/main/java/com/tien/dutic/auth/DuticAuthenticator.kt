package com.tien.dutic.auth

import com.tien.dutic.core.DuticError
import com.tien.dutic.core.DuticResult
import com.tien.dutic.core.DuticSession
import com.tien.dutic.core.MoodleApiException
import com.tien.dutic.core.SessionExpiredException
import com.tien.dutic.core.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Guards every call that needs a session.
 *
 * Mirrors `src/core/auth.ts`, minus one thing it deliberately does **not**
 * carry over: the CLI's `interactive` mode, which pops open a browser window
 * mid-operation. A phone cannot do that without hijacking whatever the user was
 * doing, so expiry is reported as [DuticError.SessionExpired] and the UI decides
 * when to show the login screen. Renewal becomes a user action rather than a
 * side effect.
 */
class DuticAuthenticator(
    private val sessionStore: SessionStore,
    private val now: () -> Long = System::currentTimeMillis
) {

    /** Emits the current session, or null while signed out. */
    val session: Flow<DuticSession?> = sessionStore.session

    /** Emits whether a usable session exists — what a UI badge binds to. */
    val isSignedIn: Flow<Boolean> = sessionStore.session.map { it?.isUsable == true }

    suspend fun currentSession(): DuticSession? = sessionStore.read()?.takeIf { it.isUsable }

    /** Stores what the login WebView captured. */
    suspend fun completeLogin(capture: LoginCapture): DuticResult<DuticSession> {
        val session = DuticLogin.buildSession(
            cookieHeader = capture.cookieHeader,
            sesskey = capture.sesskey,
            currentUrl = capture.currentUrl,
            nowMillis = now()
        ) ?: return DuticResult.Err(
            DuticError.Unreadable(
                "El inicio de sesión terminó sin cookie o sin sesskey. Vuelve a intentarlo."
            )
        )

        sessionStore.save(session)
        return DuticResult.Ok(session)
    }

    suspend fun signOut() {
        sessionStore.clear()
    }

    /**
     * Runs [operation] with a live session, translating every failure mode into
     * a [DuticResult].
     *
     * This is the single place where the exceptions thrown deep inside parsing
     * become values. Everything above it — repositories, ViewModels, UI —
     * handles results, never try/catch.
     *
     * There is no automatic retry-after-renewal as in the CLI: renewal needs the
     * user, so a caller that gets [DuticError.SessionExpired] should send them to
     * the login screen and run the operation again afterwards.
     */
    suspend fun <T> withSession(
        operation: suspend (DuticSession) -> T
    ): DuticResult<T> {
        val session = currentSession()
            ?: return DuticResult.Err(DuticError.NotSignedIn)

        return try {
            DuticResult.Ok(operation(session))
        } catch (_: SessionExpiredException) {
            // Drop the dead cookie so the UI stops claiming the user is signed
            // in while every call fails.
            sessionStore.clear()
            DuticResult.Err(DuticError.SessionExpired)
        } catch (api: MoodleApiException) {
            DuticResult.Err(DuticError.MoodleApi(api.message.orEmpty(), api.code))
        } catch (io: IOException) {
            DuticResult.Err(DuticError.Network(io.message ?: "Fallo de red"))
        } catch (parse: Exception) {
            // A parse failure means Moodle changed its shape — a defect to fix,
            // not a transient condition, so it is reported distinctly.
            DuticResult.Err(DuticError.Unreadable(parse.message ?: "Respuesta ilegible"))
        }
    }
}
