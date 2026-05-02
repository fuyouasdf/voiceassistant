/*
 * Copyright 2024 Voice Assistant Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.voiceassistant.core.pipeline

import android.content.Context
import timber.log.Timber
import java.io.File

/**
 * Manages wake words lifecycle:
 * - Stores wake words in internal storage
 * - Validates wake words against model tokens before saving
 * - Provides hot-reload capability for SherpaKWS
 * - Manages per-wake-word response phrases
 *
 * IMPORTANT: Sherpa-ONNX's KeywordSpotter calls exit() on encode failure,
 * which cannot be caught. Therefore we MUST validate wake words against
 * tokens.txt before writing to keywords.txt.
 *
 * Strategy: We keep the original keywords.txt format (from assets) as-is
 * and only validate that custom wake word Chinese characters exist in tokens.txt.
 */
class WakeWordManager(private val context: Context) {

    private val keywordsFile: File by lazy {
        File(context.filesDir, "models/kws/keywords.txt")
    }

    private val tokensFile: File by lazy {
        File(context.filesDir, "models/kws/tokens.txt")
    }

    private val _wakeWords = mutableListOf<WakeWord>()
    val wakeWords: List<WakeWord> get() = _wakeWords.toList()

    // Pinyin converter for dynamic pinyin generation
    private val pinyinConverter = PinyinConverter()

    // Valid character tokens from tokens.txt (Chinese characters Sherpa can encode)
    private lateinit var validChars: Set<String>

    // Original keywords from assets (we copy this verbatim - format is known to work)
    private val originalKeywordsText: String by lazy {
        try {
            context.assets.open("sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/keywords.txt")
                .bufferedReader()
                .readText()
        } catch (e: Exception) {
            Timber.e(e, "Failed to read original keywords.txt from assets")
            ""
        }
    }

    // Initialization flag
    private var isInitialized = false

    /**
     * Initialize by loading tokens.txt to get valid characters
     */
    fun initialize() {
        if (isInitialized) return
        validChars = loadValidChars()
        isInitialized = true
        Timber.d("WakeWordManager initialized with ${validChars.size} valid chars")
    }

    /**
     * Load valid Chinese characters from tokens.txt
     * These are single characters that Sherpa can encode
     */
    private fun loadValidChars(): Set<String> {
        return try {
            val tokensSrc = if (tokensFile.exists()) {
                tokensFile.readText()
            } else {
                context.assets.open("sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/tokens.txt")
                    .bufferedReader().readText()
            }
            // Extract tokens - take the part before space (token text)
            tokensSrc.lines()
                .map { it.substringBefore(" ").trim() }
                .filter { it.isNotEmpty() && it.length == 1 && it[0].code > 127 } // single CJK char
                .map { it }
                .toSet()
        } catch (e: Exception) {
            Timber.e(e, "Failed to load valid chars from tokens.txt")
            emptySet()
        }
    }

    /**
     * Default wake words - using ONLY characters from validChars
     * These are the original working keywords from assets
     */
    private val defaultWakeWords = listOf(
        WakeWord("小爱同学", "我在"),
        WakeWord("蛋哥蛋哥", "我在"),
        WakeWord("林美丽", "我在"),
        WakeWord("你好西西", "我在")
    )

