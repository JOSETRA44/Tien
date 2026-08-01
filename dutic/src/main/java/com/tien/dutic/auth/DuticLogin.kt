package com.tien.dutic.auth

import com.tien.dutic.core.DuticConfig
import com.tien.dutic.core.DuticSession

/**
 * The login handshake, as pure functions.
 *
 * ### What replaces Playwright
 * The CLI drives a real Chrome via Playwright: it opens the aula virtual, lets
 * the user complete Google SSO, then reads the cookie and `sesskey` out of the
 * page. On Android the same three steps are a **WebView** — it is the only
 * component that can run Google's SSO, which refuses to work in a plain HTTP
 * client because it needs cookies, redirects and JavaScript.
 *
 * The browser part is unavoidably UI. Everything *around* it — deciding when the
 * login finished, pulling the cookie apart, building the session — lives here as
 * functions with no Android dependency, so the tricky bits are unit-testable and
 * the WebView is left doing nothing but displaying a page.
 */
object DuticLogin {

    /**
     * Reads Moodle's own config object for the CSRF token.
     *
     * `M.cfg.sesskey` is what every page on a Moodle site exposes to its own
     * JavaScript, and it is exactly what Playwright reads in the CLI. Scraping
     * it out of the HTML instead would break on the next theme change.
     */
    const val SESSKEY_EXTRACTION_JS: String =
        "(function(){try{return (window.M && M.cfg && M.cfg.sesskey) || '';}" +
            "catch(e){return '';}})();"

    /**
     * True once the WebView has landed somewhere that means "signed in".
     *
     * The dashboard is the signal the CLI uses too. Checking for the *absence*
     * of the login page instead would fire on Google's own intermediate
     * redirects, capturing a cookie that is not yet a Moodle session.
     */
    fun isSignedInUrl(url: String): Boolean {
        if (!DuticConfig.isAulaUrl(url)) return false
        val path = url.substringAfter(DuticConfig.HOST, "")
        return path.contains("/my") || path.contains("/course/view.php")
    }

    /** True while the WebView is still somewhere in the sign-in flow. */
    fun isLoginUrl(url: String): Boolean = url.contains("/login/index.php")

    /**
     * Pulls `MoodleSession` out of a `document.cookie`-style header.
     *
     * Android's CookieManager hands back every cookie for the host in one
     * semicolon-separated string, and the aula virtual sets several.
     */
    fun parseMoodleSessionCookie(cookieHeader: String?): String? {
        if (cookieHeader.isNullOrBlank()) return null
        return cookieHeader
            .split(';')
            .asSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("$MOODLE_COOKIE_NAME=") }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Assembles a session from what the WebView captured, or null when a piece
     * is missing — which means the flow finished early and must not be treated
     * as success.
     */
    fun buildSession(
        cookieHeader: String?,
        sesskey: String?,
        currentUrl: String,
        nowMillis: Long
    ): DuticSession? {
        val cookie = parseMoodleSessionCookie(cookieHeader) ?: return null
        val key = sesskey?.trim()?.trim('"')?.takeIf { it.isNotBlank() } ?: return null

        val siteUrl = DuticConfig.deriveSiteUrl(
            dashboardUrl = currentUrl,
            fallback = DuticConfig.siteUrl()
        )

        return DuticSession(
            moodleSession = cookie,
            sesskey = key,
            siteUrl = siteUrl,
            capturedAt = nowMillis
        )
    }

    private const val MOODLE_COOKIE_NAME = "MoodleSession"
}

/**
 * What the WebView hands back when the flow completes.
 *
 * Kept as a separate type from [DuticSession] because these are *raw
 * observations* — any of them may be blank — while a session is something that
 * has already been validated into existence.
 */
data class LoginCapture(
    val cookieHeader: String?,
    val sesskey: String?,
    val currentUrl: String
)
