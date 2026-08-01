package com.tien.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.tien.core.core.time.DateTimeLabels
import com.tien.core.core.time.SystemTienClock
import com.tien.core.core.time.TienClock
import com.tien.core.data.nativedb.NativeConnection
import com.tien.core.data.preferences.DataStorePreferencesRepository
import com.tien.core.data.repository.BoardRepositoryImpl
import com.tien.core.data.repository.NoteRepositoryImpl
import com.tien.core.data.repository.TaskRepositoryImpl
import com.tien.core.domain.repository.BoardRepository
import com.tien.core.domain.repository.NoteRepository
import com.tien.core.domain.repository.PreferencesRepository
import com.tien.core.domain.repository.TaskRepository
import com.tien.dutic.DuticClient
import com.tien.dutic.di.DuticContainer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Object graph for the app.
 *
 * Deliberately hand-rolled rather than Hilt: the graph is small, and an
 * interface with a swappable implementation gives tests the same seam a DI
 * framework would, without the annotation processor in the build.
 * Should the graph outgrow this, the call sites already depend on the
 * interface, so introducing Hilt would not touch them.
 */
interface AppContainer {
    val clock: TienClock
    val dateTimeLabels: DateTimeLabels
    val noteRepository: NoteRepository
    val taskRepository: TaskRepository
    val boardRepository: BoardRepository
    val preferencesRepository: PreferencesRepository

    /**
     * Client for the UNSA aula virtual.
     *
     * Typed as the module's facade, never as its internals: :app cannot reach
     * the repositories or the HTTP client behind it even if it wanted to.
     */
    val duticClient: DuticClient

    /** Releases process-wide resources — currently the native connection. */
    fun shutdown()
}

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tien_preferences"
)

class DefaultAppContainer(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AppContainer {

    override val clock: TienClock = SystemTienClock()

    override val dateTimeLabels: DateTimeLabels by lazy { DateTimeLabels(clock) }

    /**
     * One connection for the whole process, opened lazily on first query.
     *
     * This single object is what replaces the previous "open the database file,
     * run the schema check, then close it" cycle that ran on every read and
     * every write.
     */
    private val nativeConnectionLazy = lazy {
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        // `databases/` is created by SQLiteOpenHelper, which this app does not
        // use. Raw sqlite3_open() will not create missing parent directories,
        // so on a clean install the very first open would fail.
        databaseFile.parentFile?.mkdirs()
        NativeConnection(databaseFile.absolutePath)
    }

    private val nativeConnection: NativeConnection by nativeConnectionLazy

    override val noteRepository: NoteRepository by lazy {
        NoteRepositoryImpl(nativeConnection, ioDispatcher)
    }

    override val taskRepository: TaskRepository by lazy {
        TaskRepositoryImpl(nativeConnection, ioDispatcher)
    }

    override val boardRepository: BoardRepository by lazy {
        BoardRepositoryImpl(nativeConnection, ioDispatcher)
    }

    override val preferencesRepository: PreferencesRepository by lazy {
        DataStorePreferencesRepository(context.preferencesDataStore)
    }

    // Built lazily: a student who never opens the aula virtual section never
    // pays for an OkHttp instance or a DataStore file.
    private val duticContainer by lazy { DuticContainer(context, ioDispatcher) }

    override val duticClient: DuticClient by lazy { duticContainer.client }

    override fun shutdown() {
        // Guarded so shutting down before anything touched the database does
        // not open a connection purely in order to close it.
        if (nativeConnectionLazy.isInitialized()) {
            nativeConnection.close()
        }
    }

    private companion object {
        const val DATABASE_NAME = "tien_notes.db"
    }
}
