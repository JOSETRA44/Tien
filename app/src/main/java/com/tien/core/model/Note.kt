package com.tien.core.model

/**
 * Kotlin mirror of the C++ `tien::core::Note` struct.
 * All fields map 1-to-1 with the JSON produced by NativeLib.getNotes().
 *
 * JSON shape: {"id":1,"title":"…","content":"…","timestamp":1234567890}
 */
data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val timestamp: Long          // Unix epoch seconds
)
