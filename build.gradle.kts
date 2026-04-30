import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    id("com.android.application") version "8.12.3" apply false
    id("com.android.library") version "8.12.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2" apply false
}

val workspaceDrive = rootProject.projectDir.toPath().root?.toString()?.trimEnd('\\') ?: "D:"
val asciiBuildRoot = "$workspaceDrive\\handmeasure-workspace-build"

allprojects {
    val projectSegment = if (path == ":") "root" else path.removePrefix(":").replace(':', '\\')
    layout.buildDirectory.set(file("$asciiBuildRoot\\$projectSegment"))
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.android") {
        apply(plugin = "io.gitlab.arturbosch.detekt")
        apply(plugin = "org.jlleitschuh.gradle.ktlint")

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            allRules = false
            parallel = true
            config.setFrom(files("$rootDir/detekt-relaxed.yml"))
        }

        tasks.withType<Detekt>().configureEach {
            jvmTarget = "17"
        }

        extensions.configure<KtlintExtension> {
            android.set(true)
            ignoreFailures.set(false)
            outputToConsole.set(true)
            baseline.set(file("$rootDir/config/ktlint/baseline.xml"))
            filter {
                exclude("**/generated/**")
                exclude("**/build/**")
            }
        }
    }
}

tasks.register<Exec>("tryOnReplayValidation") {
    workingDir = rootDir
    commandLine(
        "python",
        "tools/tryon/replay_validation.py",
        "--annotations",
        "validation/tryon/reference-annotations/video-fixture-2026-04-29.json",
        "--image-dir",
        "validation/tryon/reference-images",
        "--predictions",
        "validation/tryon/captures/video-fixture-2026-04-29.landmark-predictions.json",
        "--fixture-manifest",
        "validation/tryon/reference-annotations/fixture-manifest.json",
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
        "--min-steady-pair-count",
        "2",
        "--prediction-source",
        "fixture",
        "--skip-frame-read",
        "--strict-gate",
    )
}

tasks.register<Exec>("tryOnVideoReplayValidation") {
    workingDir = rootDir
    commandLine(
        "python",
        "tools/tryon/replay_validation.py",
        "--annotations",
        "validation/tryon/reference-annotations/video-fixture-2026-04-29.json",
        "--image-dir",
        "validation/tryon/reference-images",
        "--predictions",
        "validation/tryon/captures/video-fixture-2026-04-29.landmark-predictions.json",
        "--fixture-manifest",
        "validation/tryon/reference-annotations/fixture-manifest.json",
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
        "--min-steady-pair-count",
        "2",
        "--prediction-source",
        "fixture",
        "--skip-frame-read",
        "--strict-gate",
    )
}

tasks.register("tryOnAndroidReplayValidation") {
    dependsOn(":app:connectedDebugAndroidTest")
}
