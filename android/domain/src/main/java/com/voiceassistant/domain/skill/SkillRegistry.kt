package com.voiceassistant.domain.skill

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry for managing and dispatching to skills.
 *
 * Skills are registered at startup and can be matched against
 * incoming voice/text input. The registry handles priority
 * ordering and delegation to appropriate skills.
 *
 * Usage:
 * ```
 * // Register a skill
 * registry.register(mySkill)
 *
 * // Dispatch input to matching skill
 * val result = registry.dispatch(input, context)
 * ```
 */
@Singleton
class SkillRegistry @Inject constructor() {

    private val skills = mutableListOf<Skill>()

    /**
     * Registered skills sorted by priority (highest first).
     */
    val registeredSkills: List<Skill>
        get() = skills.toList()

    /**
     * Register a skill.
     * Skills are sorted by priority when dispatching.
     *
     * @param skill The skill to register
     */
    fun register(skill: Skill) {
        Timber.d("Registering skill: ${skill.name} with keywords: ${skill.keywords}")
        skills.add(skill)
        // Re-sort by priority (descending)
        skills.sortByDescending { it.priority }
    }

    /**
     * Unregister a skill by name.
     *
     * @param skillName The name of the skill to unregister
     * @return true if the skill was found and removed
     */
    fun unregister(skillName: String): Boolean {
        val removed = skills.removeAll { it.name == skillName }
        if (removed) {
            Timber.d("Unregistered skill: $skillName")
        }
        return removed
    }

    /**
     * Get a skill by name.
     *
     * @param name The skill name
     * @return The skill, or null if not found
     */
    fun getSkill(name: String): Skill? {
        return skills.find { it.name == name }
    }

    /**
     * Dispatch input to the first matching skill.
     *
     * Skills are tried in priority order (highest first).
     * A skill matches if its canHandle() returns true.
     *
     * @param input Normalized input text (lowercase, trimmed)
     * @param rawInput Original input text
     * @param context Skill context with repositories
     * @return The result from the first matching skill, or NotHandled
     */
    suspend fun dispatch(input: String, rawInput: String, context: SkillContext): SkillResult {
        val normalizedInput = input.lowercase().trim()

        for (skill in skills) {
            if (skill.canHandle(normalizedInput)) {
                Timber.d("Skill '${skill.name}' matched for input: $normalizedInput")
                try {
                    val result = skill.handle(normalizedInput, rawInput, context)
                    if (result is SkillResult.Success && result.consumed) {
                        return result
                    }
                    if (result is SkillResult.Error) {
                        Timber.w("Skill '${skill.name}' error: ${result.message}")
                        return result
                    }
                    // NotHandled or Success with consumed=false, try next skill
                    Timber.d("Skill '${skill.name}' returned NotHandled, trying next skill")
                } catch (e: Exception) {
                    Timber.e(e, "Skill '${skill.name}' threw exception")
                    return SkillResult.Error("技能执行失败: ${e.message}", e)
                }
            }
        }

        Timber.d("No skill handled input: $normalizedInput")
        return SkillResult.NotHandled
    }

    /**
     * Find all skills that can handle the given input.
     *
     * @param input Normalized input text
     * @return List of matching skills in priority order
     */
    fun findMatchingSkills(input: String): List<Skill> {
        val normalizedInput = input.lowercase().trim()
        return skills.filter { it.canHandle(normalizedInput) }
    }

    /**
     * Clear all registered skills.
     */
    fun clear() {
        Timber.d("Clearing all registered skills")
        skills.clear()
    }
}