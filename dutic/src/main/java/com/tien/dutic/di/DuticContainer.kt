package com.tien.dutic.di

import android.content.Context
import com.tien.dutic.DuticClient
import com.tien.dutic.auth.DuticAuthenticator
import com.tien.dutic.core.DataStoreSessionStore
import com.tien.dutic.core.DataStoreRosterCache
import com.tien.dutic.core.MoodleClient
import com.tien.dutic.core.MoodleHttp
import com.tien.dutic.core.RosterCache
import com.tien.dutic.core.SessionStore
import com.tien.dutic.core.TtlCache
import com.tien.dutic.domain.repository.CoursesRepository
import com.tien.dutic.domain.repository.GradesRepository
import com.tien.dutic.domain.repository.PeopleRepository
import com.tien.dutic.domain.repository.TasksRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Builds the module's object graph.
 *
 * The consuming app should not have to know that a DUTIC client is an OkHttp
 * instance plus five repositories plus a cache — it asks for a [DuticClient] and
 * gets one. Everything wired here is `internal`, so nothing but this file can
 * assemble the graph differently and end up with, say, two caches that disagree.
 */
class DuticContainer(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val appContext = context.applicationContext

    private val sessionStore: SessionStore by lazy { DataStoreSessionStore(appContext) }

    /**
     * One OkHttp instance for the module. OkHttp is designed to be shared —
     * creating one per call would throw away the connection pool and force a new
     * TLS handshake on every request, which on mobile is the expensive part.
     */
    private val httpClient by lazy { MoodleHttp.createClient() }

    private val moodleClient by lazy { MoodleClient(httpClient, ioDispatcher) }

    /**
     * One cache shared by every repository, so a course state fetched by the
     * task sweep is not fetched again by the contents screen a second later.
     */
    private val cache by lazy { TtlCache() }

    /**
     * Survives restarts, unlike [cache]. A class roster changes once a semester,
     * so re-fetching it on every launch spends the module's most expensive call
     * on an answer that is almost never different.
     */
    private val rosterCache: RosterCache by lazy { DataStoreRosterCache(appContext) }

    private val authenticator by lazy { DuticAuthenticator(sessionStore) }

    private val coursesRepository by lazy { CoursesRepository(moodleClient, cache) }

    private val tasksRepository by lazy { TasksRepository(moodleClient, coursesRepository) }

    private val gradesRepository by lazy {
        GradesRepository(moodleClient, coursesRepository, cache)
    }

    private val peopleRepository by lazy {
        PeopleRepository(moodleClient, coursesRepository, cache, rosterCache)
    }

    /** The module's single entry point. */
    val client: DuticClient by lazy {
        DuticClient(
            authenticator = authenticator,
            moodleClient = moodleClient,
            courses = coursesRepository,
            tasks = tasksRepository,
            grades = gradesRepository,
            people = peopleRepository
        )
    }

    /** Drops every cached read, so the next call goes to the network. */
    suspend fun invalidateCaches() {
        cache.invalidate()
    }
}
