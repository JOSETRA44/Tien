package com.tien.dutic.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The login handshake.
 *
 * These are the checks that decide whether a sign-in "worked". Getting one wrong
 * means storing a half-session: the app looks signed in and every call fails,
 * which is the most confusing state the module can be in.
 */
class DuticLoginTest {

    private val now = 1_776_000_000_000L
    private val dashboard = "https://aulavirtual.unsa.edu.pe/2026A/my/"

    @Test
    fun `picks MoodleSession out of a crowded cookie header`() {
        val header = "MOODLEID1_=abc; MoodleSession=s3cr3tvalue; _ga=GA1.2.99"

        assertEquals("s3cr3tvalue", DuticLogin.parseMoodleSessionCookie(header))
    }

    /**
     * The prefix check must be anchored: `MOODLEID1_` and `MoodleSessionTest`
     * both contain the name, and matching either would store the wrong value.
     */
    @Test
    fun `does not match a cookie that merely contains the name`() {
        assertNull(DuticLogin.parseMoodleSessionCookie("MOODLEID1_MoodleSession=nope"))
    }

    @Test
    fun `missing or empty cookie headers yield null`() {
        assertNull(DuticLogin.parseMoodleSessionCookie(null))
        assertNull(DuticLogin.parseMoodleSessionCookie(""))
        assertNull(DuticLogin.parseMoodleSessionCookie("MoodleSession="))
    }

    @Test
    fun `the dashboard means signed in`() {
        assertTrue(DuticLogin.isSignedInUrl(dashboard))
        assertTrue(
            DuticLogin.isSignedInUrl("https://aulavirtual.unsa.edu.pe/2026A/course/view.php?id=7")
        )
    }

    /**
     * Google's own redirects must not count. Firing there would capture a cookie
     * before Moodle has issued a session.
     */
    @Test
    fun `other hosts never count as signed in`() {
        assertFalse(DuticLogin.isSignedInUrl("https://accounts.google.com/o/oauth2/auth"))
        assertFalse(DuticLogin.isSignedInUrl("https://login.microsoftonline.com/"))
    }

    @Test
    fun `the login page is not a signed-in url`() {
        val loginUrl = "https://aulavirtual.unsa.edu.pe/2026A/login/index.php"

        assertFalse(DuticLogin.isSignedInUrl(loginUrl))
        assertTrue(DuticLogin.isLoginUrl(loginUrl))
    }

    @Test
    fun `builds a session and derives the real semester from the url`() {
        val session = DuticLogin.buildSession(
            cookieHeader = "MoodleSession=cookie123",
            sesskey = "KEY123",
            // A term different from the build-time default: the whole point is
            // that a stale default corrects itself here.
            currentUrl = "https://aulavirtual.unsa.edu.pe/2027B/my/",
            nowMillis = now
        )

        assertNotNull(session)
        assertEquals("cookie123", session!!.moodleSession)
        assertEquals("KEY123", session.sesskey)
        assertEquals("https://aulavirtual.unsa.edu.pe/2027B", session.siteUrl)
        assertEquals("2027B", session.semester)
        assertTrue(session.isUsable)
    }

    @Test
    fun `strips the quotes a WebView adds around evaluated javascript`() {
        // evaluateJavascript returns JSON, so a string comes back as "\"abc\"".
        val session = DuticLogin.buildSession(
            cookieHeader = "MoodleSession=cookie123",
            sesskey = "\"quotedKey\"",
            currentUrl = dashboard,
            nowMillis = now
        )

        assertEquals("quotedKey", session?.sesskey)
    }

    /** Half a capture is not a session. */
    @Test
    fun `a missing sesskey produces no session`() {
        assertNull(
            DuticLogin.buildSession(
                cookieHeader = "MoodleSession=cookie123",
                sesskey = "",
                currentUrl = dashboard,
                nowMillis = now
            )
        )
    }

    @Test
    fun `a missing cookie produces no session`() {
        assertNull(
            DuticLogin.buildSession(
                cookieHeader = "_ga=GA1.2.99",
                sesskey = "KEY123",
                currentUrl = dashboard,
                nowMillis = now
            )
        )
    }
}