    /**
     * Load wake words from internal storage
     */
    fun loadWakeWords(): List<WakeWord> {
        initialize()
        _wakeWords.clear()
        return try {
            if (keywordsFile.exists()) {
                val lines = keywordsFile.readLines()
                val loaded = lines.mapNotNull { line ->
                    WakeWord.fromLine(line)
                }.filter { validateWakeWord(it.keyword) }
                if (loaded.isNotEmpty()) {
                    _wakeWords.addAll(loaded)
                    Timber.d("Loaded ${loaded.size} wake words from $keywordsFile")
                    loaded
                } else {
                    Timber.w("No valid wake words in file, writing defaults")
                    writeOriginalKeywords()
                    _wakeWords.addAll(defaultWakeWords)
                    defaultWakeWords
                }
            } else {
                Timber.d("Keywords file not found, writing original defaults")
                writeOriginalKeywords()
                _wakeWords.addAll(defaultWakeWords)
                defaultWakeWords
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load wake words, using defaults")
            _wakeWords.addAll(defaultWakeWords)
            defaultWakeWords
        }
    }

    /**
     * Write the original keywords.txt from assets (verbatim copy)
     * This preserves the exact format that Sherpa accepts
     */
    private fun writeOriginalKeywords() {
        try {
            keywordsFile.parentFile?.mkdirs()
            if (originalKeywordsText.isNotEmpty()) {
                keywordsFile.writeText(originalKeywordsText)
                Timber.d("Wrote original keywords.txt")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to write original keywords.txt")
        }
    }

    /**
     * Save wake words to internal storage and write keywords.txt for SherpaKWS
     * @param words List of wake words to save
     * @return true if save successful
     * @throws IllegalArgumentException if wake words fail validation
     */
    fun saveWakeWords(words: List<WakeWord>): Boolean {
        initialize()

        // Validate all words before saving any
        val invalidWords = words.filterNot { validateWakeWord(it.keyword) }
        if (invalidWords.isNotEmpty()) {
            val invalidStr = invalidWords.joinToString(", ") { it.keyword }
            Timber.w("Invalid wake words rejected: $invalidStr")
            throw IllegalArgumentException("唤醒词包含不支持的字符: $invalidStr")
        }

        _wakeWords.clear()
        _wakeWords.addAll(words)

        return try {
            keywordsFile.parentFile?.mkdirs()

            // Write keywords.txt in Sherpa-ONNX format
            // Format: "pinyin @ keyword" (same as original)
            val sortedWords = words.sortedByDescending { it.keyword.length }
            val lines = sortedWords.map { word ->
                val pinyin = chineseToPinyin(word.keyword)
                "$pinyin @ ${word.keyword}"
            }

            keywordsFile.writeText(lines.joinToString("\n"))
            Timber.d("Saved ${words.size} wake words to $keywordsFile")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to save wake words")
            throw e
        }
    }

    /**
     * Validate that a wake word's characters are all in tokens.txt
     * @param keyword The Chinese keyword to validate
     * @return true if all characters are supported by Sherpa
     */
    private fun validateWakeWord(keyword: String): Boolean {
        initialize()
        // All characters must be in validChars
        return keyword.all { validChars.contains(it.toString()) }
    }

    /**
     * Get the response phrase for a detected keyword
     */
    fun getResponseForKeyword(keyword: String): String {
        _wakeWords.find { it.keyword == keyword }?.let { return it.response }
        _wakeWords.find { keyword.contains(it.keyword) }?.let { return it.response }
        return "我在"
    }

    /**
     * Chinese to Pinyin conversion using dynamic PinyinConverter.
     * Falls back to the original hardcoded map for compatibility.
     */
    private fun chineseToPinyin(chinese: String): String {
        // First try dynamic converter
        val dynamicPinyin = pinyinConverter.convert(chinese)
        if (dynamicPinyin.isNotEmpty() && !dynamicPinyin.contains(chinese.first().toString())) {
            return dynamicPinyin
        }

        // Fallback to hardcoded map for known wake words
        val pinyinMap = mapOf(
            "小爱同学" to "x iǎo ài tóng xué",
            "小爱" to "x iǎo ài",
            "你好问问" to "n ǐ h ǎo w èn w èn",
            "你好军哥" to "n ǐ h ǎo j ūn g ē",
            "蛋哥蛋哥" to "d àn g ē d àn g ē",
            "小艺小艺" to "x iǎo y ì x iǎo y ì",
            "小米小米" to "x iǎo m ǐ x iǎo m ǐ",
            "林美丽" to "l ín m ěi l ì",
            "你好西西" to "n ǐ h ǎo x ī x ī",
            "西西" to "x ī x ī"
        )
        return pinyinMap[chinese] ?: dynamicPinyin
    }

    /**
     * Get the keywords file path for SherpaKWS reload
     */
    fun getKeywordsFilePath(): String = keywordsFile.absolutePath
}