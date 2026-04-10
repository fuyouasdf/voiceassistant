pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://clojars.org/repo") }
        maven { url = uri("https://s01.oss.sonatype.org/content/repositories/releases/") }
        maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.jellyfin")
            }
        }
    }
}

rootProject.name = "VoiceAssistant"
include(":app")
include(":core")
include(":data")
include(":domain")
include(":sherpa-onnx-aar")
include(":sherpa-onnx-aar:sherpa_onnx")
