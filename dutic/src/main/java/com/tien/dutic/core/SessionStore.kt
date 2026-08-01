package com.tien.dutic.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Persists the captured session across launches.
 *
 * The CLI keeps this in `~/.dutic/session.json` with 0600 permissions. On
 * Android the equivalent is app-private storage, which the OS already isolates
 * from other apps — so DataStore in the app's own directory is the right shape.
 *
 * What is stored is a session cookie, not a password: the user's UNSA
 * credentials never pass through this app. Google's SSO handles them inside the
 * WebView and hands back only the resulting Moodle cookie.
 */
interface SessionStore {
    val session: Flow<DuticSession?>
    suspend fun read(): DuticSession?
    suspend fun save(session: DuticSession)
    suspend fun clear()
}

private val Context.duticDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dutic_session"
)

internal class DataStoreSessionStore(
    private val context: Context
) : SessionStore {

    override val session: Flow<DuticSession?> = context.duticDataStore.data
        .catch { throwable ->
            // A corrupt store must not take the app down; the user simply
            // appears signed out and can sign in again.
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs ->
            val cookie = prefs[Keys.COOKIE].orEmpty()
            val sesskey = prefs[Keys.SESSKEY].orEmpty()
            val siteUrl = prefs[Keys.SITE_URL].orEmpty()
            if (cookie.isBlank() || sesskey.isBlank() || siteUrl.isBlank()) {
                null
            } else {
                DuticSession(
                    moodleSession = cookie,
                    sesskey = sesskey,
                    siteUrl = siteUrl,
                    capturedAt = prefs[Keys.CAPTURED_AT] ?: 0L
                )
            }
        }

    override suspend fun read(): DuticSession? = session.first()

    override suspend fun save(session: DuticSession) {
        context.duticDataStore.edit { prefs ->
            prefs[Keys.COOKIE] = session.moodleSession
            prefs[Keys.SESSKEY] = session.sesskey
            prefs[Keys.SITE_URL] = session.siteUrl
            prefs[Keys.CAPTURED_AT] = session.capturedAt
        }
    }

    override suspend fun clear() {
        context.duticDataStore.edit { it.clear() }
    }

    private object Keys {
        val COOKIE = stringPreferencesKey("moodle_session")
        val SESSKEY = stringPreferencesKey("sesskey")
        val SITE_URL = stringPreferencesKey("site_url")
        val CAPTURED_AT = longPreferencesKey("captured_at")
    }
}
