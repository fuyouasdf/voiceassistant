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
// ============================================================

val downloadModels by tasks.registering {
    group = "voice assistant"
    description = "下载语音模型文件到 assets 目录"

    // 模型版本（与 sherpa-onnx v1.12.32 对应）
    val kwsModelVersion = "2024-01-01"
    val asrModelVersion = "2023-02-20"

    // HuggingFace 下载地址
    val kwsUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-$kwsModelVersion/resolve/main/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-$kwsModelVersion.zip"
    val asrUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-$asrModelVersion/resolve/main/sherpa-onnx-streaming-zipformer-bilingual-zh-en-$asrModelVersion.zip"
    val vadUrl = "https://github.com/snakers4/silero-vad/raw/master/files/silero_vad.onnx"
    val ttsUrl = "https://huggingface.co/csukuangfj/vits-piper-zh_CN-huayan-medium/resolve/main/vits-piper-zh_CN-huayan-medium.tar.gz"

    val assetsDir = file("src/main/assets")
    val tempDir = file("${buildDir}/models_temp")

    doLast {
        val kwsDir = file("${assetsDir}/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-$kwsModelVersion")
        val asrDir = file("${assetsDir}/sherpa-onnx-streaming-zipformer-bilingual-zh-en-$asrModelVersion")
        val vadFile = file("${assetsDir}/silero_vad.onnx")
        val ttsDir = file("${assetsDir}/vits-piper-zh_CN-huayan-medium")

        // 检查模型是否已完整存在（防止重复下载）
        fun isModelComplete(dir: File, vararg requiredFiles: String): Boolean {
            return dir.isDirectory && requiredFiles.all { file("$dir/$it").exists() }
        }
        val kwsComplete = isModelComplete(kwsDir,
            "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "tokens.txt", "keywords.txt")
        val asrComplete = isModelComplete(asrDir,
            "encoder-epoch-99-avg-1.int8.onnx",
            "decoder-epoch-99-avg-1.onnx",
            "joiner-epoch-99-avg-1.onnx",
            "tokens.txt")
        val vadComplete = vadFile.exists() && vadFile.length() > 0
        val ttsComplete = ttsDir.isDirectory && file("$ttsDir/zh_CN-huayan-medium.onnx").exists()

        if (kwsComplete && asrComplete && vadComplete && ttsComplete) {
            println("所有模型已存在，跳过下载:")
            println("  KWS: ${kwsDir.name}")
            println("  ASR: ${asrDir.name}")
            println("  VAD: ${vadFile.name}")
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

        fun extractZip(zipFile: File, destDir: File, namePrefix: String) {
            if (destDir.exists()) {
                println("[${namePrefix}] 已解压，跳过")
                return
            }
            println("[$namePrefix] 解压: ${zipFile.name}")
            exec { commandLine("tar", "-xzf", zipFile.absolutePath, "-C", assetsDir.absolutePath) }
            // 重命名解压后的目录为预期名称
            assetsDir.listFiles()?.find { it.isDirectory && it.name.startsWith(namePrefix) }?.let { actual ->
                if (actual != destDir) actual.renameTo(destDir)
            }
        }

        fun extractTarGz(tarFile: File, destDir: File, namePrefix: String) {
            if (destDir.exists()) {
                println("[${namePrefix}] 已解压，跳过")
                return
            }
            println("[$namePrefix] 解压: ${tarFile.name}")
            exec { commandLine("tar", "-xzf", tarFile.absolutePath, "-C", assetsDir.absolutePath) }
            assetsDir.listFiles()?.find { it.isDirectory && it.name.startsWith(namePrefix) }?.let { actual ->
                if (actual != destDir) actual.renameTo(destDir)
            }
        }

        // 1. KWS 模型
        if (!kwsComplete) {
            val kwsZip = file("${tempDir}/kws.zip")
            download(kwsUrl, kwsZip, "KWS")
            extractZip(kwsZip, kwsDir, "sherpa-onnx-kws")
        } else {
            println("[KWS] 已存在，跳过")
        }

        // 2. ASR 模型
        if (!asrComplete) {
            val asrZip = file("${tempDir}/asr.zip")
            download(asrUrl, asrZip, "ASR")
            extractZip(asrZip, asrDir, "sherpa-onnx-streaming")
        } else {
            println("[ASR] 已存在，跳过")
        }

        // 3. VAD 模型
        if (!vadComplete) {
            download(vadUrl, vadFile, "VAD")
        } else {
            println("[VAD] 已存在，跳过")
        }

        // 4. TTS 模型
        if (!ttsComplete) {
            val ttsTar = file("${tempDir}/tts.tar.gz")
            download(ttsUrl, ttsTar, "TTS")
            extractTarGz(ttsTar, ttsDir, "vits")
        } else {
            println("[TTS] 已存在，跳过")
        }

        // 清理临时文件
        tempDir.deleteRecursively()

        println("")
        println("=== 模型下载完成 ===")
        if (kwsDir.exists()) println("  KWS: ${kwsDir.name}")
        if (asrDir.exists()) println("  ASR: ${asrDir.name}")
        if (vadFile.exists()) println("  VAD: ${vadFile.name}")
        if (ttsDir.exists()) println("  TTS: ${ttsDir.name}")
    }
}
