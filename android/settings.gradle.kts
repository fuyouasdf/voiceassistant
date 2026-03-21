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
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://clojars.org/repo") }
        maven { url = uri("https://repo1.maven.org/maven2") }
    }
}

rootProject.name = "VoiceAssistant"
include(":app")
include(":core")
include(":data")
include(":domain")
