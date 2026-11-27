plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is read from environment variables so no secret lives in the repo.
// Locally: export BEYONDPAD_KEYSTORE=/path/to/beyondpad.jks plus the three passwords/alias.
val releaseKeystore: String? = System.getenv("BEYONDPAD_KEYSTORE")

android {
    namespace = "com.beyondpad.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.beyondpad.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 8
        versionName = "1.7.0"
        // The Pad 3 is arm64 only: skip native code for other CPUs, and English resources only.
        ndk { abiFilters += "arm64-v8a" }
        resourceConfigurations += "en"
    }

    signingConfigs {
        if (releaseKeystore != null && file(releaseKeystore).exists()) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("BEYONDPAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BEYONDPAD_KEY_ALIAS")
                keyPassword = System.getenv("BEYONDPAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

}
