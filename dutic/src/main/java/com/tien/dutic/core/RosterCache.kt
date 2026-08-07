package com.tien.dutic.core

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tien.dutic.domain.model.Participant
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Stores a course's participant list across launches.
 *
 * ### Why this is not [TtlCache]
 * The in-memory cache lives two minutes and dies with the process, which is the
 * right shape for tasks and grades — those change while the student is looking
 * at them.
 *
 * A class roster does not. The people enrolled in a course change at the start
 * of a semester and then stay put for months, and reading one is the most
 * expensive call in the module: a full participant table, and for a whole-account
 * person search, one per enrolled course. Re-fetching that every time the app
 * restarts spends a lot for an answer that is almost never different.
 *
 * So it is written to disk with a week-long life, and the student can always
 * force a fresh read. Stale-by-a-few-days here means a classmate who enrolled
 * late appears late — a far smaller cost than a slow screen every single time.
 */
interface RosterCache {
    suspend fun read(siteUrl: String, courseId: Long): List<Participant>?
    suspend fun write(siteUrl: String, courseId: Long, participants: List<Participant>)

    /** Drops one course's roster, or every stored roster when [courseId] is null. */
    suspend fun clear(siteUrl: String, courseId: Long? = null)
}

private val Context.rosterDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dutic_rosters"
)

internal class DataStoreRosterCache(
    private val context: Context,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val now: () -> Long = System::currentTimeMillis
) : RosterCache {

    override suspend fun read(siteUrl: String, courseId: Long): List<Participant>? {
        val raw = preferences()[keyFor(siteUrl, courseId)] ?: return null

        return runCatching {
            val envelope = JSONObject(raw)
            val storedAt = envelope.optLong(FIELD_STORED_AT)

            // Expiry is checked on read rather than swept on a timer: there is
            // no background work here, and a roster nobody opens costs nothing.
            if (now() - storedAt >= ttlMillis) return null

            val array = envelope.optJSONArray(FIELD_PEOPLE) ?: return null
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.toParticipant()
            }
        }.getOrElse { error ->
            // A roster written by an older build is not worth crashing over;
            // treating it as absent just means one more fetch.
            Log.w(TAG, "Roster ilegible para el curso $courseId", error)
            null
        }
    }

    override suspend fun write(
        siteUrl: String,
        courseId: Long,
        participants: List<Participant>
    ) {
        // An empty roster is never stored. It usually means the request was
        // filtered or failed, and caching that for a week would hide the real
        // list until the cache expired.
        if (participants.isEmpty()) return

        val payload = JSONObject()
            .put(FIELD_STORED_AT, now())
            .put(
                FIELD_PEOPLE,
                JSONArray().apply { participants.forEach { put(it.toJson()) } }
            )
            .toString()

        edit { prefs -> prefs[keyFor(siteUrl, courseId)] = payload }
    }

    override suspend fun clear(siteUrl: String, courseId: Long?) {
        edit { prefs ->
            if (courseId != null) {
                prefs.remove(keyFor(siteUrl, courseId))
            } else {
                val prefix = "$KEY_PREFIX$siteUrl"
                prefs.asMap().keys
                    .filter { it.name.startsWith(prefix) }
                    .forEach { prefs.remove(stringPreferencesKey(it.name)) }
            }
        }
    }

    private suspend fun preferences(): Preferences = context.rosterDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .first()

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        runCatching { context.rosterDataStore.edit(block) }
            .onFailure { Log.w(TAG, "No se pudo escribir el roster", it) }
    }

    private fun keyFor(siteUrl: String, courseId: Long) =
        stringPreferencesKey("$KEY_PREFIX$siteUrl:$courseId")

    private fun Participant.toJson(): JSONObject = JSONObject()
        .put("userId", userId)
        .put("fullName", fullName)
        .put("email", email ?: JSONObject.NULL)
        .put("roles", JSONArray().apply { roles.forEach { put(it) } })
        .put("profileImageUrl", profileImageUrl ?: JSONObject.NULL)
        .put("lastAccess", lastAccess ?: JSONObject.NULL)

    private fun JSONObject.toParticipant(): Participant? {
        val userId = optLong("userId").takeIf { it > 0 } ?: return null
        val rolesArray = optJSONArray("roles") ?: JSONArray()

        return Participant(
            userId = userId,
            fullName = optString("fullName"),
            email = optString("email").takeIf { it.isNotBlank() && it != "null" },
            roles = (0 until rolesArray.length()).mapNotNull {
                rolesArray.optString(it).takeIf { role -> role.isNotBlank() }
            },
            profileImageUrl = optString("profileImageUrl")
                .takeIf { it.isNotBlank() && it != "null" },
            lastAccess = optString("lastAccess").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private companion object {
        const val TAG = "RosterCache"
        const val KEY_PREFIX = "roster:"
        const val FIELD_STORED_AT = "storedAt"
        const val FIELD_PEOPLE = "people"

        /**
         * A week. Long enough that a roster is fetched roughly once per course
         * per term, short enough that a late enrolment shows up without the
         * student ever thinking about it.
         */
        const val DEFAULT_TTL_MILLIS: Long = 7L * 24 * 60 * 60 * 1000
    }
}
