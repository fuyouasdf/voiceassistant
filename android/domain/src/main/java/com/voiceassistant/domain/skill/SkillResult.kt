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

package com.voiceassistant.domain.skill

/**
 * Result returned by a skill after handling input.
 */
sealed class SkillResult {
    /**
     * Skill handled the input and produced a response.
     */
    data class Success(
        val response: String,
        val consumed: Boolean = true
    ) : SkillResult()

    /**
     * Skill did not handle the input (e.g., keywords didn't match).
     * The input should be passed to other skills.
     */
    data object NotHandled : SkillResult()

    /**
     * Skill encountered an error.
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : SkillResult()
}