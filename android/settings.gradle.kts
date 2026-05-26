pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Note: do NOT add `org.gradle.toolchains.foojay-resolver-convention` here.
// It downloads JDKs from foojay.io at build time, which F-Droid considers a
// non-free network dependency and rejects in their build scanner. We pin
// `sourceCompatibility = 17` in app/build.gradle.kts and rely on the
// build environment to provide JDK 17 (which both local dev and F-Droid
// CI have available by default).

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "aka Alarm"
include(":app")
