# ═══════════════════════════════════════════════════════════════════════════
#  Tien — R8 / ProGuard rules
#
#  R8 is enabled for release builds. Without the rules below the release APK
#  would build cleanly and then crash at runtime, which is the worst kind of
#  failure: it never shows up in a debug build.
# ═══════════════════════════════════════════════════════════════════════════

# ── JNI boundary — load-bearing ────────────────────────────────────────────
# libtien_core.so resolves its entry points by the mangled symbol name
# `Java_com_tien_core_data_nativedb_NativeDatabase_<method>`. R8 renames classes
# and methods by default, so obfuscating this one would break every lookup with
# an UnsatisfiedLinkError the moment the database is first touched.
-keep class com.tien.core.data.nativedb.NativeDatabase {
    native <methods>;
    *;
}

# Belt and braces for any future native class: keep every method a .so binds to.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ── Domain models crossing the JSON boundary ───────────────────────────────
# Field names are read reflectively-by-string from the native payload
# ("createdAt", "isDone", …) in NativePayloadMapper. Renaming the Kotlin
# properties is safe — the mapper uses string literals — but keeping the model
# classes makes stack traces readable and guards against a future switch to a
# reflective serializer.
-keep class com.tien.core.domain.model.** { *; }

# ── Kotlin / coroutines ────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# DataStore relies on these being present.
-keep class androidx.datastore.*.** { *; }

# ── Diagnostics ────────────────────────────────────────────────────────────
# Keep line numbers so a release crash report points at a real line, while
# still hiding the original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep the annotations Compose and the Kotlin runtime read at runtime.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
