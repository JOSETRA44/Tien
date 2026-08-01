plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)         // Kotlin 2.x Compose compiler plugin
}

android {
    namespace = "com.tien.core"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.tien.core"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    buildTypes {
        debug {
            // Lets a debug build sit side-by-side with a release install.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false

            ndk {
                // Debug builds only need the two architectures anyone actually
                // debugs on: modern hardware and the x86_64 emulator. The
                // 9.5 MB SQLite amalgamation was being compiled four times per
                // build, for armeabi-v7a and x86 nobody was running.
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
        release {
            // R8 was off, so release shipped every unused class with full symbol
            // names. Both flags on: smaller APK, obfuscated code.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // AGP 8.13 + compileSdk 36 expect a 17 toolchain; 11 left newer API
        // surface and desugaring on the table.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        // minSdk is 24, but the date/time layer uses java.time (API 26+), so
        // the desugar library backfills it.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"

        // ── Informes del compilador de Compose ────────────────────────────────
        // Emits, per composable, whether it is skippable/restartable and whether
        // each parameter is stable. That is the only way to *verify* an
        // @Immutable claim rather than trust it: an unstable parameter silently
        // makes a composable recompose on every frame.
        //
        //   ./gradlew assembleRelease -PcomposeCompilerReports=true
        //   → build/compose-reports/*.txt  and  build/compose-metrics/*.json
        if (project.findProperty("composeCompilerReports") == "true") {
            freeCompilerArgs += listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
                    layout.buildDirectory.dir("compose-reports").get().asFile.absolutePath,
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" +
                    layout.buildDirectory.dir("compose-metrics").get().asFile.absolutePath
            )
        }
    }

    // ── Android Lint ─────────────────────────────────────────────────────────
    // Lint shipped with AGP but was never configured, so it ran with defaults
    // and nothing ever failed on its findings.
    lint {
        // Correctness and security issues stop the build; style opinions do not.
        abortOnError = true
        warningsAsErrors = false

        // A missing translation or an unused resource is worth knowing about.
        checkDependencies = true
        checkReleaseBuilds = true
        checkTestSources = true

        // Promote the checks that map to real user-visible defects.
        error += listOf(
            "UnsafeOptInUsageError",
            "WrongThread",
            "WrongConstant",
            "Recycle",              // unclosed Cursor / TypedArray
            "SuspiciousIndentation",
            "StringFormatInvalid",
            "StringFormatMatches",
            "InlinedApi",
            "NewApi",               // API level guards — minSdk is 24
            "ObsoleteSdkInt"
        )

        disable += listOf(
            // Not applicable: the app has no deep links to index.
            "GoogleAppIndexingWarning",
            "MissingApplicationIcon",

            // Dependency freshness is Dependabot's job (.github/dependabot.yml).
            // Leaving it to lint means every build reports staleness that only a
            // deliberate, tested upgrade should resolve — noise that trains
            // people to ignore lint output.
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion"
        )

        // No baseline file. The project is at zero lint errors, and a baseline
        // is a way to *defer* findings — worth adding only when inheriting debt
        // that cannot be paid down at once.

        htmlReport = true
        sarifReport = true   // ingested by GitHub code scanning
        xmlReport = false
        textReport = false
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// ── Estabilidad de Compose ───────────────────────────────────────────────────
// The compiler cannot infer stability for JDK types, so it treats every one as
// unstable — which then infects any composable taking one as a parameter. The
// config file lists the types that are genuinely immutable and safe to trust.
composeCompiler {
    stabilityConfigurationFile =
        rootProject.layout.projectDirectory.file("compose_compiler_config.conf")
}

dependencies {
    // ── Core ──────────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // ── Jetpack Compose (BOM manages all Compose versions) ───────────────────
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // ── Compose integration ───────────────────────────────────────────────────
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // ── Preferences ───────────────────────────────────────────────────────────
    implementation(libs.androidx.datastore.preferences)

    // ── Reglas de lint específicas de Compose ─────────────────────────────────
    // Catches what stock lint cannot see: unstable parameters that defeat
    // skipping, composables that forget to accept a Modifier, state hoisted to
    // the wrong level. `lintChecks` ships them into the lint run only — nothing
    // is added to the APK.
    lintChecks(libs.compose.lint.checks)

    // ── Debug tooling ─────────────────────────────────────────────────────────
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Watches for retained objects. Apt here specifically because the app now
    // holds a process-long native connection and ViewModels that own Flow
    // collectors — the two shapes that leak an Activity if they capture one.
    // debugImplementation only, so no LeakCanary code reaches a release build.
    debugImplementation(libs.leakcanary.android)

    // ── Tests ─────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
