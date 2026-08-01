#pragma once

#include <android/log.h>
#include <cstdarg>

namespace tien::utils {

// Thin wrapper around android logcat.
// All modules must route diagnostics through this header —
// never call __android_log_print directly outside of here.
//
// IMPORTANT: the helpers below forward a `va_list`, so they must use
// __android_log_vprint. Handing a va_list to the *variadic*
// __android_log_print is undefined behaviour — a "%s" directive ends up
// dereferencing the va_list structure itself as a char*, which crashes.

enum class LogLevel {
    Verbose = ANDROID_LOG_VERBOSE,
    Debug = ANDROID_LOG_DEBUG,
    Info = ANDROID_LOG_INFO,
    Warn = ANDROID_LOG_WARN,
    Error = ANDROID_LOG_ERROR,
    Fatal = ANDROID_LOG_FATAL
};

constexpr const char* kDefaultTag = "TienCore";

// format(printf, ...) lets -Wformat validate every call site at compile time,
// turning a mismatched directive into a build warning instead of a crash.
#define TIEN_PRINTF_LIKE(fmt_idx, args_idx) __attribute__((format(printf, fmt_idx, args_idx)))

inline void vlog(LogLevel level, const char* tag, const char* fmt, va_list args) {
    __android_log_vprint(static_cast<int>(level), tag, fmt, args);
}

inline void log(LogLevel level, const char* tag, const char* fmt, ...) TIEN_PRINTF_LIKE(3, 4);
inline void log(LogLevel level, const char* tag, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vlog(level, tag, fmt, args);
    va_end(args);
}

// ── Convenience overloads — default tag ─────────────────────────────────────
#define TIEN_DEFINE_LOG_FN(fn_name, level_enum)                       \
    inline void fn_name(const char* fmt, ...) TIEN_PRINTF_LIKE(1, 2); \
    inline void fn_name(const char* fmt, ...) {                       \
        va_list args;                                                 \
        va_start(args, fmt);                                          \
        vlog(level_enum, kDefaultTag, fmt, args);                     \
        va_end(args);                                                 \
    }

TIEN_DEFINE_LOG_FN(v, LogLevel::Verbose)
TIEN_DEFINE_LOG_FN(d, LogLevel::Debug)
TIEN_DEFINE_LOG_FN(i, LogLevel::Info)
TIEN_DEFINE_LOG_FN(w, LogLevel::Warn)
TIEN_DEFINE_LOG_FN(e, LogLevel::Error)

#undef TIEN_DEFINE_LOG_FN

}  // namespace tien::utils
