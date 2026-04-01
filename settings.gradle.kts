pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HandMeasureWorkspace"
include(":handmeasure-core")
include(":handtryon-core")
include(":HandMeasure")
include(":HandTryOn")
include(":app")
