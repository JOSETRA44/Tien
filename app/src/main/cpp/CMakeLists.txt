# ═══════════════════════════════════════════════════════════════════════════
#  Tien Core — Android NDK native library
#  C++17 · SQLite3 (amalgamation) · JNI bridge
# ═══════════════════════════════════════════════════════════════════════════

cmake_minimum_required(VERSION 3.22)   # NDK r25+ ships 3.22

project("tien_core" LANGUAGES C CXX)

# ── C++ standard ────────────────────────────────────────────────────────────
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)          # -std=c++17, not gnu++17

# ── SQLite3 amalgamation (embedded) ─────────────────────────────────────────
# The amalgamation files (sqlite3.c / sqlite3.h) must live in sqlite3/.
# Download from https://www.sqlite.org/download.html → "amalgamation" zip.
add_library(sqlite3 STATIC
    sqlite3/sqlite3.c
)
target_include_directories(sqlite3 PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/sqlite3
)
# Recommended compile-time options for a mobile-embedded build:
# https://www.sqlite.org/compile.html#recommended_compile_time_options
target_compile_definitions(sqlite3 PRIVATE
    SQLITE_OMIT_LOAD_EXTENSION      # No dynamic extension loading on Android
    SQLITE_THREADSAFE=1             # Serialized mode — safe across JNI threads
    SQLITE_OMIT_DEPRECATED          # Smaller binary
    SQLITE_ENABLE_FTS5              # Future-proof: full-text search
    SQLITE_ENABLE_JSON1             # JSON helpers for flexible metadata
    SQLITE_DEFAULT_JOURNAL_MODE_WAL # Write-Ahead Logging by default
    SQLITE_DEFAULT_SYNCHRONOUS=1    # NORMAL — good balance speed/safety
)

# ── Tien shared library (the .so that Android loads) ───────────────────────
add_library(tien_core SHARED
    db/DatabaseManager.cpp
    jni/native-lib.cpp
)

target_include_directories(tien_core PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}      # Allows #include "db/DatabaseManager.h"
)

target_link_libraries(tien_core
    PRIVATE
        sqlite3                     # Static lib → merged into the .so
        log                         # Android liblog for __android_log_print
)

# Strip unused symbols for a leaner binary
target_link_options(tien_core PRIVATE
    -Wl,--gc-sections
    -Wl,--strip-all
)

# ── Compiler warnings ───────────────────────────────────────────────────────
target_compile_options(tien_core PRIVATE
    -Wall -Wextra -Wpedantic
    -Werror=return-type
    -fvisibility=hidden             # Only JNI exports are visible (see below)
)

# JNI functions use explicit extern "C" so they bypass -fvisibility=hidden.
# No extra attributes needed; the linker exports them by default when the
# symbol is not static and is referenced.
