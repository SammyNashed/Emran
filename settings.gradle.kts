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
// Consumed as a composite build: clone github.com/SammyNashed/ratl next to this repo.
includeBuild("../ratl")

rootProject.name = "Emran"
include(":app")
