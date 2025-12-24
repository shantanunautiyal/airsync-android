import org.gradle.api.JavaVersion.VERSION_11
import org.gradle.api.JavaVersion.VERSION_17

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
    kotlin("kapt")
}

android {
    namespace = "com.sameerasw.airsync"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sameerasw.airsync"
        minSdk = 30
        targetSdk = 36
        versionCode = 17
        versionName = "2.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = VERSION_11
        targetCompatibility = VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Smartspacer SDK
    implementation(libs.sdk.plugin)

    // Material Components (XML themes: Theme.Material3.*)
    implementation(libs.material)

    // Android 12+ SplashScreen API with backward compatibility attributes
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    // DataStore for state persistence
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)

    // ViewModel and state handling
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.runtime.livedata)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // WebSocket support
    implementation(libs.okhttp)

    // JSON parsing for GitHub API
    implementation(libs.gson)

    // Media session support for Mac media player
    implementation(libs.androidx.media)

    // Health Connect SDK
    implementation(libs.androidx.connect.client)

    // DocumentFile for folder access
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation(libs.ui.graphics)
    implementation(libs.androidx.foundation)

    // CameraX for QR scanning
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("androidx.camera:camera-mlkit-vision:1.4.0")
    
    // Guava for ListenableFuture (required by CameraX)
    implementation("com.google.guava:guava:32.1.3-android")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")

    // Room database for call history
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Phone number normalization
    implementation(libs.libphonenumber)

    // Coroutines for async operations
    implementation(libs.kotlinx.coroutines.android)

    // ML Kit barcode scanner (QR code only)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
