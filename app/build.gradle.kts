plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.stonecoldos.thortube"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.stonecoldos.thortube"
        minSdk = 33
        targetSdk = 35
        // CI passes the run number so every test build has a higher version than the last.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        // A release tag like v0.1.0 names the release build; everything else is a numbered test build.
        val tag = System.getenv("GITHUB_REF_NAME")?.takeIf { it.matches(Regex("v\\d+\\.\\d+\\.\\d+.*")) }
        versionName = System.getenv("THOR_VERSION") ?: tag?.removePrefix("v") ?: "0.0.1-dev.$build"
    }

    signingConfigs {
        // Shared debug key (committed on purpose) so each test build installs over the last one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release signing comes from CI secrets; see .github/workflows/release.yml
        val storeFilePath = System.getenv("THOR_KEYSTORE_PATH")
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("THOR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("THOR_KEY_ALIAS") ?: "thortube"
                keyPassword = System.getenv("THOR_KEY_PASSWORD") ?: System.getenv("THOR_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Not debuggable (the default for release): nobody with a USB cable can reach the app's data.
            // The extractor relies on reflection-heavy libraries; keep shrinking off until it's tested.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/README.md",
                "META-INF/CHANGES",
                "META-INF/COPYRIGHT",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)

    implementation(libs.okhttp)
    implementation(libs.newpipe.extractor)
}
