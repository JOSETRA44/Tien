plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tien.dutic"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The academic term is part of the aula virtual's URL
        // (https://aulavirtual.unsa.edu.pe/2026A/) and changes every period.
        // This is only the starting guess: the real value is re-derived from the
        // dashboard URL after login, so a stale default corrects itself.
        buildConfigField("String", "DEFAULT_SEMESTER", "\"2026A\"")
        buildConfigField("String", "AULA_HOST", "\"aulavirtual.unsa.edu.pe\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    // Speaks to the aula virtual's internal AJAX endpoint.
    implementation(libs.okhttp)

    // Parses the pages Moodle only ever renders as HTML — grades and profiles
    // have no AJAX equivalent on this install.
    implementation(libs.jsoup)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
