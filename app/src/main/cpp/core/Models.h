#pragma once

#include <string>
#include <cstdint>

// ═══════════════════════════════════════════════════════════════════════════
//  Tien — Domain models (native side)
//
//  Single source of truth for the C++ layer. Every struct here mirrors a
//  Kotlin data class in `com.tien.core.domain.model`; keep the two in sync.
//  Field ordering matters — aggregate initialisation is used in DatabaseManager.
// ═══════════════════════════════════════════════════════════════════════════

namespace tien::core {

// Priority ladder for a task. Persisted as INTEGER.
enum class Priority : int {
    Low = 0,
    Medium = 1,
    High = 2
};

constexpr int kPriorityMin = static_cast<int>(Priority::Low);
constexpr int kPriorityMax = static_cast<int>(Priority::High);

// Clamp any externally supplied integer into the valid Priority range.
constexpr int clampPriority(int raw) noexcept {
    if (raw < kPriorityMin) return kPriorityMin;
    if (raw > kPriorityMax) return kPriorityMax;
    return raw;
}

// ── Note ────────────────────────────────────────────────────────────────────
struct Note {
    int64_t     id;  // SQLite ROWID alias
    std::string title;
    std::string content;
    int64_t     created_at;  // Unix epoch seconds
    int64_t     updated_at;  // Unix epoch seconds
    bool        pinned;
};

// ── Task ────────────────────────────────────────────────────────────────────
struct Task {
    int64_t     id;
    std::string title;
    std::string details;
    int64_t     due_at;      // Unix epoch seconds
    int64_t     created_at;  // Unix epoch seconds
    int64_t     updated_at;  // Unix epoch seconds
    int         priority;    // Priority enum value
    bool        is_done;
};

// ── Board: papers pinned on a wall ──────────────────────────────────────────

// One piece of paper on the board.
//
// Position, rotation and stacking order belong to the *paper*, not to the idea
// written on it — the same text pinned twice is two papers, each with its own
// spot on the wall.
struct BoardNote {
    int64_t     id;
    int64_t     board_id;
    std::string text;

    // Board coordinates in density-independent pixels, of the note's top-left
    // corner before rotation is applied.
    double x;
    double y;
    double width;
    double height;

    // Degrees. Persisted, so a paper keeps the tilt it was pinned at.
    double rotation;

    int color_index;
    int z;  // stacking order; higher is nearer the viewer

    // 0 when the paper carries its own text rather than mirroring a note.
    int64_t source_note_id;

    int64_t created_at;
    int64_t updated_at;
};

// The thread tying two papers together.
struct BoardLink {
    int64_t id;
    int64_t board_id;
    int64_t from_note_id;
    int64_t to_note_id;
    int64_t created_at;
};

// Sentinel for BoardNote::source_note_id when the paper is standalone.
constexpr int64_t kNoSourceNote = 0;

// ── Query contracts (mirrored by Kotlin enums) ──────────────────────────────

// Ordering applied to the notes query. Pushed down to SQL — never sorted in
// Kotlin, so the work stays O(log n) against an index instead of O(n log n)
// over the whole table in memory.
enum class NoteSort : int {
    RecentlyUpdated = 0,
    OldestFirst = 1,
    TitleAsc = 2
};

// Completion filter applied to the tasks query.
enum class TaskFilter : int {
    All = 0,
    Pending = 1,
    Completed = 2
};

}  // namespace tien::core
