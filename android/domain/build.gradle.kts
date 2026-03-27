plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.voiceassistant.domain"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

val versions = rootProject.extra["versions"] as Map<String, String>

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${versions["coroutines"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${versions["coroutines"]}")

    // Timber
    implementation("com.jakewharton.timber:timber:${versions["timber"]}")

    // Hilt
    implementation("com.google.dagger:hilt-android:${versions["hilt"]}")
    ksp("com.google.dagger:hilt-android-compiler:${versions["hilt"]}")

    // Test
    testImplementation("junit:junit:${versions["junit"]}")
}
