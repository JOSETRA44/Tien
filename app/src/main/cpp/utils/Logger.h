#pragma once

#include <android/log.h>
#include <string>

namespace tien::utils {

// Thin RAII-safe wrapper around android logcat.
// All modules must route diagnostics through this header —
// never call __android_log_print directly outside of here.

enum class LogLevel {
    Verbose = ANDROID_LOG_VERBOSE,
    Debug    = ANDROID_LOG_DEBUG,
    Info     = ANDROID_LOG_INFO,
    Warn     = ANDROID_LOG_WARN,
    Error    = ANDROID_LOG_ERROR,
    Fatal    = ANDROID_LOG_FATAL
};

constexpr const char* kDefaultTag = "TienCore";

inline void log(LogLevel level, const char* tag, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    __android_log_print(static_cast<int>(level), tag, fmt, args);
    va_end(args);
}

// Convenience overloads — default tag
inline void v(const char* fmt, ...) { va_list a; va_start(a, fmt); __android_log_print(ANDROID_LOG_VERBOSE, kDefaultTag, fmt, a); va_end(a); }
inline void d(const char* fmt, ...) { va_list a; va_start(a, fmt); __android_log_print(ANDROID_LOG_DEBUG,    kDefaultTag, fmt, a); va_end(a); }
inline void i(const char* fmt, ...) { va_list a; va_start(a, fmt); __android_log_print(ANDROID_LOG_INFO,     kDefaultTag, fmt, a); va_end(a); }
inline void w(const char* fmt, ...) { va_list a; va_start(a, fmt); __android_log_print(ANDROID_LOG_WARN,     kDefaultTag, fmt, a); va_end(a); }
inline void e(const char* fmt, ...) { va_list a; va_start(a, fmt); __android_log_print(ANDROID_LOG_ERROR,    kDefaultTag, fmt, a); va_end(a); }

} // namespace tien::utils
