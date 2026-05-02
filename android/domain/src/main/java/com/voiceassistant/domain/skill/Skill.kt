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
 * Interface for skill plugins that can handle voice/text commands.
 *
 * Skills are self-contained units that handle specific types of commands.
 * They are triggered by keywords and can be registered at app startup.
 *
 * Example skills:
 * - MusicSkill: handles "播放音乐", "暂停" commands
 * - VolumeSkill: handles "音量调大", "静音" commands
 * - SmartHomeSkill: handles "打开灯", "关闭空调" commands (extensible)
 * - CalendarSkill: handles "提醒我明天开会" commands (extensible)
 */
interface Skill {
    /**
     * Human-readable name of the skill.
     */
    val name: String

    /**
     * Keywords that trigger this skill.
     * When any of these keywords is found in the input,
     * the skill's handle method will be called.
     */
    val keywords: List<String>

    /**
     * Priority of the skill when multiple skills match.
     * Higher priority skills are tried first.
     * Default is 0.
     */
    val priority: Int get() = 0

    /**
     * Handle the input text and produce a response.
     *
     * @param input The normalized input text (lowercase, trimmed)
     * @param rawInput The original input text (may contain case and whitespace)
     * @param context Context providing access to repositories and use cases
     * @return SkillResult indicating success, not handled, or error
     */
    suspend fun handle(input: String, rawInput: String, context: SkillContext): SkillResult

    /**
     * Check if this skill can handle the given input based on keywords.
     * This is a fast check before calling handle().
     *
     * @param input The normalized input text
     * @return true if this skill's keywords match the input
     */
    fun canHandle(input: String): Boolean {
        return keywords.any { keyword -> input.contains(keyword) }
    }
}