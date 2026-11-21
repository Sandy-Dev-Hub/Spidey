package com.spidey.assistant.skills

/**
 * Registry of known per-app skills.
 */
class AppSkillRegistry {
    private val skills = mutableMapOf<String, AppSkill>()

    fun register(skill: AppSkill) {
        skills[skill.packageName] = skill
    }

    fun getSkill(packageName: String): AppSkill? = skills[packageName]

    fun findSkillForApp(appNameOrPkg: String): AppSkill? {
        val lower = appNameOrPkg.lowercase()
        return skills.values.firstOrNull { skill ->
            skill.packageName.equals(lower, ignoreCase = true) ||
            skill.packageName.contains(lower, ignoreCase = true)
        }
    }

    fun getAllSkills(): List<AppSkill> = skills.values.toList()
}
