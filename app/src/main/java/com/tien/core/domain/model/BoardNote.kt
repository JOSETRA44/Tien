package com.tien.core.domain.model

/**
 * A piece of paper pinned to a board.
 *
 * Mirrors `tien::core::BoardNote` in `cpp/core/Models.h`.
 *
 * Position, tilt and stacking order belong to the *paper*, not to the idea
 * written on it: the same sentence pinned twice is two papers, each with its own
 * spot on the wall.
 *
 * [x] and [y] are board coordinates in density-independent pixels, addressing
 * the note's top-left corner **before** [rotation] is applied.
 */
data class BoardNote(
    val id: Long,
    val boardId: Long,
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,

    /**
     * Degrees, persisted rather than randomised at render time.
     *
     * A paper that re-tilts itself on every redraw is unmistakably digital.
     * Pinned once, it stays at the angle it was pinned at.
     */
    val rotation: Float,

    val color: PaperColor,

    /** Stacking order; higher is nearer the viewer. */
    val z: Int,

    /** Non-null when this paper mirrors a note from the notes list. */
    val sourceNoteId: Long?,

    val createdAt: Long,
    val updatedAt: Long
) {
    val isBlank: Boolean get() = text.isBlank()

    /** Centre point, used to anchor the thread drawn between two papers. */
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f

    companion object {
        const val DEFAULT_SIZE = 180f

        /**
         * How far a paper may tilt, in degrees.
         *
         * Small on purpose. Past roughly six degrees it stops reading as "pinned
         * by hand in a hurry" and starts reading as broken layout.
         */
        const val MAX_TILT = 5f

        const val MIN_SIZE = 120f
        const val MAX_SIZE = 420f
    }
}

/**
 * The paper stock a note is written on.
 *
 * These are deliberately outside the app's urgency palette. Everywhere else in
 * Tien a warm colour means "this has a deadline"; on the board colour is just
 * which paper you grabbed, so the scale is desaturated and carries no ranking.
 * The ordinal is persisted, so entries must never be reordered.
 */
enum class PaperColor(val nativeValue: Int) {
    CREAM(0),
    BUTTER(1),
    MINT(2),
    SKY(3),
    BLUSH(4),
    LILAC(5);

    companion object {
        val DEFAULT = CREAM

        fun fromNative(value: Int): PaperColor =
            entries.firstOrNull { it.nativeValue == value } ?: DEFAULT

        /** Cycles to the next stock, for the "change paper" action. */
        fun next(current: PaperColor): PaperColor =
            entries[(current.ordinal + 1) % entries.size]
    }
}

/**
 * The thread tying two papers together.
 *
 * Undirected in meaning — the native layer stores the pair in a canonical order
 * so tying A→B and B→A cannot produce two threads over the same gap.
 */
data class BoardLink(
    val id: Long,
    val boardId: Long,
    val fromNoteId: Long,
    val toNoteId: Long,
    val createdAt: Long
) {
    fun connects(noteId: Long): Boolean = fromNoteId == noteId || toNoteId == noteId

    /** The paper at the other end of this thread, or null if [noteId] is not on it. */
    fun otherEnd(noteId: Long): Long? = when (noteId) {
        fromNoteId -> toNoteId
        toNoteId -> fromNoteId
        else -> null
    }
}

/** Identifier of the board created by migration 003. */
const val DEFAULT_BOARD_ID: Long = 1L
