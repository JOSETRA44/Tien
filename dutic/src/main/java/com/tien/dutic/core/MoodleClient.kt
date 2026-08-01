package com.tien.dutic.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Speaks Moodle's internal AJAX protocol — the same one the web UI uses.
 *
 * Mirrors `src/core/moodleClient.ts`. Everything of value comes through
 * `{siteUrl}/lib/ajax/service.php?sesskey=…`: a batch of `{index, methodname,
 * args}` objects in, an array of `{error, data, exception}` out, in the same
 * order. Batching matters — a course sweep issues one call per course, and
 * sending them together is the difference between one round trip and thirty.
 *
 * On this Moodle install `core_course_get_contents` is blocked but
 * `core_courseformat_get_state` is not, which is the whole reason hidden
 * assignments are discoverable at all.
 */
internal class MoodleClient(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher
) {

    /** One AJAX call: a Moodle web-service method and its arguments. */
    data class Call(val methodName: String, val args: JSONObject)

    /**
     * Runs [calls] as one batch.
     *
     * @return each call's `data`, in the order given.
     * @throws SessionExpiredException when Moodle rejects the session.
     * @throws MoodleApiException on an application-level error (never retried —
     *   a rejected argument stays rejected).
     */
    suspend fun postBatch(session: DuticSession, calls: List<Call>): List<Any?> =
        withContext(ioDispatcher) {
            require(calls.isNotEmpty()) { "postBatch called with no calls" }

            val payload = JSONArray().apply {
                calls.forEachIndexed { index, call ->
                    put(
                        JSONObject()
                            .put("index", index)
                            .put("methodname", call.methodName)
                            .put("args", call.args)
                    )
                }
            }.toString()

            val request = Request.Builder()
                .url(DuticConfig.ajaxUrl(session.siteUrl, session.sesskey))
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .withMoodleSession(session)
                .header("Content-Type", "application/json")
                .build()

            var lastNetworkFailure: Exception? = null

            // Attempt 0 plus one per backoff step.
            for (attempt in 0..RETRY_DELAYS_MILLIS.size) {
                if (attempt > 0) delay(RETRY_DELAYS_MILLIS[attempt - 1])

                try {
                    client.newCall(request).execute().use { response ->
                        if (response.looksLikeLoginWall()) throw SessionExpiredException()
                        if (!response.isSuccessful) {
                            throw MoodleApiException("HTTP ${response.code}")
                        }

                        val body = response.body?.string().orEmpty()
                        return@withContext parseBatchResponse(body)
                    }
                } catch (expired: SessionExpiredException) {
                    throw expired
                } catch (api: MoodleApiException) {
                    throw api
                } catch (io: IOException) {
                    // Timeouts and connection resets are transient; keep the
                    // last one so the final error says what actually happened.
                    lastNetworkFailure = io
                }
            }

            throw IOException(
                "Fallo de red tras ${RETRY_DELAYS_MILLIS.size + 1} intentos: " +
                    (lastNetworkFailure?.message ?: "causa desconocida")
            )
        }

    /** Convenience for a single call; returns its `data` directly. */
    suspend fun post(session: DuticSession, methodName: String, args: JSONObject): Any? =
        postBatch(session, listOf(Call(methodName, args))).first()

    /**
     * Fetches a page as HTML, for the parts of Moodle with no AJAX equivalent —
     * grades and user profiles are rendered server-side only.
     */
    suspend fun getHtml(session: DuticSession, url: String): String = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(url)
            .get()
            .withMoodleSession(session)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.looksLikeLoginWall()) throw SessionExpiredException()
            if (!response.isSuccessful) throw MoodleApiException("HTTP ${response.code}")

            val body = response.body?.string().orEmpty()
            // A 200 carrying the login form is the sneakiest expiry shape.
            if (body.isLoginHtml()) throw SessionExpiredException()
            body
        }
    }

    private fun parseBatchResponse(body: String): List<Any?> {
        val decoded = try {
            JSONArray(body)
        } catch (_: Exception) {
            // Moodle serves the login page as HTML when the cookie is dead.
            if (body.isLoginHtml()) throw SessionExpiredException()
            throw MoodleApiException("La respuesta del servidor no es JSON")
        }

        return (0 until decoded.length()).map { index ->
            val entry = decoded.optJSONObject(index)
                ?: throw MoodleApiException("Respuesta inesperada en la posición $index")

            if (entry.optBoolean("error", false)) {
                val exception = entry.optJSONObject("exception")
                val code = exception?.optString("errorcode").orEmpty()
                val message = exception?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Error desconocido de Moodle"

                if (code in SESSION_ERROR_CODES) throw SessionExpiredException()
                throw MoodleApiException(message, code.takeIf { it.isNotBlank() })
            }

            // `data` may be an object, an array, or — for
            // core_courseformat_get_state — a JSON *string* that still needs
            // parsing. Callers handle that; this only unwraps the envelope.
            entry.opt("data")
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Backoff between retries of transient network failures. */
        val RETRY_DELAYS_MILLIS = longArrayOf(800, 1_600)

        val SESSION_ERROR_CODES = setOf("requireloginerror", "servicerequireslogin")
    }
}
