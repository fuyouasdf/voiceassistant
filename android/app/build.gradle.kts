plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.voiceassistant.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.voiceassistant.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

val versions = rootProject.extra["versions"] as Map<String, String>

dependencies {
    // Modules
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":domain"))

    // Core Library Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // AndroidX Core
    implementation("androidx.core:core-ktx:${versions["androidxCore"]}")
    implementation("androidx.appcompat:appcompat:${versions["androidxAppcompat"]}")
    implementation("com.google.android.material:material:${versions["material"]}")
    implementation("androidx.constraintlayout:constraintlayout:${versions["constraintlayout"]}")
    implementation("androidx.activity:activity-ktx:${versions["androidxActivity"]}")
    implementation("androidx.fragment:fragment-ktx:${versions["androidxFragment"]}")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${versions["androidxLifecycle"]}")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${versions["androidxLifecycle"]}")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${versions["androidxLifecycle"]}")
    implementation("androidx.lifecycle:lifecycle-process:${versions["androidxLifecycle"]}")

    // Room
    implementation("androidx.room:room-runtime:${versions["androidxRoom"]}")
    implementation("androidx.room:room-ktx:${versions["androidxRoom"]}")
    ksp("androidx.room:room-compiler:${versions["androidxRoom"]}")

    // Security (Encrypted SharedPreferences)
    implementation("androidx.security:security-crypto:${versions["securityCrypto"]}")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:${versions["retrofit"]}")
    implementation("com.squareup.retrofit2:converter-gson:${versions["retrofit"]}")
    implementation("com.squareup.okhttp3:okhttp:${versions["okhttp"]}")
    implementation("com.squareup.okhttp3:logging-interceptor:${versions["okhttp"]}")

    // Hilt (using KSP)
    implementation("com.google.dagger:hilt-android:${versions["hilt"]}")
    ksp("com.google.dagger:hilt-android-compiler:${versions["hilt"]}")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${versions["coroutines"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${versions["coroutines"]}")

    // Timber (Logging)
    implementation("com.jakewharton.timber:timber:${versions["timber"]}")

    // Image Loading
    implementation("io.coil-kt:coil:${versions["coil"]}")

    // Test
    testImplementation("junit:junit:${versions["junit"]}")
    androidTestImplementation("androidx.test.ext:junit:${versions["androidxTestExtJunit"]}")
    androidTestImplementation("androidx.test.espresso:espresso-core:${versions["androidxTestEspresso"]}")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
