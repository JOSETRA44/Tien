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
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true                           // Enable Jetpack Compose
    }
}

dependencies {
    // ── Core ──────────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

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

    // ── Debug tooling ─────────────────────────────────────────────────────────
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ── Tests ─────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
