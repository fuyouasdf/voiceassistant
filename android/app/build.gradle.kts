plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.voiceassistant.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.voiceassistant.app"
        minSdk = 26
        targetSdk = 34
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
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // Modules
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":domain"))

    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Security (Encrypted SharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Timber (Logging)
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Sherpa-ONNX (Voice) - local AAR file
    implementation(files("libs/sherpa-onnx-android.aar"))

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

kapt {
    correctErrorTypes = true
}

// ============================================================
// 模型下载任务
// 用法: ./gradlew downloadModels
//
// 注意：此任务的模型 URL 必须与 ModelConfig.kt 中的 getDownloadUrl() 保持一致
// ============================================================

val downloadModels by tasks.registering {
    group = "voice assistant"
    description = "下载语音模型文件到 assets 目录"

    // 模型版本 - 与 ModelConfig.kt 保持一致
    val kwsModelVersion = "2025-02-28"
    val asrModelVersion = "v1.12.30"
    val ttsModelVersion = "2025-01-15"

    // GitHub releases 下载地址 - 与 ModelConfig.kt getDownloadUrl() 一致
    val kwsUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-$kwsModelVersion.tar.bz2"
    val asrUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/$asrModelVersion/sherpa-onnx-$asrModelVersion-vad-asr-zh_en-paraformer_large.tar.bz2"
    val ttsUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-zh_CN-huayan-medium-$ttsModelVersion.tar.bz2"

    val assetsDir = file("src/main/assets")
    val tempDir = file("${buildDir}/models_temp")

    doLast {
        // 解压后的目录名（与实际发布版本保持一致）
        val kwsDir = file("${assetsDir}/sherpa-onnx-kws-zipformer-gigaspeech-3.3M")
        val asrDir = file("${assetsDir}/sherpa-onnx-$asrModelVersion-vad-asr-zh_en-paraformer_large")
        val vadDir = file("${assetsDir}/models/vad")  // VAD 单独目录
        val ttsDir = file("${assetsDir}/vits-piper-zh_CN-huayan-medium")

        // 检查模型是否已完整存在（防止重复下载）
        // KWS 模型文件检查
        fun isKwsComplete(): Boolean {
            if (!kwsDir.isDirectory) return false
            val required = listOf("model.onnx", "tokens.txt", "keywords.txt")
            return required.all { file("$kwsDir/$it").exists() }
        }

        // ASR 模型文件检查 (paraformer_large)
        fun isAsrComplete(): Boolean {
            if (!asrDir.isDirectory) return false
            val required = listOf("model.onnx", "decodeit.nemo", "vocab.txt")
            return required.all { file("$asrDir/$it").exists() }
        }

        // VAD 模型文件检查
        fun isVadComplete(): Boolean {
            return vadDir.isDirectory && file("$vadDir/silero_vad.onnx").exists()
        }

        // TTS 模型文件检查
        fun isTtsComplete(): Boolean {
            if (!ttsDir.isDirectory) return false
            return file("$ttsDir/zh_CN-huayan-medium.onnx").exists()
        }

        val kwsComplete = isKwsComplete()
        val asrComplete = isAsrComplete()
        val vadComplete = isVadComplete()
        val ttsComplete = isTtsComplete()

        if (kwsComplete && asrComplete && vadComplete && ttsComplete) {
            println("所有模型已存在，跳过下载:")
            println("  KWS: ${kwsDir.name}")
            println("  ASR: ${asrDir.name}")
            println("  VAD: ${vadDir.name}")
            println("  TTS: ${ttsDir.name}")
            return@doLast
        }

        // 创建临时目录
        tempDir.mkdirs()

        // 下载函数
        fun download(url: String, dest: File, desc: String) {
            if (dest.exists() && dest.length() > 0) {
                println("[$desc] 压缩包已存在: ${dest.name}")
                return
            }
            println("[$desc] 下载: $url")
            exec { commandLine("curl", "-L", "-o", dest.absolutePath, url) }
            println("[$desc] 完成: ${dest.length() / 1024 / 1024} MB")
        }

        // 解压 tar.bz2 函数
        fun extractTarBz2(tarFile: File, namePrefix: String) {
            val extractedDir = file("${assetsDir}/$namePrefix")
            if (extractedDir.exists()) {
                println("[$namePrefix] 已解压，跳过")
                return
            }
            println("[$namePrefix] 解压: ${tarFile.name}")
            // tar.bz2 格式使用 -xjf 参数
            exec { commandLine("tar", "-xjf", tarFile.absolutePath, "-C", assetsDir.absolutePath) }
            // 重命名解压后的目录为预期名称
            assetsDir.listFiles()?.find { it.isDirectory && it.name.startsWith(namePrefix) }?.let { actual ->
                if (actual != extractedDir) {
                    actual.renameTo(extractedDir)
                }
            }
        }

        // 1. KWS 模型
        if (!kwsComplete) {
            val kwsTar = file("${tempDir}/kws.tar.bz2")
            download(kwsUrl, kwsTar, "KWS")
            extractTarBz2(kwsTar, "sherpa-onnx-kws-zipformer-gigaspeech-3.3M")
        } else {
            println("[KWS] 已存在，跳过")
        }

        // 2. ASR 模型 (paraformer_large)
        if (!asrComplete) {
            val asrTar = file("${tempDir}/asr.tar.bz2")
            download(asrUrl, asrTar, "ASR")
            extractTarBz2(asrTar, "sherpa-onnx-$asrModelVersion-vad-asr-zh_en-paraformer_large")
        } else {
            println("[ASR] 已存在，跳过")
        }

        // 3. VAD 模型 (silero_vad.onnx) - 单独下载
        if (!vadComplete) {
            vadDir.mkdirs()
            val vadFile = file("${vadDir}/silero_vad.onnx")
            val vadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
            if (!vadFile.exists() || vadFile.length() == 0L) {
                println("[VAD] 下载: $vadUrl")
                exec { commandLine("curl", "-L", "-o", vadFile.absolutePath, vadUrl) }
                println("[VAD] 完成: ${vadFile.length() / 1024 / 1024} MB")
            } else {
                println("[VAD] 已存在，跳过")
            }
        } else {
            println("[VAD] 已存在，跳过")
        }

        // 4. TTS 模型
        if (!ttsComplete) {
            val ttsTar = file("${tempDir}/tts.tar.bz2")
            download(ttsUrl, ttsTar, "TTS")
            extractTarBz2(ttsTar, "vits-piper-zh_CN-huayan-medium")
        } else {
            println("[TTS] 已存在，跳过")
        }

        // 清理临时文件
        tempDir.deleteRecursively()

        println("")
        println("=== 模型下载完成 ===")
        if (kwsDir.exists()) println("  KWS: ${kwsDir.name}")
        if (asrDir.exists()) println("  ASR: ${asrDir.name}")
        if (vadDir.exists()) println("  VAD: ${vadDir.name}")
        if (ttsDir.exists()) println("  TTS: ${ttsDir.name}")
    }
}
