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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// RATL - on-device Quran recitation assessment engine (Apache-2.0).
// Consumed as a composite build from the ratl/ git submodule
// (git clone --recursive, or git submodule update --init).
includeBuild("ratl")

rootProject.name = "Emran"
include(":app")
