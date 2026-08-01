package com.tien.core.data.mapper

import android.util.Log
import com.tien.core.core.result.AppResult
import com.tien.core.core.result.DataError
import com.tien.core.domain.model.BoardLink
import com.tien.core.domain.model.BoardNote
import com.tien.core.domain.model.Note
import com.tien.core.domain.model.PaperColor
import com.tien.core.domain.model.Priority
import com.tien.core.domain.model.Task
import org.json.JSONArray
import org.json.JSONObject

/**
 * Translates the native layer's JSON envelope into domain models.
 *
 * This is the only place in the app that knows the wire format. Keeping
 * `org.json` confined here means switching the transport (to protobuf, to a
 * direct object array over JNI, to kotlinx-serialization) touches one file.
 *
 * Envelope shape:
 * ```json
 * {"ok":true, "data":[ … ]}
 * {"ok":false,"error":"disk I/O error"}
 * ```
 */
internal object NativePayloadMapper {

    private const val TAG = "NativePayloadMapper"

    /**
     * Decodes a UTF-8 envelope and maps each element with [element].
     *
     * A null payload, malformed JSON, or `ok:false` all produce a
     * [AppResult.Failure] — never a silently empty list, which is how the old
     * bridge reported hard database errors.
     */
    fun <T> decodeList(
        payload: ByteArray?,
        element: (JSONObject) -> T
    ): AppResult<List<T>> {
        if (payload == null) {
            Log.e(TAG, "Native layer returned a null payload")
            return AppResult.Failure(DataError.Unavailable)
        }

        return try {
            val envelope = JSONObject(payload.toString(Charsets.UTF_8))

            if (!envelope.optBoolean("ok", false)) {
                val message = envelope.optString("error", "unknown native error")
                Log.e(TAG, "Native query failed: $message")
                return AppResult.Failure(DataError.Unknown(message))
            }

            val array: JSONArray = envelope.optJSONArray("data") ?: JSONArray()
            val items = ArrayList<T>(array.length())
            for (index in 0 until array.length()) {
                items.add(element(array.getJSONObject(index)))
            }
            AppResult.Success(items)
        } catch (error: Exception) {
            // A parse failure means this build and the .so disagree on the wire
            // format — a real defect, not a transient condition.
            Log.e(TAG, "Malformed native payload", error)
            AppResult.Failure(DataError.Corrupted)
        }
    }

    // ── Element mappers ───────────────────────────────────────────────────────

    fun toNote(json: JSONObject): Note = Note(
        id = json.getLong("id"),
        title = json.optString("title", ""),
        content = json.optString("content", ""),
        createdAt = json.optLong("createdAt", 0L),
        updatedAt = json.optLong("updatedAt", 0L),
        pinned = json.optBoolean("pinned", false)
    )

    fun toBoardNote(json: JSONObject): BoardNote = BoardNote(
        id = json.getLong("id"),
        boardId = json.optLong("boardId", 0L),
        text = json.optString("text", ""),
        // The wire carries doubles because SQLite stores REAL; Compose works in
        // Float, so the narrowing happens here, once, at the boundary.
        x = json.optDouble("x", 0.0).toFloat(),
        y = json.optDouble("y", 0.0).toFloat(),
        width = json.optDouble("width", BoardNote.DEFAULT_SIZE.toDouble()).toFloat(),
        height = json.optDouble("height", BoardNote.DEFAULT_SIZE.toDouble()).toFloat(),
        rotation = json.optDouble("rotation", 0.0).toFloat(),
        color = PaperColor.fromNative(json.optInt("colorIndex", PaperColor.DEFAULT.nativeValue)),
        z = json.optInt("z", 0),
        // SQL NULL arrives as 0 through the envelope; both mean "standalone".
        sourceNoteId = json.optLong("sourceNoteId", 0L).takeIf { it > 0L },
        createdAt = json.optLong("createdAt", 0L),
        updatedAt = json.optLong("updatedAt", 0L)
    )

    fun toBoardLink(json: JSONObject): BoardLink = BoardLink(
        id = json.getLong("id"),
        boardId = json.optLong("boardId", 0L),
        fromNoteId = json.getLong("fromNoteId"),
        toNoteId = json.getLong("toNoteId"),
        createdAt = json.optLong("createdAt", 0L)
    )

    fun toTask(json: JSONObject): Task = Task(
        id = json.getLong("id"),
        title = json.optString("title", ""),
        details = json.optString("details", ""),
        dueAt = json.optLong("dueAt", 0L),
        createdAt = json.optLong("createdAt", 0L),
        updatedAt = json.optLong("updatedAt", 0L),
        // fromNative falls back to MEDIUM instead of throwing, so a value written
        // by a newer build cannot crash an older one.
        priority = Priority.fromNative(json.optInt("priority", Priority.DEFAULT.nativeValue)),
        isDone = json.optBoolean("isDone", false)
    )
}
