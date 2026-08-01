package com.tien.core.data.repository

import com.tien.core.core.result.AppResult
import com.tien.core.core.result.DataError
import com.tien.core.data.mapper.NativePayloadMapper
import com.tien.core.data.nativedb.NativeConnection
import com.tien.core.data.nativedb.NativeDatabase
import com.tien.core.domain.model.BoardNote
import com.tien.core.domain.model.PaperColor
import com.tien.core.domain.repository.BoardContent
import com.tien.core.domain.repository.BoardRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * SQLite-backed [BoardRepository].
 *
 * Shares the revision-counter invalidation model with the other repositories:
 * a write bumps a counter, and every active stream re-queries once.
 */
internal class BoardRepositoryImpl(
    private val connection: NativeConnection,
    private val dispatcher: CoroutineDispatcher
) : BoardRepository {

    private val revision = MutableStateFlow(0L)

    override fun observeBoard(boardId: Long): Flow<AppResult<BoardContent>> =
        revision
            .map { fetchBoard(boardId) }
            .flowOn(dispatcher)

    private fun fetchBoard(boardId: Long): AppResult<BoardContent> {
        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return unavailable()

        // Notes and links are read back-to-back on the same dispatcher, so the
        // pair a frame renders can never be half a revision apart.
        val notesPayload = NativeDatabase.nativeQueryBoardNotes(handle, boardId)
        val notes = NativePayloadMapper.decodeList(notesPayload, NativePayloadMapper::toBoardNote)
        if (notes is AppResult.Failure) return notes

        val linksPayload = NativeDatabase.nativeQueryBoardLinks(handle, boardId)
        val links = NativePayloadMapper.decodeList(linksPayload, NativePayloadMapper::toBoardLink)
        if (links is AppResult.Failure) return links

        return AppResult.Success(
            BoardContent(
                notes = (notes as AppResult.Success).data,
                links = (links as AppResult.Success).data
            )
        )
    }

    override suspend fun pinNote(
        boardId: Long,
        text: String,
        x: Float,
        y: Float,
        rotation: Float,
        color: PaperColor
    ): AppResult<BoardNote> = withContext(dispatcher) {
        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

        val code = NativeDatabase.nativeInsertBoardNote(
            handle = handle,
            boardId = boardId,
            text = text.toByteArray(Charsets.UTF_8),
            x = x.toDouble(),
            y = y.toDouble(),
            rotation = rotation.toDouble(),
            colorIndex = color.nativeValue,
            sourceNoteId = 0L
        )

        when (val inserted = connection.toResult(code)) {
            is AppResult.Failure -> inserted
            is AppResult.Success -> {
                invalidate()
                // Re-read so the caller receives the z-index and timestamps the
                // database assigned, rather than a client-side guess.
                findNote(boardId, inserted.data)
            }
        }
    }

    override suspend fun updateText(id: Long, text: String): AppResult<Unit> =
        withContext(dispatcher) {
            val handle = connection.handle()
            if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

            val code = NativeDatabase.nativeUpdateBoardNoteText(
                handle, id, text.toByteArray(Charsets.UTF_8)
            )
            connection.toUnitResult(code).also { if (it.isSuccess) invalidate() }
        }

    override suspend fun updateTransform(
        id: Long,
        x: Float,
        y: Float,
        rotation: Float
    ): AppResult<Unit> = withContext(dispatcher) {
        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

        val code = NativeDatabase.nativeUpdateBoardNoteTransform(
            handle, id, x.toDouble(), y.toDouble(), rotation.toDouble()
        )
        connection.toUnitResult(code).also { if (it.isSuccess) invalidate() }
    }

    override suspend fun updateSize(id: Long, width: Float, height: Float): AppResult<Unit> =
        withContext(dispatcher) {
            val handle = connection.handle()
            if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

            val code = NativeDatabase.nativeUpdateBoardNoteSize(
                handle, id, width.toDouble(), height.toDouble()
            )
            connection.toUnitResult(code).also { if (it.isSuccess) invalidate() }
        }

    override suspend fun updateColor(id: Long, color: PaperColor): AppResult<Unit> =
        withContext(dispatcher) {
            val handle = connection.handle()
            if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

            val code = NativeDatabase.nativeUpdateBoardNoteColor(handle, id, color.nativeValue)
            connection.toUnitResult(code).also { if (it.isSuccess) invalidate() }
        }

    override suspend fun raise(id: Long): AppResult<Unit> = withContext(dispatcher) {
        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

        val code = NativeDatabase.nativeRaiseBoardNote(handle, id)
        if (code < 0) return@withContext connection.toUnitResult(code)

        // Zero rows means the paper was already on top. That is success, and it
        // must not invalidate — re-querying on every tap of the front-most note
        // would redraw the whole board for nothing.
        if (code > 0) invalidate()
        AppResult.Success(Unit)
    }

    override suspend fun removeNote(boardId: Long, id: Long): AppResult<BoardNote> =
        withContext(dispatcher) {
            val handle = connection.handle()
            if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

            // Snapshot before deleting: the row is the only record of where the
            // paper was pinned, and undo has to put it back in the same spot.
            val current = fetchBoard(boardId)
            val snapshot = when (current) {
                is AppResult.Failure -> return@withContext current
                is AppResult.Success -> current.data.notes.firstOrNull { it.id == id }
            } ?: return@withContext AppResult.Failure(DataError.NotFound)

            val code = NativeDatabase.nativeDeleteBoardNote(handle, id)
            when (val deleted = connection.toUnitResult(code)) {
                is AppResult.Failure -> deleted
                is AppResult.Success -> {
                    invalidate()
                    AppResult.Success(snapshot)
                }
            }
        }

    override suspend fun restoreNote(note: BoardNote): AppResult<Unit> = withContext(dispatcher) {
        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

        val code = NativeDatabase.nativeRestoreBoardNote(
            handle = handle,
            id = note.id,
            boardId = note.boardId,
            text = note.text.toByteArray(Charsets.UTF_8),
            x = note.x.toDouble(),
            y = note.y.toDouble(),
            width = note.width.toDouble(),
            height = note.height.toDouble(),
            rotation = note.rotation.toDouble(),
            colorIndex = note.color.nativeValue,
            z = note.z,
            sourceNoteId = note.sourceNoteId ?: 0L,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt
        )
        connection.toUnitResult(code).also { if (it.isSuccess) invalidate() }
    }

    override suspend fun link(
        boardId: Long,
        fromNoteId: Long,
        toNoteId: Long
    ): AppResult<Unit> = withContext(dispatcher) {
        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

        val code = NativeDatabase.nativeInsertBoardLink(handle, boardId, fromNoteId, toNoteId)
        if (code < 0) return@withContext connection.toUnitResult(code)

        // 0 means the thread already existed. Tying two papers that are already
        // tied is a no-op, not an error.
        if (code > 0) invalidate()
        AppResult.Success(Unit)
    }

    override suspend fun unlink(fromNoteId: Long, toNoteId: Long): AppResult<Unit> =
        withContext(dispatcher) {
            val handle = connection.handle()
            if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

            val code = NativeDatabase.nativeDeleteBoardLink(handle, fromNoteId, toNoteId)
            connection.toUnitResult(code).also { if (it.isSuccess) invalidate() }
        }

    private fun findNote(boardId: Long, id: Long): AppResult<BoardNote> =
        when (val content = fetchBoard(boardId)) {
            is AppResult.Failure -> content
            is AppResult.Success ->
                content.data.notes.firstOrNull { it.id == id }
                    ?.let { AppResult.Success(it) }
                    ?: AppResult.Failure(DataError.NotFound)
        }

    private fun invalidate() {
        revision.update { it + 1 }
    }
}
