package com.tien.dutic.core

import com.tien.dutic.BuildConfig

/**
 * Where the aula virtual lives and how we must look while talking to it.
 *
 * Mirrors `src/core/config.ts` from the CLI. The term ("2026A") is part of the
 * URL and changes every academic period, so it is a *starting guess* only: the
 * real value is re-derived from the dashboard URL after login and stored in the
 * session, which means a stale default corrects itself on the next sign-in.
 */
object DuticConfig {

    const val HOST: String = BuildConfig.AULA_HOST

    val defaultSemester: String = BuildConfig.DEFAULT_SEMESTER

    /**
     * A desktop Chrome User-Agent, not the device's.
     *
     * Not cosmetic: Google's OAuth flow answers 403 to generic bot and WebView
     * User-Agents, so the login would fail before it started. The CLI sends the
     * same string for the same reason.
     */
    const val CHROME_USER_AGENT: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    fun siteUrl(semester: String = defaultSemester): String = "https://$HOST/$semester"

    /** The page carrying the "Ingresar con Correo UNSA" button. */
    fun loginUrl(semester: String = defaultSemester): String =
        "${siteUrl(semester)}/login/index.php"

    fun dashboardUrl(siteUrl: String): String = "$siteUrl/my/"

    /** Moodle's internal AJAX endpoint — the source of almost everything. */
    fun ajaxUrl(siteUrl: String, sesskey: String): String =
        "$siteUrl/lib/ajax/service.php?sesskey=$sesskey"

    fun gradesUrl(siteUrl: String, courseId: Long): String =
        "$siteUrl/grade/report/user/index.php?id=$courseId"

    fun courseUrl(siteUrl: String, courseId: Long): String =
        "$siteUrl/course/view.php?id=$courseId"

    fun participantsUrl(siteUrl: String, courseId: Long): String =
        "$siteUrl/user/index.php?id=$courseId"

    fun profileUrl(siteUrl: String, userId: Long, courseId: Long?): String =
        buildString {
            append("$siteUrl/user/profile.php?id=$userId")
            if (courseId != null) append("&course=$courseId")
        }

    fun assignUrl(siteUrl: String, cmid: Long): String =
        "$siteUrl/mod/assign/view.php?id=$cmid"

    fun isAulaUrl(url: String): Boolean = runCatching {
        java.net.URI(url).host == HOST
    }.getOrDefault(false)

    /**
     * Trims a dashboard URL back to the site root, which is how the real term is
     * discovered. `https://…/2026B/my/` → `https://…/2026B`.
     */
    fun deriveSiteUrl(dashboardUrl: String, fallback: String): String {
        val index = dashboardUrl.indexOf("/my")
        return if (index > 0) dashboardUrl.take(index) else fallback
    }
}
