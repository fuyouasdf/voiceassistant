package com.voiceassistant.data.remote

import java.security.MessageDigest

/**
 * Subsonic API authentication helper.
 *
 * Implements the Subsonic token scheme:
 *   token = MD5(password + salt)
 *   salt = random 6-char string
 *
 * Extracted from MusicRepositoryImpl and LLMRepositoryImpl to avoid duplication.
 */
object SubsonicAuthHelper {

    /**
     * Generate auth token and salt for Subsonic API.
     * @param password The user's password
     * @return SubsonicAuth containing username (placeholder), token, and salt
     */
    fun generateToken(password: String): SubsonicAuth {
        val salt = generateSalt()
        val token = md5(password + salt)
        return SubsonicAuth(
            username = "",  // Caller should fill in
            token = token,
            salt = salt
        )
    }

    private fun generateSalt(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
