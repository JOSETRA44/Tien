#pragma once

#include <string>
#include <cstdint>

namespace tien::core {

// Represents a "Bóveda" — an independent course vault.
// Each Course maps 1:1 to its own .db file on disk.
struct Course {
    int64_t     id;          // SQLite ROWID alias
    std::string name;        // Human-readable course name  (e.g. "Cálculo II")
    std::string code;        // Institutional code          (e.g. "MAT-202")
    std::string db_path;     // Absolute path to the vault  (e.g. "/data/.../MAT-202.db")
    int64_t     created_at;  // Unix epoch seconds
    int64_t     updated_at;  // Unix epoch seconds
};

// A single grade/evaluation entry inside a course vault.
struct Grade {
    int64_t     id;
    int64_t     course_id;   // FK → Course.id  (logical, not enforced across DBs)
    std::string title;       // "Parcial 1", "Laboratorio 3" …
    double      score;       // 0.0 – 100.0  (normalised scale)
    double      weight;      // Percentage weight toward final grade (0.0 – 1.0)
    int64_t     recorded_at; // Unix epoch seconds
};

// A note/memo related to a course.
struct Note {
    int64_t     id;         // Escalabilidad: Se cambia de int a int64_t (SQLite ROWID)
    std::string title;
    std::string content;
    int64_t     timestamp;
};

// A scheduled productivity task.
struct Task {
    int64_t     id;
    std::string title;
    std::string details;
    int64_t     due_at;      // Unix epoch seconds
    int64_t     created_at;  // Unix epoch seconds
    int         priority;    // 0=Low, 1=Medium, 2=High
    bool        is_done;
};

} // namespace tien::core
