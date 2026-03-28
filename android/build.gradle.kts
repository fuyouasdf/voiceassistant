// Top-level build file
plugins {
    id("com.android.application") version "8.9.1" apply false
    id("com.android.library") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.google.dagger.hilt.android") version "2.55" apply false
}

extra["versions"] = mapOf(
    "androidxCore" to "1.17.0",
    "androidxLifecycle" to "2.9.4",
    "androidxRoom" to "2.7.2",
    "coroutines" to "1.10.2",
    "timber" to "5.0.1",
    "hilt" to "2.55",
    "retrofit" to "2.11.0",
    "okhttp" to "4.12.0",
    "securityCrypto" to "1.1.0-alpha06",
    "leakcanary" to "2.14",
    "androidxActivity" to "1.11.0",
    "androidxFragment" to "1.8.9",
    "androidxAppcompat" to "1.7.1",
    "material" to "1.13.0",
    "constraintlayout" to "2.2.1",
    "junit" to "4.13.2",
    "androidxTestRunner" to "1.7.0",
    "androidxTestExtJunit" to "1.2.1",
    "androidxTestEspresso" to "3.7.0",
    "media3" to "1.8.0",
    "coil" to "2.6.0",
    "gson" to "2.10.1"
)
