plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.handtryon"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")

    api(project(":handtryon-core"))

    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("io.github.sceneview:arsceneview:2.0.4")

    debugImplementation(composeBom)
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("org.json:json:20240303")
}

tasks.register<Exec>("runTryOnReplayValidation") {
    workingDir = rootDir
    commandLine(
        "python",
        "tools/tryon/replay_validation.py",
        "--annotations",
        "validation/tryon/reference-annotations/video-fixture-2026-04-29.json",
        "--image-dir",
        "validation/tryon/reference-images",
        "--fixture-manifest",
        "validation/tryon/reference-annotations/fixture-manifest.json",
        "--predictions",
        "validation/tryon/captures/video-fixture-2026-04-29.landmark-predictions.json",
        "--report-dir",
        "validation/tryon/reports",
        "--center-ratio-threshold",
        "0.35",
        "--min-center-ratio-pass-rate",
        "0.85",
        "--steady-scale-delta-threshold",
        "0.12",
        "--steady-rotation-jitter-threshold",
        "8.0",
        "--min-steady-pass-rate",
        "0.85",
        "--strict-gate",
    )
}
