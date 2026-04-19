package com.voiceassistant.core.skill

import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.model.IntentType
import com.voiceassistant.domain.skill.Skill
import com.voiceassistant.domain.skill.SkillContext
import com.voiceassistant.domain.skill.SkillResult
import com.voiceassistant.domain.usecase.HandleQueryUseCase
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in skill for handling information query commands.
 *
 * Handles:
 * - Time queries ("现在几点了")
 * - Date queries ("今天几号")
 * - Weather queries (delegates to LLM)
 * - General knowledge queries (delegates to LLM)
 */
@Singleton
class QuerySkill @Inject constructor() : Skill {

    override val name: String = "query"

    override val keywords: List<String> = listOf(
        "天气", "时间", "日期", "查询", "搜索", "是什么", "在哪里"
    )

    override val priority: Int = 5

    override suspend fun handle(input: String, rawInput: String, context: SkillContext): SkillResult {
        // First check for time/date queries (fast local path)
        getTimeQueryResponse(input)?.let {
            return SkillResult.Success(it)
        }

        // Fall back to LLM-based query handling
        val queryUseCase = context.handleQueryUseCase
        if (queryUseCase == null) {
            return SkillResult.Success(getFallbackResponse(input))
        }

        val intent = Intent(IntentType.QUERY, query = rawInput)
        val result = queryUseCase.execute(intent)
        return SkillResult.Success(result)
    }

    private fun getTimeQueryResponse(rawText: String): String? {
        val text = rawText.lowercase().trim()
        if (text.isEmpty()) return null

        val now = LocalDateTime.now()

        if (text.contains("几点") || text.contains("几时") || text.contains("当前时间") ||
            (text.contains("现在") && text.contains("时间"))) {
            return "现在是${now.hour}点${now.minute}分"
        }

        if (text.contains("星期几") || text.contains("周几")) {
            return "今天是${weekdayToChinese(now.dayOfWeek.value)}"
        }

        val askDate = text.contains("几号") || text.contains("几月几号") || text.contains("日期")
        if (!askDate) return null

        val offset = when {
            text.contains("前天") -> -2L
            text.contains("昨天") -> -1L
            text.contains("明天") -> 1L
            text.contains("后天") -> 2L
            else -> 0L
        }
        val date = now.toLocalDate().plusDays(offset)
        return "${date.monthValue}月${date.dayOfMonth}号，${weekdayToChinese(date.dayOfWeek.value)}"
    }

    private fun weekdayToChinese(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "星期一"
            2 -> "星期二"
            3 -> "星期三"
            4 -> "星期四"
            5 -> "星期五"
            6 -> "星期六"
            7 -> "星期日"
            else -> "未知"
        }
    }

    private fun getFallbackResponse(query: String): String {
        return when {
            query.contains("天气") -> "抱歉，我需要联网才能查询天气"
            query.contains("时间") || query.contains("日期") -> "抱歉，我需要联网才能查询时间"
            else -> "需要联网才能回答这个问题"
        }
    }
}