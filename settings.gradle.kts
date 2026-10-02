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
        // Google distributes Home APIs as a signed-in SDK download.
        // Keep its Maven repository inside this project for a self-contained build.
        maven { url = uri(rootDir.resolve(".home-sdk-repo")) }
        google()
        mavenCentral()
    }
}

rootProject.name = "NestClimateMonitor"
include(":app")
