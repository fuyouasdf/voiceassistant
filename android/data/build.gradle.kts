plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.voiceassistant.data"
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
}

val versions = rootProject.extra["versions"] as Map<String, String>

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Modules
    implementation(project(":core"))
    implementation(project(":domain"))

    // Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${versions["coroutines"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${versions["coroutines"]}")

    // AndroidX
    implementation("androidx.core:core-ktx:${versions["androidxCore"]}")

    // Room
    implementation("androidx.room:room-runtime:${versions["androidxRoom"]}")
    implementation("androidx.room:room-ktx:${versions["androidxRoom"]}")
    ksp("androidx.room:room-compiler:${versions["androidxRoom"]}")

    // Security
    implementation("androidx.security:security-crypto:${versions["securityCrypto"]}")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:${versions["retrofit"]}")
    implementation("com.squareup.retrofit2:converter-gson:${versions["retrofit"]}")
    implementation("com.squareup.okhttp3:okhttp:${versions["okhttp"]}")
    implementation("com.squareup.okhttp3:logging-interceptor:${versions["okhttp"]}")

    // Hilt (for @Inject)
    implementation("com.google.dagger:hilt-android:${versions["hilt"]}")
    ksp("com.google.dagger:hilt-android-compiler:${versions["hilt"]}")

    // Timber
    implementation("com.jakewharton.timber:timber:${versions["timber"]}")

    // Test
    testImplementation("junit:junit:${versions["junit"]}")
}
