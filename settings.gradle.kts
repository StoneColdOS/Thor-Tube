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
        // NewPipeExtractor is published through JitPack
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "Thor-Tube"
include(":app")
