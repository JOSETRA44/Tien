package com.tien.dutic.core

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * The HTTP client used for every call to the aula virtual.
 *
 * ### A deliberate divergence from the CLI
 * The Node client sets `rejectUnauthorized: false`, disabling TLS verification
 * outright, because Node's trust store does not carry UNSA's CA. That is not
 * carried over here. Turning verification off in a mobile app is a real
 * vulnerability — it makes every request forgeable on any network the phone
 * joins, which for a student is usually campus or café Wi-Fi.
 *
 * Android's trust store is as broad as Chrome's, so ordinary verification is
 * expected to succeed where Node's failed. If a particular device still cannot
 * validate the chain, the fix is `network_security_config.xml`, scoped to this
 * one host — see the module's res/xml. That keeps the exception narrow and
 * auditable instead of global and invisible.
 */
internal object MoodleHttp {

    private const val CONNECT_TIMEOUT_SECONDS = 20L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val CALL_TIMEOUT_SECONDS = 45L

    fun createClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // A hard ceiling on the whole call. Without it a hung request stalls a
        // course sweep indefinitely, which is exactly what the CLI's
        // AbortController guards against.
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
}

/** Applies the headers Moodle expects from a signed-in browser. */
internal fun Request.Builder.withMoodleSession(session: DuticSession): Request.Builder = apply {
    header("Cookie", "MoodleSession=${session.moodleSession}")
    header("User-Agent", DuticConfig.CHROME_USER_AGENT)
    header("Origin", session.siteUrl.substringBeforeLast('/', session.siteUrl))
    header("Referer", session.siteUrl)
}

/**
 * Detects the several shapes a dead session takes.
 *
 * Moodle does not answer a stale cookie with a clean 401. Depending on the
 * endpoint it redirects to the login page, returns 303, or serves the login
 * HTML with a 200 — so the check has to look at the status *and* the body.
 */
internal fun Response.looksLikeLoginWall(): Boolean {
    if (code == HTTP_SEE_OTHER || code == HTTP_FOUND || code == HTTP_UNAUTHORIZED) return true
    val finalUrl = request.url.toString()
    return finalUrl.contains("/login/index.php")
}

internal fun String.isLoginHtml(): Boolean =
    contains("login/index.php") || contains("loginform", ignoreCase = true)

private const val HTTP_FOUND = 302
private const val HTTP_SEE_OTHER = 303
private const val HTTP_UNAUTHORIZED = 401
