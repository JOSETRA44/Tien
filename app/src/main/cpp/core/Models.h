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
