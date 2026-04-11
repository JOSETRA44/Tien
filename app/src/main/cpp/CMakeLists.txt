# ═══════════════════════════════════════════════════════════════════════════════
#  Tien Core — Android NDK native library
#  CMake 3.22 · C++17 · SQLite3 amalgamation · JNI bridge
# ═══════════════════════════════════════════════════════════════════════════════

cmake_minimum_required(VERSION 3.22)

project("tien_core" LANGUAGES C CXX)

# ── C++ standard ───────────────────────────────────────────────────────────────
set(CMAKE_CXX_STANDARD          17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS        OFF)   # -std=c++17, not gnu++17

# ═══════════════════════════════════════════════════════════════════════════════
#  TARGET 1 — sqlite3 (static)
#  Source: sqlite3/sqlite3.c  (official SQLite amalgamation)
# ═══════════════════════════════════════════════════════════════════════════════
add_library(sqlite3 STATIC
    sqlite3/sqlite3.c
)

target_include_directories(sqlite3 PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/sqlite3
)

# Mobile-optimised compile-time flags
# Reference: https://www.sqlite.org/compile.html#recommended_compile_time_options
target_compile_definitions(sqlite3 PRIVATE
    SQLITE_OMIT_LOAD_EXTENSION        # No dynamic extension loading on Android
    SQLITE_THREADSAFE=1               # Serialised mode — safe across JNI threads
    SQLITE_OMIT_DEPRECATED            # Trim deprecated surface area → smaller binary
    SQLITE_ENABLE_FTS5                # Full-text search (future-proof)
    SQLITE_ENABLE_JSON1               # JSON helpers for flexible metadata
    SQLITE_DEFAULT_JOURNAL_MODE_WAL   # Write-Ahead Logging by default
    SQLITE_DEFAULT_SYNCHRONOUS=1      # NORMAL — balanced speed / durability
    SQLITE_DEFAULT_MEMSTATUS=0        # Skip memory-use statistics (mobile perf)
    SQLITE_DEFAULT_WAL_AUTOCHECKPOINT=1000
)

# ═══════════════════════════════════════════════════════════════════════════════
#  TARGET 2 — tien_core (shared .so loaded by Android)
#  Sources: db/DatabaseManager.cpp · jni/native-lib.cpp
# ═══════════════════════════════════════════════════════════════════════════════
add_library(tien_core SHARED
    db/DatabaseManager.cpp
    jni/native-lib.cpp
)

# Expose cpp/ as root so any translation unit can write:
#   #include "db/DatabaseManager.h"
#   #include "core/Course.h"
#   #include "utils/Logger.h"
target_include_directories(tien_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(tien_core
    PRIVATE
        sqlite3        # Merged into the .so at link time (no extra .a shipped)
        log            # Android liblog → __android_log_print
)

# ── Release hardening ──────────────────────────────────────────────────────────
target_compile_options(tien_core PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -Werror=return-type
    -fvisibility=hidden   # Only explicit JNI symbols are exported
    -fstack-protector-strong
)

target_link_options(tien_core PRIVATE
    -Wl,--gc-sections     # Discard unreferenced sections
    -Wl,--strip-all       # Remove all symbol table entries from the .so
)

# JNI functions declared extern "C" in native-lib.cpp bypass -fvisibility=hidden
# automatically; no JNIEXPORT/__attribute__((visibility("default"))) dance needed
# as long as you keep the standard Java_com_tien_core_* naming convention.
