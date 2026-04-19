plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.voiceassistant.core"
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
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        disable += "MissingPermission"
    }
}

val versions = rootProject.extra["versions"] as Map<String, String>

dependencies {
    // Core Library Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${versions["coroutines"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${versions["coroutines"]}")

    // AndroidX
    implementation("androidx.core:core-ktx:${versions["androidxCore"]}")

    // Gson
    implementation("com.google.code.gson:gson:${versions["gson"]}")

    // Retrofit (for HttpException)
    implementation("com.squareup.retrofit2:retrofit:${versions["retrofit"]}")

    // Media3
    implementation("androidx.media3:media3-common:${versions["media3"]}")
    implementation("androidx.media3:media3-session:${versions["media3"]}")
    implementation("androidx.media3:media3-ui:${versions["media3"]}")
    implementation("androidx.media3:media3-exoplayer:${versions["media3"]}")

    // Sherpa-ONNX (AAR module built from sherpa-onnx-aar)
    implementation(project(":sherpa-onnx-aar:sherpa_onnx"))

    // Data module (for JellyfinClient)
    // Note: Removed due to circular dependency. Use PlaybackReporter interface instead.

    // Timber
    implementation("com.jakewharton.timber:timber:${versions["timber"]}")

    // Domain module
    implementation(project(":domain"))

    // Hilt (using KSP)
    implementation("com.google.dagger:hilt-android:${versions["hilt"]}")
    ksp("com.google.dagger:hilt-android-compiler:${versions["hilt"]}")

    // Test
    testImplementation("junit:junit:${versions["junit"]}")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${versions["coroutines"]}")
    androidTestImplementation("androidx.test.ext:junit:${versions["androidxTestExtJunit"]}")
    androidTestImplementation("androidx.test.espresso:espresso-core:${versions["androidxTestEspresso"]}")
}
