package com.tien.dutic.core

/**
 * A captured Moodle session. Two artefacts do all the work:
 *
 *  - [moodleSession] — the `MoodleSession` cookie value. Authentication.
 *  - [sesskey] — the CSRF token Moodle demands on every AJAX call.
 *
 * [siteUrl] is derived from the dashboard URL after login, so it carries the
 * real academic term rather than the build-time guess.
 */
data class DuticSession(
    val moodleSession: String,
    val sesskey: String,
    val siteUrl: String,
    /** Epoch millis at capture. */
    val capturedAt: Long
) {
    /**
     * Usable if it has both artefacts. **Age is deliberately not a factor.**
     *
     * The CLI learned this the hard way: gating on a TTL caused re-logins while
     * the session was still perfectly alive. The server is the only authority —
     * we try the session, and renew only when Moodle actually rejects it.
     */
    val isUsable: Boolean
        get() = moodleSession.isNotBlank() && sesskey.isNotBlank() && siteUrl.isNotBlank()

    /** Informational only: drives "signed in 3 h ago" copy, never a refresh. */
    fun ageMillis(nowMillis: Long): Long = nowMillis - capturedAt

    /** The academic term embedded in the site URL, e.g. "2026A". */
    val semester: String
        get() = siteUrl.substringAfterLast('/', missingDelimiterValue = "")

    companion object {
        /**
         * Soft staleness threshold, used only to *label* a session in the UI.
         * Ten hours matches the CLI's informational TTL.
         */
        const val SOFT_TTL_MILLIS: Long = 10L * 60 * 60 * 1000
    }
}
