# 小爱同学风格语音助手设计方案

> **版本**: 1.3
> **日期**: 2026-03-29
> **项目**: voice-assistant
> **目标**: 将现有语音助手增强为类似小爱同学的智能语音交互应用

---

## 一、当前项目分析

### 已完成模块 ✅

| 模块 | 状态 | 说明 |
|------|------|------|
| 语音管道 (KWS/VAD/ASR/TTS) | ✅ | Sherpa-ONNX 完整实现 |
| Jellyfin 音乐服务 | ✅ | 搜索 + 播放 |
| LLM 对话 (DeepSeek) | ✅ | 可配置 API Key |
| 意图路由 (IntentRouter) | ✅ | 基础版规则匹配 |
| 前台服务 + 通知 | ✅ | 后台运行支持 |
| MainActivity UI | ✅ | 对话风格界面 |

### 待增强模块 ⚠️

| 模块 | 现状 | 目标 |
|------|------|------|
| 智能家居控制 | ❌ 未实现 | 支持灯、空调、电视等设备 |
| 闹钟/提醒功能 | ❌ 未实现 | 语音设置闹钟和提醒 |
| 天气查询 | ⚠️ 依赖 LLM | 专业天气技能 |
| 意图扩展性 | ⚠️ 硬编码 | Skills 系统 |
| 对话上下文 | ❌ 无记忆 | 多轮对话支持 |
| 技能/Skills 系统 | ❌ 无 | 模块化技能管理 |

---

## 二、整体架构设计

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           小爱同学风格架构                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        Skills Layer (技能层)                          │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐      │   │
│  │  │ SmartHome│ │  Alarm  │ │ Weather │ │  News  │ │  Music  │      │   │
│  │  │  技能   │ │  技能   │ │  技能   │ │  技能   │ │  技能   │      │   │
│  │  └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘      │   │
│  └───────┼────────────┼───────────┼────────────┼────────────┼───────────┘   │
│          │            │           │            │            │               │
│          ▼            ▼           ▼            ▼            ▼               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    IntentRouter (意图路由)                            │   │
│  │  · 规则匹配 (Rule-based)     · LLM 智能分类                         │   │
│  │  · 上下文管理 (Context)      · 多意图分离                            │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                       │
│                                    ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    ConversationManager (对话管理)                     │   │
│  │  · 对话上下文记忆     · 人格一致性     · 情感反馈                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                       │
│                                    ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      VoicePipeline (语音管道)                        │   │
│  │  KWS → VAD → ASR → Intent → LLM → TTS                              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 与现有架构的对应关系

```
现有架构                          扩展后架构
─────────────────────────────────────────────────────
VoicePipeline                 →  VoicePipeline (保持)
IntentRouter                 →  IntentRouter + Skills 系统
无                            →  ConversationManager (新增)
MusicRepository              →  MusicSkill (技能化)
LLMRepository               →  LLM 降级为 Skills 路由的兜底
```

---

## 三、核心模块设计

### 3.1 Skill 接口定义

**文件**: `core/src/main/java/com/voiceassistant/core/skill/Skill.kt`

```kotlin
package com.voiceassistant.core.skill

/**
 * 技能接口 - 所有技能的基类
 */
interface Skill {
    /** 技能名称 */
    val name: String

    /** 触发关键词列表 */
    val keywords: List<String>

    /** 技能描述 */
    val description: String

    /**
     * 执行技能
     * @param context 技能执行上下文
     * @return 技能执行结果
     */
    suspend fun execute(context: SkillContext): SkillResult
}

/**
 * 技能执行上下文
 */
data class SkillContext(
    val text: String,                        // 原始用户文本
    val slots: Map<String, String>,          // 提取的实体槽位
    val conversationHistory: List<ConversationTurn>, // 对话历史
    val userProfile: UserProfile?            // 用户画像
)

/**
 * 技能执行结果
 */
data class SkillResult(
    val response: String,           // TTS 回复文本
    val action: SkillAction? = null, // 执行的动作
    val requiresFollowUp: Boolean = false  // 是否需要追问
)

/**
 * 技能执行的动作
 */
sealed class SkillAction {
    // 音乐相关
    data class PlayMusic(
        val query: String,
        val artist: String? = null
    ) : SkillAction()

    // 闹钟相关
    data class SetAlarm(
        val time: java.time.LocalTime,
        val label: String? = null
    ) : SkillAction()

    data class CancelAlarm(val alarmId: String) : SkillAction()

    // 设备控制
    data class ControlDevice(
        val deviceType: DeviceType,
        val action: DeviceAction,
        val value: Int? = null
    ) : SkillAction()

    // 天气查询
    data class QueryWeather(val location: String? = null) : SkillAction()

    // 新闻
    data class ReadNews(val category: String? = null) : SkillAction()

    // 通用
    data class LLMQuery(val query: String) : SkillAction()
}

/**
 * 设备类型
 */
enum class DeviceType {
    LIGHT,    // 灯
    AC,       // 空调
    TV,       // 电视
    FAN,      // 风扇
    SWITCH,   // 开关
    CURTAIN,  // 窗帘
    SPEAKER   // 音箱
}

/**
 * 设备动作
 */
enum class DeviceAction {
    ON, OFF, TOGGLE, UP, DOWN, SET
}

/**
 * 对话轮次
 */
data class ConversationTurn(
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class Role { USER, ASSISTANT }

/**
 * 用户画像
 */
data class UserProfile(
    val name: String? = null,
    val morningAlarm: java.time.LocalTime? = null,
    val favoriteMusic: List<String> = emptyList(),
    val smartHomeDevices: Map<String, DeviceState> = emptyMap()
)

data class DeviceState(
    val type: DeviceType,
    val isOn: Boolean,
    val value: Int? = null  // 亮度、温度等
)
```

### 3.2 SmartHomeSkill (智能家居技能)

**文件**: `core/src/main/java/com/voiceassistant/core/skill/SmartHomeSkill.kt`

```kotlin
package com.voiceassistant.core.skill

/**
 * 智能家居技能
 *
 * 支持的命令示例：
 * - "打开灯"
 * - "关闭客厅的灯"
 * - "空调调到26度"
 * - "把灯调亮一点"
 * - "打开电视"
 */
class SmartHomeSkill(
    private val deviceRepository: SmartDeviceRepository
) : Skill {

    override val name = "智能家居"
    override val keywords = listOf(
        "打开", "关闭", "关", "开", "调", "灯", "空调", "电视",
        "风扇", "窗帘", "开关", "亮", "暗", "热", "冷"
    )
    override val description = "控制家中的智能设备"

    // 设备类型关键词映射
    private val devicePatterns = mapOf(
        "灯" to DeviceType.LIGHT,
        "灯泡" to DeviceType.LIGHT,
        "空调" to DeviceType.AC,
        "冷气" to DeviceType.AC,
        "电视" to DeviceType.TV,
        "电视机" to DeviceType.TV,
        "风扇" to DeviceType.FAN,
        "窗帘" to DeviceType.CURTAIN,
        "开关" to DeviceType.SWITCH,
        "音箱" to DeviceType.SPEAKER
    )

    override suspend fun execute(context: SkillContext): SkillResult {
        val text = context.text.lowercase()

        // 1. 提取设备类型
        val deviceType = extractDeviceType(text)
            ?: return SkillResult(
                "你想控制什么设备？可以说灯、空调、电视等",
                requiresFollowUp = true
            )

        // 2. 提取动作
        val action = extractAction(text)

        // 3. 提取数值 (如调节亮度/温度)
        val value = extractNumber(text)

        // 4. 执行设备控制
        return try {
            val success = deviceRepository.control(deviceType, action, value)
            val response = buildResponse(deviceType, action, value, success)
            SkillResult(response, action = SkillAction.ControlDevice(deviceType, action, value))
        } catch (e: Exception) {
            SkillResult("抱歉，控制设备失败了", requiresFollowUp = false)
        }
    }

    private fun extractDeviceType(text: String): DeviceType? {
        return devicePatterns.entries.find { text.contains(it.key) }?.value
    }

    private fun extractAction(text: String): DeviceAction {
        return when {
            text.contains("打开") || text.contains("开") -> DeviceAction.ON
            text.contains("关闭") || text.contains("关") -> DeviceAction.OFF
            text.contains("调高") || text.contains("亮一点") ||
                text.contains("热一点") || text.contains("大声") -> DeviceAction.UP
            text.contains("调低") || text.contains("暗一点") ||
                text.contains("冷一点") || text.contains("小声") -> DeviceAction.DOWN
            text.contains("设为") || text.contains("调到") -> DeviceAction.SET
            else -> DeviceAction.TOGGLE
        }
    }

    private fun extractNumber(text: String): Int? {
        val regex = Regex("\\d+")
        return regex.find(text)?.value?.toIntOrNull()
    }

    private fun buildResponse(
        deviceType: DeviceType,
        action: DeviceAction,
        value: Int?,
        success: Boolean
    ): String {
        val deviceName = getDeviceName(deviceType)

        if (!success) {
            return "抱歉，无法$deviceName"
        }

        return when (action) {
            DeviceAction.ON -> "好的，已打开$deviceName"
            DeviceAction.OFF -> "好的，已关闭$deviceName"
            DeviceAction.UP -> {
                when (deviceType) {
                    DeviceType.LIGHT -> "好的，把${deviceName}调亮了"
                    DeviceType.AC -> "好的，温度调高了"
                    DeviceType.FAN -> "好的，风速调大了"
                    else -> "${deviceName}已调大"
                }
            }
            DeviceAction.DOWN -> {
                when (deviceType) {
                    DeviceType.LIGHT -> "好的，把${deviceName}调暗了"
                    DeviceType.AC -> "好的，温度调低了"
                    DeviceType.FAN -> "好的，风速调小了"
                    else -> "${deviceName}已调小"
                }
            }
            DeviceAction.SET -> {
                value?.let { "好的，${deviceName}设为$it" }
                    ?: "${deviceName}已调整"
            }
            DeviceAction.TOGGLE -> "${deviceName}已切换"
        }
    }

    private fun getDeviceName(type: DeviceType): String = when (type) {
        DeviceType.LIGHT -> "灯"
        DeviceType.AC -> "空调"
        DeviceType.TV -> "电视"
        DeviceType.FAN -> "风扇"
        DeviceType.CURTAIN -> "窗帘"
        DeviceType.SWITCH -> "开关"
        DeviceType.SPEAKER -> "音箱"
    }
}

/**
 * 智能设备仓库接口
 */
interface SmartDeviceRepository {
    /**
     * 控制设备
     * @return 是否成功
     */
    suspend fun control(
        deviceType: DeviceType,
        action: DeviceAction,
        value: Int?
    ): Boolean

    /**
     * 获取设备状态
     */
    suspend fun getDeviceState(deviceId: String): DeviceState?

    /**
     * 发现可用设备
     */
    suspend fun discoverDevices(): List<DiscoveredDevice>
}

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    val room: String? = null  // 所在房间
)
```

### 3.3 AlarmSkill (闹钟技能)

**文件**: `core/src/main/java/com/voiceassistant/core/skill/AlarmSkill.kt`

```kotlin
package com.voiceassistant.core.skill

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 闹钟技能
 *
 * 支持的命令示例：
 * - "设置一个明天早上7点的闹钟"
 * - "帮我定个闹钟"
 * - "明天8点叫我起床"
 * - "取消闹钟"
 */
class AlarmSkill(
    private val alarmManager: AlarmScheduler
) : Skill {

    override val name = "闹钟"
    override val keywords = listOf("闹钟", "提醒", "定时", "几点", "叫醒", "起床")
    override val description = "设置和管理闹钟"

    // 时间表达式正则
    private val simpleTimePattern = Regex("(\\d{1,2})点(\\d{1,2})?")
    private val colonTimePattern = Regex("(\\d{1,2})[:：](\\d{1,2})")

    override suspend fun execute(context: SkillContext): SkillResult {
        val text = context.text
        val slots = context.slots

        // 解析时间
        val alarmTime = slots["time"]?.let { parseTime(it) }
            ?: extractTimeFromText(text)

        // 检查是否是取消闹钟
        if (text.contains("取消") || text.contains("删除")) {
            return handleCancel()
        }

        // 检查是否是查询闹钟
        if (text.contains("还有") || text.contains("查看") || text.contains("有几个")) {
            return handleQuery()
        }

        if (alarmTime == null) {
            return SkillResult(
                "请问你想设置几点的闹钟？比如下午3点",
                requiresFollowUp = true
            )
        }

        // 提取标签
        val label = extractLabel(text)

        // 设置闹钟
        val alarmId = alarmManager.setAlarm(alarmTime, label)

        val timeStr = formatTime(alarmTime)
        val labelStr = label?.let { "，标签是$it" } ?: ""

        return SkillResult(
            "好的，已设置${timeStr}的闹钟$labelStr",
            action = SkillAction.SetAlarm(alarmTime, label)
        )
    }

    private fun parseTime(timeStr: String): LocalTime? {
        // 尝试 "HH:mm" 格式
        colonTimePattern.find(timeStr)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        }

        // 尝试 "H点M分" 格式
        simpleTimePattern.find(timeStr)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        }

        return null
    }

    private fun extractTimeFromText(text: String): LocalTime? {
        // 处理自然语言时间
        val isPM = text.contains("下午") || text.contains("晚上")
        val isAM = text.contains("早上") || text.contains("上午")

        simpleTimePattern.find(text)?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: 0

            // 转换12小时制到24小时制
            if (isPM && hour < 12) hour += 12
            if (isAM && hour == 12) hour = 0

            return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        }

        return null
    }

    private fun extractLabel(text: String): String? {
        val labelKeywords = listOf("叫", "起床", "开会", "吃饭", "睡觉", "提醒")
        return labelKeywords.find { text.contains(it) }
    }

    private fun formatTime(time: LocalTime): String {
        val formatter = DateTimeFormatter.ofPattern("h点m分")
        return time.format(formatter)
    }

    private suspend fun handleCancel(): SkillResult {
        val alarms = alarmManager.getActiveAlarms()
        if (alarms.isEmpty()) {
            return SkillResult("目前没有设置任何闹钟")
        }
        // 默认取消最近的一个
        alarmManager.cancelAlarm(alarms.first().id)
        return SkillResult("好的，已取消最近的闹钟")
    }

    private suspend fun handleQuery(): SkillResult {
        val alarms = alarmManager.getActiveAlarms()
        if (alarms.isEmpty()) {
            return SkillResult("目前没有设置任何闹钟")
        }

        val alarmList = alarms.joinToString("、") { alarm ->
            "${formatTime(alarm.time)}${alarm.label?.let { "($it)" } ?: ""}"
        }
        return SkillResult("你当前有${alarms.size}个闹钟：$alarmList")
    }
}

/**
 * 闹钟调度器接口
 */
interface AlarmScheduler {
    /**
     * 设置闹钟
     * @return 闹钟ID
     */
    suspend fun setAlarm(time: LocalTime, label: String?): String

    /**
     * 取消闹钟
     */
    suspend fun cancelAlarm(alarmId: String): Boolean

    /**
     * 获取活跃闹钟列表
     */
    suspend fun getActiveAlarms(): List<AlarmInfo>
}

data class AlarmInfo(
    val id: String,
    val time: LocalTime,
    val label: String?,
    val isEnabled: Boolean = true
)
```

### 3.4 WeatherSkill (天气技能)

**文件**: `core/src/main/java/com/voiceassistant/core/skill/WeatherSkill.kt`

```kotlin
package com.voiceassistant.core.skill

/**
 * 天气技能
 *
 * 支持的命令示例：
 * - "今天天气怎么样"
 * - "明天会下雨吗"
 * - "北京现在多少度"
 * - "周末适合出门吗"
 */
class WeatherSkill(
    private val weatherApi: WeatherApi
) : Skill {

    override val name = "天气"
    override val keywords = listOf("天气", "气温", "温度", "下雨", "下雪", "晴", "阴", "冷", "热", "度")
    override val description = "查询天气信息"

    override suspend fun execute(context: SkillContext): SkillResult {
        val text = context.text

        // 提取地点
        val location = extractLocation(text)

        // 提取时间 (今天/明天/周末)
        val timeRange = extractTimeRange(text)

        return try {
            val weather = weatherApi.getWeather(location ?: "当前地区", timeRange)
            val response = buildWeatherResponse(weather, timeRange)
            SkillResult(response, action = SkillAction.QueryWeather(location))
        } catch (e: Exception) {
            Timber.e(e, "获取天气失败")
            SkillResult("抱歉，无法获取天气信息，请稍后重试")
        }
    }

    private fun extractLocation(text: String): String? {
        // 常见地点模式
        val locationPatterns = listOf(
            Regex("(在北京|上海|广州|深圳|杭州|成都|武汉|西安|南京)\\s*"),
            Regex("(北京|上海|广州|深圳|杭州|成都|武汉|西安|南京|重庆)的天气"),
            Regex("(.+)的天气")
        )

        for (pattern in locationPatterns) {
            pattern.find(text)?.let { match ->
                val location = match.groupValues.getOrNull(1)
                    ?: match.groupValues.getOrNull(2)
                if (!location.isNullOrBlank() && location != "的天气") {
                    return location.trim()
                }
            }
        }

        return null  // 使用默认位置
    }

    private fun extractTimeRange(text: String): TimeRange {
        return when {
            text.contains("今天") || text.contains("现在") -> TimeRange.TODAY
            text.contains("明天") -> TimeRange.TOMORROW
            text.contains("后天") -> TimeRange.AFTER_TOMORROW
            text.contains("周末") -> TimeRange.WEEKEND
            text.contains("下周") -> TimeRange.NEXT_WEEK
            else -> TimeRange.TODAY
        }
    }

    private fun buildWeatherResponse(weather: WeatherInfo, timeRange: TimeRange): String {
        return buildString {
            append("${weather.city}：")

            when (timeRange) {
                TimeRange.TODAY -> {
                    append("${weather.condition}，")
                    append("气温${weather.tempLow}到${weather.tempHigh}度")
                    if (weather.windSpeed > 4) {
                        append("，风力${weather.windSpeed}级")
                    }
                    if (weather.humidity > 70) {
                        append("，湿度较大")
                    }
                    weather.precipitation?.let { precip ->
                        if (precip > 30) {
                            append("，${precip}%降水概率")
                        }
                    }
                }
                TimeRange.TOMORROW -> {
                    append("明天${weather.condition}，")
                    append("${weather.tempLow}到${weather.tempHigh}度")
                }
                TimeRange.AFTER_TOMORROW -> {
                    append("后天${weather.condition}，")
                    append("${weather.tempLow}到${weather.tempHigh}度")
                }
                TimeRange.WEEKEND -> {
                    append("周末整体${weather.condition}，")
                    append("气温${weather.tempLow}到${weather.tempHigh}度")
                }
                TimeRange.NEXT_WEEK -> {
                    append("下周天气以${weather.condition}为主，")
                    append("气温${weather.tempLow}到${weather.tempHigh}度")
                }
            }

            // 添加出行建议
            when {
                weather.tempHigh > 35 -> append("，天气较热，注意防暑")
                weather.tempLow < 0 -> append("，气温较低，注意保暖")
                weather.precipitation != null && weather.precipitation > 60 ->
                    append("，建议带伞")
                weather.windSpeed > 5 -> append("，风较大，注意防护")
            }
        }
    }
}

enum class TimeRange {
    TODAY, TOMORROW, AFTER_TOMORROW, WEEKEND, NEXT_WEEK
}

/**
 * 天气信息
 */
data class WeatherInfo(
    val city: String,
    val condition: String,       // 晴/多云/小雨/中雨/雷阵雨等
    val tempLow: Int,            // 最低气温
    val tempHigh: Int,           // 最高气温
    val humidity: Int? = null,   // 湿度
    val windSpeed: Int? = null,  // 风速 (级)
    val precipitation: Int? = null // 降水概率
)

/**
 * 天气API接口
 */
interface WeatherApi {
    suspend fun getWeather(location: String, timeRange: TimeRange = TimeRange.TODAY): WeatherInfo
}
```

### 3.5 ConversationManager (对话管理器)

**文件**: `core/src/main/java/com/voiceassistant/core/conversation/ConversationManager.kt`

```kotlin
package com.voiceassistant.core.conversation

import com.voiceassistant.core.skill.ConversationTurn
import com.voiceassistant.core.skill.Role
import com.voiceassistant.core.skill.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 对话管理器
 *
 * 负责：
 * - 维护对话历史
 * - 构建带上下文的 LLM prompt
 * - 管理用户画像
 * - 个性化回复
 */
@Singleton
class ConversationManager @Inject constructor() {

    private val history = mutableListOf<ConversationTurn>()
    private val maxHistorySize = 10

    private var userProfile: UserProfile? = null

    // 性格配置
    private val personality = Personality(
        name = "小爱",
        greeting = "你好，我是小爱同学，有什么可以帮你的吗？",
        defaultResponses = mapOf(
            "听不懂" to "抱歉，我没听清楚，能再说一遍吗？",
            "重复" to "不好意思，能再说一次吗？",
            "感谢" to "不客气！还有什么需要帮忙的吗？",
            "道歉" to "没关系呢~",
            "再见" to "再见啦，有事随时叫我！"
        )
    )

    /**
     * 添加用户对话
     */
    fun addUserMessage(text: String) {
        addTurn(Role.USER, text)
    }

    /**
     * 添加助手回复
     */
    fun addAssistantMessage(text: String) {
        val personalized = personalize(text)
        addTurn(Role.ASSISTANT, personalized)
    }

    private fun addTurn(role: Role, content: String) {
        history.add(ConversationTurn(role, content))
        if (history.size > maxHistorySize) {
            history.removeAt(0)
        }
    }

    /**
     * 获取对话历史
     */
    fun getHistory(): List<ConversationTurn> = history.toList()

    /**
     * 获取带上下文的 LLM prompt
     */
    fun buildContextualPrompt(userInput: String): String {
        return buildString {
            // 系统提示
            appendLine("你是小爱同学，一个温柔可爱的智能语音助手。")
            appendLine("性格特点：活泼、亲切、乐于助人，声音甜美。")
            appendLine()

            // 用户信息
            userProfile?.name?.let {
                appendLine("用户名叫$it。")
            }
            appendLine()

            // 对话历史
            appendLine("【对话历史】")
            history.takeLast(6).forEach { turn ->
                val roleStr = if (turn.role == Role.USER) "用户" else "小爱"
                appendLine("$roleStr：${turn.content}")
            }
            appendLine()

            // 当前输入
            appendLine("用户：$userInput")
            append("小爱：")
        }
    }

    /**
     * 获取问候语
     */
    fun getGreeting(): String {
        val hour = java.time.LocalTime.now().hour
        return when {
            hour < 6 -> "凌晨好呀，这么晚还没睡呢？"
            hour < 9 -> "早上好！新的一天开始了~"
            hour < 12 -> "上午好！有什么需要帮忙的吗？"
            hour < 14 -> "中午好！吃饭了吗？"
            hour < 18 -> "下午好！"
            hour < 21 -> "晚上好！今天过得怎么样？"
            else -> "夜深了，早点休息哦~"
        }
    }

    /**
     * 个性化回复处理
     */
    private fun personalize(response: String): String {
        // 检查是否匹配默认回复
        for ((pattern, reply) in personality.defaultResponses) {
            if (response.contains(pattern)) {
                return reply
            }
        }

        // 添加小爱风格的语气词
        return when {
            response.startsWith("抱歉") || response.startsWith("对不起") ->
                response + "呢~"
            response.startsWith("好的") || response.startsWith("好的呀") ->
                if (response.endsWith("吗？") || response.endsWith("？")) response
                else response + "！还有什么需要帮忙的吗？"
            response.contains("错误") || response.contains("失败") ->
                response + "呢，再试试吧~"
            response.endsWith("。") && !response.endsWith("？") ->
                response + "有什么还需要帮忙的吗？"
            else -> response
        }
    }

    /**
     * 更新用户画像
     */
    fun updateUserProfile(profile: UserProfile) {
        userProfile = profile
    }

    /**
     * 获取用户画像
     */
    fun getUserProfile(): UserProfile? = userProfile

    /**
     * 清除对话历史
     */
    fun clearHistory() {
        history.clear()
    }
}

data class Personality(
    val name: String,
    val greeting: String,
    val defaultResponses: Map<String, String>  // 模式 -> 回复
)
```

### 3.6 IntentRouter (增强版)

**文件**: `core/src/main/java/com/voiceassistant/core/intent/IntentRouter.kt`

```kotlin
package com.voiceassistant.core.intent

import com.voiceassistant.core.conversation.ConversationManager
import com.voiceassistant.core.skill.*
import com.voiceassistant.domain.repository.LLMRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 意图路由 - 智能分发用户输入到对应技能
 *
 * 路由策略：
 * 1. 规则匹配优先 (技能关键词)
 * 2. 无法匹配时使用 LLM 分类
 * 3. 支持多意图拆分
 */
@Singleton
class IntentRouter @Inject constructor(
    private val skills: List<Skill>,
    private val llmRepository: LLMRepository?,
    private val conversationManager: ConversationManager
) {

    init {
        Timber.d("IntentRouter 初始化，注册 ${skills.size} 个技能")
        skills.forEach { skill ->
            Timber.d("  - ${skill.name}: ${skill.keywords.take(3).joinToString()}")
        }
    }

    /**
     * 处理用户输入
     *
     * @param text 用户说的话语
     * @return 最终回复文本
     */
    suspend fun handle(text: String): String {
        Timber.d("IntentRouter 处理: $text")

        // 添加到对话历史
        conversationManager.addUserMessage(text)

        // 1. 规则匹配技能
        val matchedSkill = findMatchedSkill(text)
        if (matchedSkill != null) {
            Timber.d("匹配到技能: ${matchedSkill.name}")
            val context = buildSkillContext(text)
            val result = matchedSkill.execute(context)

            // 如果需要追问且有 LLM
            if (result.requiresFollowUp && llmRepository != null) {
                val llmResponse = llmRepository.chat(text).getOrNull()
                    ?: "没听懂，请再说一遍"
                conversationManager.addAssistantMessage(llmResponse)
                return llmResponse
            }

            conversationManager.addAssistantMessage(result.response)
            return result.response
        }

        // 2. 无法匹配，使用 LLM 兜底
        Timber.d("无技能匹配，交给 LLM 处理")
        return handleWithLLM(text)
    }

    /**
     * 查找匹配的技能
     */
    private fun findMatchedSkill(text: String): Skill? {
        val lowerText = text.lowercase()

        // 遍历所有技能，找关键词匹配
        for (skill in skills) {
            if (skill.keywords.any { keyword -> lowerText.contains(keyword) }) {
                return skill
            }
        }

        return null
    }

    /**
     * 构建技能执行上下文
     */
    private fun buildSkillContext(text: String): SkillContext {
        return SkillContext(
            text = text,
            slots = extractSlots(text),
            conversationHistory = conversationManager.getHistory(),
            userProfile = conversationManager.getUserProfile()
        )
    }

    /**
     * 提取文本中的实体槽位
     */
    private fun extractSlots(text: String): Map<String, String> {
        val slots = mutableMapOf<String, String>()

        // 时间
        Regex("(\\d{1,2})点(\\d{1,2})?").find(text)?.let {
            slots["time"] = it.value
        }

        // 数字
        Regex("\\d+").find(text)?.let {
            slots["number"] = it.value
        }

        // 常见设备
        listOf("灯", "空调", "电视", "风扇", "窗帘").find { text.contains(it) }?.let {
            slots["device"] = it
        }

        // 地点
        Regex("(北京|上海|广州|深圳|杭州|成都|武汉|西安|南京)").find(text)?.let {
            slots["location"] = it.value
        }

        return slots
    }

    /**
     * 使用 LLM 处理
     */
    private suspend fun handleWithLLM(text: String): String {
        val llm = llmRepository
        if (llm == null) {
            val response = "这个问题我暂时回答不了，你可以试试问我天气、播放音乐或者控制智能家居~"
            conversationManager.addAssistantMessage(response)
            return response
        }

        // 构建带上下文的 prompt
        val prompt = conversationManager.buildContextualPrompt(text)

        val response = llm.chat(prompt).fold(
            onSuccess = { it },
            onFailure = { "抱歉，服务暂时不可用，请稍后重试" }
        )

        conversationManager.addAssistantMessage(response)
        return response
    }

    /**
     * 获取问候语
     */
    fun getGreeting(): String = conversationManager.getGreeting()
}
```

---

## 3.7 错误处理规范

### 错误分类体系

所有技能错误分为三类：

| 错误类型 | 说明 | 用户可见性 | 处理策略 |
|----------|------|-----------|----------|
| **TransientError** (瞬时错误) | 网络超时、服务器500、服务暂时不可用 | 可重试提示 | Retry 2x with exponential backoff |
| **PermanentError** (永久错误) | 设备离线、API Key无效、参数错误 | 明确错误信息 | 降级到兜底回答 |
| **FatalError** (致命错误) | 未捕获异常、数据损坏 | 友好错误 + 日志 | 上报，返回"出了点问题" |

### 错误传播链

```
Skill.execute()
    │
    ├─→ TransientError ──→ RetryInterceptor ──→ Retry 2x ──→ Still fail ──→ FallbackResponse
    │                                                    └─→ Success
    │
    ├─→ PermanentError ──→ SkillResult(errorMsg, requiresFollowUp=false)
    │
    └─→ FatalError ──→ SkillResult("抱歉，出了点问题，请稍后重试")
                   └─→ Timber.e(...) // 上报错误
```

### SkillResult 错误扩展

```kotlin
data class SkillResult(
    val response: String,
    val action: SkillAction? = null,
    val requiresFollowUp: Boolean = false,
    val error: SkillError? = null  // 新增：错误信息
)

sealed class SkillError {
    data class Transient(val retryAfter: Int? = null) : SkillError()
    data class Permanent(val code: ErrorCode, val message: String) : SkillError()
    data class Fatal(val throwable: Throwable) : SkillError()
}

enum class ErrorCode {
    DEVICE_OFFLINE,
    DEVICE_UNREACHABLE,
    API_KEY_INVALID,
    API_RATE_LIMITED,
    NETWORK_UNAVAILABLE,
    TIMEOUT,
    INVALID_PARAMS,
    UNKNOWN
}
```

### 各技能错误处理规范

#### SmartHomeSkill 错误处理

```kotlin
override suspend fun execute(context: SkillContext): SkillResult {
    return try {
        val success = deviceRepository.control(deviceType, action, value)
        if (!success) {
            SkillResult(
                "抱歉，${getDeviceName(deviceType)}没有响应",
                error = SkillError.Permanent(ErrorCode.DEVICE_UNREACHABLE, "设备无响应")
            )
        } else {
            SkillResult(response, action = SkillAction.ControlDevice(deviceType, action, value))
        }
    } catch (e: java.net.SocketTimeoutException) {
        Timber.w(e, "设备控制超时: $deviceType")
        SkillResult(
            "抱歉，${getDeviceName(deviceType)}响应超时，稍后重试吧",
            error = SkillError.Transient(5)
        )
    } catch (e: java.net.UnknownHostException) {
        Timber.w(e, "设备网络不可达: $deviceType")
        SkillResult(
            "抱歉，${getDeviceName(deviceType)}网络连接失败",
            error = SkillError.Permanent(ErrorCode.DEVICE_OFFLINE, "设备离线")
        )
    } catch (e: Exception) {
        Timber.e(e, "设备控制异常: $deviceType")
        SkillResult(
            "抱歉，控制${getDeviceName(deviceType)}失败了",
            error = SkillError.Fatal(e)
        )
    }
}
```

#### WeatherSkill 错误处理

```kotlin
override suspend fun execute(context: SkillContext): SkillResult {
    return try {
        val weather = weatherApi.getWeather(location ?: "当前地区", timeRange)
        SkillResult(buildWeatherResponse(weather, timeRange))
    } catch (e: WeatherApiException) when (e.code) {
        400 -> SkillResult("抱歉，位置信息无效", error = SkillError.Permanent(ErrorCode.INVALID_PARAMS, "无效位置"))
        401 -> SkillResult("抱歉，天气服务未授权", error = SkillError.Fatal(e))
        429 -> SkillResult("天气查询太频繁了，稍后再试", error = SkillError.Transient(60))
        500, 502, 503 -> SkillResult("天气服务暂时不可用，稍后重试", error = SkillError.Transient(30))
        else -> SkillResult("抱歉，无法获取天气信息", error = SkillError.Transient())
    } catch (e: java.net.UnknownHostException) {
        SkillResult("网络连接失败，请检查网络", error = SkillError.Permanent(ErrorCode.NETWORK_UNAVAILABLE, "网络不可用"))
    } catch (e: Exception) {
        Timber.e(e, "天气查询异常")
        SkillResult("抱歉，天气查询失败了", error = SkillError.Fatal(e))
    }
}

class WeatherApiException(val code: Int, override val message: String) : Exception(message)
```

#### AlarmSkill 错误处理

```kotlin
override suspend fun execute(context: SkillContext): SkillResult {
    val alarmTime = slots["time"]?.let { parseTime(it) } ?: extractTimeFromText(text)

    if (alarmTime == null) {
        return SkillResult(
            "请问你想设置几点的闹钟？比如下午3点",
            requiresFollowUp = true
        )
    }

    // 检查时间合理性
    if (alarmTime.isBefore(LocalTime.now())) {
        return SkillResult(
            "这个时间已经过了，设置明天的闹钟吗？",
            requiresFollowUp = true,
            error = SkillError.Permanent(ErrorCode.INVALID_PARAMS, "时间已过")
        )
    }

    return try {
        val alarmId = alarmManager.setAlarm(alarmTime, label)
        SkillResult("好的，已设置${formatTime(alarmTime)}的闹钟", action = SkillAction.SetAlarm(alarmTime, label))
    } catch (e: Exception) {
        Timber.e(e, "设置闹钟失败")
        SkillResult("抱歉，设置闹钟失败了", error = SkillError.Fatal(e))
    }
}
```

#### IntentRouter 错误处理增强

```kotlin
suspend fun handle(text: String): String {
    return try {
        conversationManager.addUserMessage(text)

        val matchedSkill = findMatchedSkill(text)
        if (matchedSkill != null) {
            val context = buildSkillContext(text)
            val result = matchedSkill.execute(context)

            if (result.requiresFollowUp && llmRepository != null) {
                val llmResponse = llmRepository.chat(text).getOrNull() ?: "没听懂，请再说一遍"
                conversationManager.addAssistantMessage(llmResponse)
                return llmResponse
            }

            conversationManager.addAssistantMessage(result.response)
            return result.response
        }

        return handleWithLLM(text)
    } catch (e: CancellationException) {
        throw e // 不捕获协程取消
    } catch (e: Exception) {
        Timber.e(e, "IntentRouter 处理异常: $text")
        val response = "抱歉，处理出错了，请稍后重试"
        conversationManager.addAssistantMessage(response)
        response
    }
}

private suspend fun handleWithLLM(text: String): String {
    val llm = llmRepository ?: run {
        val response = "这个问题我暂时回答不了，你可以试试问我天气、播放音乐或者控制智能家居~"
        conversationManager.addAssistantMessage(response)
        return response
    }

    val prompt = conversationManager.buildContextualPrompt(text)

    val result = llmRepository.chat(prompt)

    result.fold(
        onSuccess = { response ->
            conversationManager.addAssistantMessage(response)
            response
        },
        onFailure = { error ->
            Timber.w(error, "LLM 调用失败，尝试降级")
            val fallback = generateSimpleFallback(text)
            if (fallback != null) {
                conversationManager.addAssistantMessage(fallback)
                return fallback
            }
            val response = "这个问题我暂时回答不了，你可以试试问我天气、播放音乐或者控制智能家居~"
            conversationManager.addAssistantMessage(response)
            response
        }
    )
}

private fun generateSimpleFallback(text: String): String? {
    return when {
        text.contains("天气") -> "抱歉，天气服务暂时不可用"
        text.contains("播放") || text.contains("音乐") -> "抱歉，音乐服务暂时不可用"
        text.contains("打开") || text.contains("关闭") -> "抱歉，智能家居服务暂时不可用"
        else -> null
    }
}
```

---

## 3.8 离线行为规范

### 离线状态定义

| 状态 | 网络 | LLM | 天气API | Jellyfin | SmartHome |
|------|------|-----|---------|----------|-----------|
| **完全在线** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **LLM离线** | ✅ | ❌ | ✅ | ✅ | ✅ |
| **天气API离线** | ✅ | ✅ | ❌ | ✅ | ✅ |
| **Jellyfin离线** | ✅ | ✅ | ✅ | ❌ | ✅ |
| **完全离线** | ❌ | ❌ | ❌ | ❌ | ✅ (本地Hub) |

### 离线降级策略

```kotlin
/**
 * 离线降级管理器
 */
class OfflineDegradationManager(
    private val networkMonitor: NetworkMonitor,
    private val llmRepository: LLMRepository?,
    private val weatherApi: WeatherApi?,
    private val jellyfinClient: JellyfinClient?
) {
    val isOnline: Boolean get() = networkMonitor.isConnected()
    val isLLMAvailable: Boolean get() = isOnline && llmRepository != null
    val isWeatherAvailable: Boolean get() = isOnline && weatherApi != null
    val isJellyfinAvailable: Boolean get() = jellyfinClient?.isConnected() == true
}
```

### 各技能离线处理

#### WeatherSkill 离线处理

```kotlin
class WeatherSkill(
    private val weatherApi: WeatherApi,
    private val networkMonitor: NetworkMonitor
) : Skill {
    // ... keywords and other properties

    override suspend fun execute(context: SkillContext): SkillResult {
        if (!networkMonitor.isConnected()) {
            return SkillResult(
                "天气查询需要联网哦，打开网络后再试试吧~",
                error = SkillError.Permanent(ErrorCode.NETWORK_UNAVAILABLE, "网络离线")
            )
        }

        return try {
            val weather = weatherApi.getWeather(location ?: "当前地区", timeRange)
            SkillResult(buildWeatherResponse(weather, timeRange))
        } catch (e: WeatherApiException) when (e.code) {
            // ... normal error handling
        }
    }
}
```

#### MusicSkill 离线处理

```kotlin
class MusicSkill(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository?,
    private val jellyfinClient: JellyfinClient?
) : Skill {
    override suspend fun execute(context: SkillContext): SkillResult {
        // Jellyfin 本地网络检查
        if (jellyfinClient?.isConnected() != true) {
            return SkillResult(
                "音乐服务暂时不可用，请确保 Jellyfin 服务器正常运行~",
                error = SkillError.Permanent(ErrorCode.SERVICE_UNAVAILABLE, "Jellyfin离线")
            )
        }
        // ... rest of implementation
    }
}
```

#### SmartHomeSkill 离线处理

```kotlin
class SmartHomeSkill(
    private val deviceRepository: SmartDeviceRepository
) : Skill {
    override suspend fun execute(context: SkillContext): SkillResult {
        // 智能家居通常本地通信，不需要互联网
        // 但需要检查 Hub 连接状态
        return try {
            val success = deviceRepository.control(deviceType, action, value)
            if (!success) {
                return SkillResult(
                    "抱歉，无法连接到智能家居Hub，请检查网络连接",
                    error = SkillError.Permanent(ErrorCode.DEVICE_OFFLINE, "Hub离线")
                )
            }
            SkillResult(response, action = SkillAction.ControlDevice(deviceType, action, value))
        } catch (e: Exception) {
            // ... error handling
        }
    }
}
```

#### IntentRouter 完全离线兜底

```kotlin
private suspend fun handleOffline(text: String): String {
    // 完全离线时的兜底响应
    val lowerText = text.lowercase()

    return when {
        // 基础语音命令 - 离线可执行
        lowerText.contains("现在几点") || lowerText.contains("时间") -> {
            val now = LocalTime.now()
            "现在是${now.hour}点${now.minute}分"
        }
        lowerText.contains("今天几号") || lowerText.contains("日期") -> {
            val today = LocalDate.now()
            "今天是${today.year}年${today.monthValue}月${today.dayOfMonth}日，${today.dayOfWeek.chinese()}"
        }
        lowerText.contains("打开灯") || lowerText.contains("关闭灯") -> {
            // SmartHomeSkill 处理，Hub 在线即可
            val skill = findMatchedSkill(text)
            if (skill != null) {
                val result = skill.execute(buildSkillContext(text))
                return result.response
            }
            "无法执行家居控制"
        }
        // 无法处理的离线命令
        else -> {
            "抱歉，完全离线状态下，我只能回答时间和执行家居控制。联网后我可以回答更多问题~"
        }
    }
}
```

### 网络状态监听

```kotlin
/**
 * 网络状态监听器
 * 用于实时感知网络状态变化
 */
interface NetworkMonitor {
    val isConnected: StateFlow<Boolean>
    val connectionType: StateFlow<ConnectionType> // WIFI, CELLULAR, NONE

    fun checkConnectivity(): Boolean
}

enum class ConnectionType {
    WIFI, CELLULAR, ETHERNET, NONE
}
```

### 离线缓存策略

```kotlin
/**
 * 天气缓存 - 减少离线时的体验降级
 */
class WeatherCache {
    private val cache = MutableStateFlow<WeatherCacheEntry?>(null)

    data class WeatherCacheEntry(
        val weather: WeatherInfo,
        val timestamp: Long,
        val expiresAt: Long = timestamp + 30 * 60 * 1000 // 30分钟有效
    )

    fun getCached(location: String): WeatherInfo? {
        val entry = cache.value
        return if (entry != null && entry.expiresAt > System.currentTimeMillis()) {
            entry.weather
        } else null
    }

    fun cache(weather: WeatherInfo, location: String) {
        cache.value = WeatherCacheEntry(weather, System.currentTimeMillis())
    }
}
```

---

## 3.9 测试策略规范

### 测试金字塔

```
        ┌─────────────┐
        │    E2E     │  ← 关键用户路径测试 (5%)
        │   Tests    │
        ├─────────────┤
        │ Integration │  ← Skill 集成、IntentRouter 路由 (20%)
        │   Tests    │
        ├─────────────┤
        │ Unit Tests │  ← 每个 Skill、ConversationManager (75%)
        │            │
        └─────────────┘
```

### 单元测试规范

#### Skill 单元测试模板

```kotlin
class SmartHomeSkillTest {
    private lateinit var skill: SmartHomeSkill
    private lateinit var mockDeviceRepository: SmartDeviceRepository

    @Before
    fun setup() {
        mockDeviceRepository = mockk(relaxed = true)
        skill = SmartHomeSkill(mockDeviceRepository)
    }

    @Test
    fun `execute - 成功打开灯`() = runTest {
        // Given
        every { mockDeviceRepository.control(DeviceType.LIGHT, DeviceAction.ON, null) } returns true
        val context = SkillContext(
            text = "打开灯",
            slots = emptyMap(),
            conversationHistory = emptyList(),
            userProfile = null
        )

        // When
        val result = skill.execute(context)

        // Then
        assert(result.response.contains("已打开灯"))
        assert(result.action is SkillAction.ControlDevice)
    }

    @Test
    fun `execute - 设备无响应`() = runTest {
        // Given
        every { mockDeviceRepository.control(DeviceType.LIGHT, DeviceAction.ON, null) } returns false
        val context = SkillContext(
            text = "打开灯",
            slots = emptyMap(),
            conversationHistory = emptyList(),
            userProfile = null
        )

        // When
        val result = skill.execute(context)

        // Then
        assert(result.response.contains("没有响应"))
        assert(result.error is SkillError.Permanent)
    }

    @Test
    fun `execute - 网络超时`() = runTest {
        // Given
        every { mockDeviceRepository.control(DeviceType.LIGHT, DeviceAction.ON, null) } throws SocketTimeoutException()
        val context = SkillContext(
            text = "打开灯",
            slots = emptyMap(),
            conversationHistory = emptyList(),
            userProfile = null
        )

        // When
        val result = skill.execute(context)

        // Then
        assert(result.error is SkillError.Transient)
        assert(result.response.contains("超时"))
    }
}
```

#### AlarmSkill 单元测试

```kotlin
class AlarmSkillTest {
    @Test
    fun `parseTime - 下午时间正确转换`() {
        val skill = AlarmSkill(mockk())
        val result = skill.parseTime("下午3点30分")
        assert(result == LocalTime.of(15, 30))
    }

    @Test
    fun `parseTime - 早上12点转换`() {
        val skill = AlarmSkill(mockk())
        val result = skill.parseTime("早上12点")
        assert(result == LocalTime.of(0, 0))
    }

    @Test
    fun `execute - 无时间参数需要追问`() = runTest {
        val skill = AlarmSkill(mockk(relaxed = true))
        val context = SkillContext(
            text = "设置闹钟",
            slots = emptyMap(),
            conversationHistory = emptyList(),
            userProfile = null
        )

        val result = skill.execute(context)

        assert(result.requiresFollowUp)
        assert(result.response.contains("几点的"))
    }
}
```

#### ConversationManager 单元测试

```kotlin
class ConversationManagerTest {
    private lateinit var manager: ConversationManager

    @Before
    fun setup() {
        manager = ConversationManager()
    }

    @Test
    fun `addUserMessage - 添加到历史`() {
        manager.addUserMessage("今天天气")
        assert(manager.getHistory().size == 1)
        assert(manager.getHistory()[0].role == Role.USER)
    }

    @Test
    fun `addAssistantMessage - 个性化处理`() {
        manager.addAssistantMessage("好的")
        val history = manager.getHistory()
        assert(history[0].content.contains("还有什么需要帮忙"))
    }

    @Test
    fun `buildContextualPrompt - 包含历史`() {
        manager.addUserMessage("今天天气怎么样")
        manager.addAssistantMessage("今天晴天")
        val prompt = manager.buildContextualPrompt("明天呢")

        assert(prompt.contains("小爱同学"))
        assert(prompt.contains("今天天气怎么样"))
        assert(prompt.contains("今天晴天"))
    }

    @Test
    fun `getGreeting - 根据时间返回不同问候`() {
        val morningManager = mockTime(LocalTime.of(8, 0))
        assert(morningManager.getGreeting().contains("早上好"))

        val nightManager = mockTime(LocalTime.of(22, 0))
        assert(nightManager.getGreeting().contains("夜深了"))
    }
}
```

### 集成测试规范

#### IntentRouter 集成测试

```kotlin
class IntentRouterIntegrationTest {
    @Test
    fun `handle - 匹配 SmartHomeSkill`() = runTest {
        val skill = SmartHomeSkill(mockDeviceRepository)
        val router = IntentRouter(listOf(skill), null, conversationManager)

        val response = router.handle("打开客厅灯")

        assert(response.contains("已打开") || response.contains("灯"))
    }

    @Test
    fun `handle - 无匹配使用LLM兜底`() = runTest {
        val mockLLM = mockk<LLMRepository>()
        every { mockLLM.chat(any()) } returns Result.success("这是LLM的回答")
        val router = IntentRouter(emptyList(), mockLLM, conversationManager)

        val response = router.handle("今天午餐吃什么")

        assert(response.contains("LLM"))
    }

    @Test
    fun `handle - 多轮对话上下文`() = runTest {
        val router = IntentRouter(listOf(skill), llm, conversationManager)

        router.handle("设置闹钟")
        router.handle("下午3点")

        val history = conversationManager.getHistory()
        assert(history.size >= 4) // 问 + 答 + 问 + 答
    }
}
```

### E2E 测试规范

```kotlin
class VoiceAssistantE2ETest {
    @Test
    fun `完整流程 - 语音设置闹钟`() = runTest {
        // 1. 唤醒
        voicePipeline.triggerWakeWord()

        // 2. 语音输入
        voicePipeline.inputSpeech("设置一个下午3点的闹钟")

        // 3. 等待处理
        delay(2000)

        // 4. 验证闹钟已设置
        val alarms = alarmScheduler.getActiveAlarms()
        assert(alarms.any { it.time == LocalTime.of(15, 0) })

        // 5. 验证TTS回复
        verify(tts).speak(capture(responseCaptor))
        assert(responseCaptor.value.contains("已设置"))
    }
}
```

### Mock 基础设施

```kotlin
/**
 * 测试用 Mock 依赖
 */
object TestDependencies {
    fun mockSmartDeviceRepository() = mockk<SmartDeviceRepository> {
        every { control(any(), any(), any()) } returns true
        every { getDeviceState(any()) } returns DeviceState(DeviceType.LIGHT, true, 100)
        every { discoverDevices() } returns listOf(
            DiscoveredDevice("1", "客厅灯", DeviceType.LIGHT, "客厅"),
            DiscoveredDevice("2", "空调", DeviceType.AC, "客厅")
        )
    }

    fun mockAlarmScheduler() = mockk<AlarmScheduler> {
        every { setAlarm(any(), any()) } returns "alarm-123"
        every { getActiveAlarms() } returns emptyList()
        every { cancelAlarm(any()) } returns true
    }

    fun mockWeatherApi() = mockk<WeatherApi> {
        every { getWeather(any(), any()) } returns WeatherInfo(
            city = "北京",
            condition = "晴",
            tempLow = 15,
            tempHigh = 25,
            humidity = 50,
            windSpeed = 3
        )
    }
}
```

---

## 3.10 数据持久化规范

### 持久化需求矩阵

| 数据 | 存储方式 | 生命周期 | 说明 |
|------|----------|----------|------|
| **对话历史** | Room/SharedPreferences | 应用内 | 最近 N 条，可配置 |
| **用户画像** | Room | 永久 | 用户名、偏好设置 |
| **闹钟** | AlarmManager | 系统级 | 由 Android 管理系统 |
| **设备状态缓存** | Room/Memory | 应用内 | SmartHome 设备状态 |
| **天气缓存** | Memory | 30分钟 | 减少重复 API 调用 |
| **Skill 配置** | DataStore | 永久 | 技能开关、API Keys |

### 对话历史持久化

```kotlin
@Entity(tableName = "conversation_history")
data class ConversationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String,  // "USER" or "ASSISTANT"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String  // 用于区分不同对话会话
)

class ConversationRepository {
    private val dao: ConversationHistoryDao

    suspend fun saveTurn(role: Role, content: String, sessionId: String) {
        dao.insert(ConversationHistoryEntity(
            role = role.name,
            content = content,
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId
        ))
    }

    suspend fun getHistory(sessionId: String, limit: Int = 20): List<ConversationTurn> {
        return dao.getRecent(sessionId, limit).map { entity ->
            ConversationTurn(
                role = Role.valueOf(entity.role),
                content = entity.content,
                timestamp = entity.timestamp
            )
        }
    }

    suspend fun clearHistory(sessionId: String) {
        dao.deleteSession(sessionId)
    }
}
```

### 用户画像持久化

```kotlin
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Int = 1,  // 单用户，应用只有一份
    val name: String? = null,
    val morningAlarmTime: String? = null,  // "HH:mm" format
    val favoriteMusicGenres: String? = null,  // JSON array
    val smartHomeDevicesJson: String? = null,  // JSON map
    val updatedAt: Long = System.currentTimeMillis()
)

class UserProfileRepository {
    private val dao: UserProfileDao

    suspend fun getUserProfile(): UserProfile? {
        val entity = dao.get() ?: return null
        return UserProfile(
            name = entity.name,
            morningAlarm = entity.morningAlarmTime?.let { LocalTime.parse(it) },
            favoriteMusic = entity.favoriteMusicGenres?.split(",") ?: emptyList(),
            smartHomeDevices = entity.smartHomeDevicesJson?.let { parseDevices(it) } ?: emptyMap()
        )
    }

    suspend fun updateUserProfile(profile: UserProfile) {
        dao.insert(profile.toEntity())
    }
}
```

### 设备状态缓存

```kotlin
@Entity(tableName = "device_state_cache")
data class DeviceStateCacheEntity(
    @PrimaryKey
    val deviceId: String,
    val deviceType: String,
    val isOn: Boolean,
    val value: Int?,  // 亮度、温度等
    val lastUpdated: Long
)

class DeviceStateCache {
    private val dao: DeviceStateCacheDao
    private val cacheValidityMs = 60_000  // 1分钟内有效

    suspend fun getCachedState(deviceId: String): DeviceState? {
        val cached = dao.get(deviceId) ?: return null
        if (System.currentTimeMillis() - cached.lastUpdated > cacheValidityMs) {
            return null  // 缓存过期
        }
        return DeviceState(
            type = DeviceType.valueOf(cached.deviceType),
            isOn = cached.isOn,
            value = cached.value
        )
    }

    suspend fun cacheState(deviceId: String, state: DeviceState) {
        dao.insert(DeviceStateCacheEntity(
            deviceId = deviceId,
            deviceType = state.type.name,
            isOn = state.isOn,
            value = state.value,
            lastUpdated = System.currentTimeMillis()
        ))
    }
}
```

### DataStore 用于 Skill 配置

```kotlin
class SkillConfigRepository(private val dataStore: DataStore<Preferences>) {
    private val weatherApiKeyKey = stringPreferencesKey("weather_api_key")
    private val hfengApiKeyKey = stringPreferencesKey("hefeng_api_key")
    private val skillEnabledKey = stringSetPreferencesKey("skill_enabled")

    suspend fun getWeatherApiKey(): String? {
        return dataStore.data.first()[weatherApiKeyKey]
    }

    suspend fun setWeatherApiKey(key: String) {
        dataStore.edit { it[weatherApiKeyKey] = key }
    }

    suspend fun isSkillEnabled(skillName: String): Boolean {
        val enabled = dataStore.data.first()[skillEnabledKey] ?: setOf("all")
        return enabled.contains("all") || enabled.contains(skillName)
    }

    suspend fun setSkillEnabled(skillName: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[skillEnabledKey]?.toMutableSet() ?: mutableSetOf("all")
            if (enabled) {
                current.add(skillName)
            } else {
                current.remove(skillName)
            }
            prefs[skillEnabledKey] = current
        }
    }
}
```

---

## 3.11 安全考虑

### 输入验证

```kotlin
/**
 * 语音输入验证器
 */
object VoiceInputValidator {
    private const val MAX_TEXT_LENGTH = 500
    private val DangerousPatterns = listOf(
        Regex(".*(rm -rf|sudo|chmod|eval|exec).*", RegexOption.IGNORE_CASE),
        Regex(".*(<script|javascript:|onerror=).*", RegexOption.IGNORE_CASE)
    )

    fun validate(text: String): ValidationResult {
        return when {
            text.isBlank() -> ValidationResult.Invalid("输入不能为空")
            text.length > MAX_TEXT_LENGTH -> ValidationResult.Invalid("输入过长")
            DangerousPatterns.any { it.matches(text) } -> ValidationResult.Invalid("无效输入")
            else -> ValidationResult.Valid(text.trim())
        }
    }
}

sealed class ValidationResult {
    data class Valid(val text: String) : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}
```

### API Key 保护

```kotlin
/**
 * API Key 安全存储
 * 使用 Android EncryptedSharedPreferences
 */
class SecureKeyStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(keyName: String, apiKey: String) {
        securePrefs.edit().putString(keyName, apiKey).apply()
    }

    fun getApiKey(keyName: String): String? {
        return securePrefs.getString(keyName, null)
    }

    fun deleteApiKey(keyName: String) {
        securePrefs.edit().remove(keyName).apply()
    }
}
```

### 技能调用频率限制

```kotlin
/**
 * 技能调用频率限制器
 */
class SkillRateLimiter {
    private val callTimestamps = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun canCall(skillName: String, maxCallsPerMinute: Int = 10): Boolean {
        val now = System.currentTimeMillis()
        val windowMs = 60_000L

        val timestamps = callTimestamps.computeIfAbsent(skillName) { ArrayDeque() }

        synchronized(timestamps) {
            // 移除窗口外的记录
            while (timestamps.isNotEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.removeFirst()
            }

            return if (timestamps.size < maxCallsPerMinute) {
                timestamps.addLast(now)
                true
            } else {
                false
            }
        }
    }
}
```

---

## 3.12 可观测性规范

### 日志规范

```kotlin
/**
 * 技能执行日志
 */
object SkillLogger {
    private const val TAG = "SkillExecution"

    fun logExecution(skillName: String, text: String, durationMs: Long, success: Boolean) {
        if (success) {
            Timber.d("$TAG: $skillName executed in ${durationMs}ms - input: $text")
        } else {
            Timber.w("$TAG: $skillName failed in ${durationMs}ms - input: $text")
        }
    }

    fun logError(skillName: String, text: String, error: Throwable) {
        Timber.e(error, "$TAG: $skillName error - input: $text, error: ${error.message}")
    }
}

/**
 * IntentRouter 日志
 */
object RouterLogger {
    private const val TAG = "IntentRouter"

    fun logRouting(text: String, matchedSkill: String?, confidence: Float?) {
        Timber.d("$TAG: routing '$text' -> skill=$matchedSkill, confidence=$confidence")
    }

    fun logLLMFallback(text: String) {
        Timber.d("$TAG: no skill matched, falling back to LLM for '$text'")
    }
}
```

### Metrics 规范

```kotlin
/**
 * 技能执行指标
 */
object SkillMetrics {
    private val skillExecutionTime = Histogram.builder("skill_execution_time_ms")
        .description("Skill execution time in milliseconds")
        .register()

    private val skillSuccessCount = Counter.builder("skill_execution_success_total")
        .description("Total number of successful skill executions")
        .register()

    private val skillFailureCount = Counter.builder("skill_execution_failure_total")
        .description("Total number of failed skill executions")
        .register()

    fun recordExecution(skillName: String, durationMs: Long, success: Boolean) {
        skillExecutionTime.record(durationMs, Tags.of("skill", skillName))
        if (success) {
            skillSuccessCount.increment(Tags.of("skill", skillName))
        } else {
            skillFailureCount.increment(Tags.of("skill", skillName))
        }
    }
}

/**
 * IntentRouter 指标
 */
object RouterMetrics {
    private val routingCount = Counter.builder("intent_router_routing_total")
        .description("Total number of routing decisions")
        .register()

    private val skillMatchCount = Counter.builder("intent_router_skill_match_total")
        .description("Total number of skill matches")
        .register()

    private val llmFallbackCount = Counter.builder("intent_router_llm_fallback_total")
        .description("Total number of LLM fallback calls")
        .register()

    fun recordRouting(matchedSkill: Boolean) {
        routingCount.increment()
        if (matchedSkill) {
            skillMatchCount.increment()
        } else {
            llmFallbackCount.increment()
        }
    }
}
```

### 追踪规范

```kotlin
/**
 * 技能执行追踪
 */
object SkillTracer {
    fun traceSkillExecution(
        spanName: String,
        skillName: String,
        text: String,
        block: () -> SkillResult
    ): SkillResult {
        return trace(spanName) { span ->
            span.setAttribute("skill.name", skillName)
            span.setAttribute("skill.input", text)
            try {
                val result = block()
                span.setAttribute("skill.success", result.error == null)
                result
            } catch (e: Exception) {
                span.setAttribute("skill.error", true)
                span.setAttribute("skill.error.message", e.message ?: "Unknown")
                throw e
            }
        }
    }
}
```

---

## 3.13 多轮对话流程规范

### 多轮对话状态机

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        多轮对话状态机                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────┐    用户输入    ┌──────────────┐    槽位完整   ┌──────────┐  │
│  │  IDLE   │ ───────────▶ │ AWAITING_SLOT │ ───────────▶ │ EXECUTING │  │
│  └─────────┘              └──────────────┘              └──────────┘  │
│       ▲                           │                            │          │
│       │                           │ 槽位不完整/追问              │ 完成     │
│       │                           ▼                            ▼          │
│       │                    ┌──────────────┐              ┌──────────┐   │
│       │                    │  AWAITING    │              │  RESPONDING│   │
│       │                    │  CONFIRM     │              └──────────┘   │
│       │                    └──────────────┘                    │          │
│       │                           │                             ▼          │
│       │                           │                      ┌──────────┐   │
│       │                           └──────────────────────▶│   IDLE   │   │
│       │                                                └──────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘

状态说明：
- IDLE: 等待用户唤醒
- AWAITING_SLOT: 等待用户补充必要参数（如时间、地点）
- AWAITING_CONFIRM: 等待用户确认（如"确定设置吗？"）
- EXECUTING: 技能执行中
- RESPONDING: 回复用户中
```

### 槽位填充机制

```kotlin
/**
 * 槽位状态
 */
data class SlotState(
    val skillName: String,
    val requiredSlots: Map<String, SlotDefinition>,
    val filledSlots: MutableMap<String, String> = mutableMapOf(),
    val state: SlotFillingState = SlotFillingState.IN_PROGRESS,
    val turnCount: Int = 0  // 多轮次数，防止无限追问
)

enum class SlotFillingState {
    IN_PROGRESS,  // 槽位填充中
    COMPLETE,     // 槽位已满
    EXPIRED       // 槽位过期（超过最大轮次）
}

/**
 * 槽位定义
 */
data class SlotDefinition(
    val name: String,
    val type: SlotType,
    val required: Boolean = true,
    val prompt: String,  // 追问时的提示语
    val maxTurns: Int = 3  // 最大追问轮次
)

enum class SlotType {
    TIME,        // 时间
    LOCATION,    // 地点
    DEVICE,      // 设备
    NUMBER,      // 数字
    TEXT         // 通用文本
}
```

### 多轮对话示例

#### 示例1：设置闹钟

```
用户：设置闹钟
    ↓ [槽位：TIME 未填充]
小爱：请问你想设置几点的闹钟？
    ↓ [AWAITING_SLOT]
用户：下午3点
    ↓ [槽位：TIME=下午3点 已填充]
小爱：好的，已设置下午3点的闹钟，还有什么需要帮忙的吗？
    ↓ [IDLE]
```

#### 示例2：智能家居控制

```
用户：打开灯
    ↓ [槽位：DEVICE=LIGHT 已填充，ACTION=ON 已推断]
小爱：好的，已打开灯。
    ↓ [IDLE]
```

#### 示例3：带确认的设备控制

```
用户：把客厅灯调到最亮
    ↓ [槽位完整]
小爱：好的，把客厅灯调到最亮，确认吗？
    ↓ [AWAITING_CONFIRM]
用户：确认
    ↓ [EXECUTING]
小爱：已确认，客厅灯已调到最亮。
```

### 槽位管理器

```kotlin
class SlotFillingManager {
    private val activeSlots = ConcurrentHashMap<String, SlotState>()

    /**
     * 开始槽位填充
     */
    fun startFilling(skillName: String, requiredSlots: Map<String, SlotDefinition>): SlotState {
        val state = SlotState(skillName, requiredSlots)
        activeSlots[skillName] = state
        return state
    }

    /**
     * 填充槽位
     */
    fun fillSlot(sessionId: String, slotName: String, value: String): SlotState? {
        val state = activeSlots[sessionId] ?: return null

        state.filledSlots[slotName] = value
        state.turnCount++

        // 检查是否所有必填槽位都已填充
        val allFilled = state.requiredSlots.all { (name, def) ->
            !def.required || state.filledSlots.containsKey(name)
        }

        if (allFilled) {
            state.state = SlotFillingState.COMPLETE
        } else if (state.turnCount >= 3) {
            state.state = SlotFillingState.EXPIRED
        }

        return state
    }

    /**
     * 获取追问提示
     */
    fun getPrompt(state: SlotState): String {
        val missingSlot = state.requiredSlots.entries.find { (name, def) ->
            def.required && !state.filledSlots.containsKey(name)
        }
        return missingSlot?.let { it.value.prompt }
            ?: "无法理解，请再说一遍"
    }

    /**
     * 清除槽位状态
     */
    fun clear(sessionId: String) {
        activeSlots.remove(sessionId)
    }
}
```

### IntentRouter 多轮支持

```kotlin
suspend fun handle(text: String, sessionId: String): String {
    // 1. 检查是否有进行中的槽位填充
    val activeSlot = slotFillingManager.getActiveSlot(sessionId)

    if (activeSlot != null && activeSlot.state == SlotFillingState.IN_PROGRESS) {
        // 多轮对话：继续槽位填充
        val skill = skills.find { it.name == activeSlot.skillName }
        if (skill != null) {
            val updatedState = slotFillingManager.fillSlot(sessionId, extractSlotName(text), text)
            if (updatedState?.state == SlotFillingState.COMPLETE) {
                // 槽位填充完成，执行技能
                val context = buildSkillContext(updatedSlotToMap(updatedState))
                val result = skill.execute(context)
                slotFillingManager.clear(sessionId)
                return result.response
            } else if (updatedState?.state == SlotFillingState.EXPIRED) {
                slotFillingManager.clear(sessionId)
                return "太久了，我们换个话题吧~"
            } else {
                return slotFillingManager.getPrompt(updatedState!!)
            }
        }
    }

    // 2. 正常单轮处理
    return handleSingleTurn(text)
}
```

---

## 3.14 边缘情况处理规范

### 各技能边缘情况

#### SmartHomeSkill 边缘情况

```kotlin
// 边缘情况矩阵
val edgeCases = listOf(
    EdgeCase(
        input = "打开灯",
        scenario = "用户家有多个灯，无房间信息",
        expected = "追问"请问打开哪个房间的灯？""
    ),
    EdgeCase(
        input = "把灯调到50%",
        scenario = "灯不支持亮度调节",
        expected = "提示"该灯不支持亮度调节""
    ),
    EdgeCase(
        input = "打开电视",
        scenario = "电视已打开",
        expected = "提示"电视已经是打开状态了""
    ),
    EdgeCase(
        input = "关闭不存在的设备",
        scenario = "设备ID无效",
        expected = "提示"没有找到这个设备""
    ),
    EdgeCase(
        input = "把空调调到100度",
        scenario = "参数超出范围",
        expected = "提示"空调温度支持16-30度，请说一个范围内的温度""
    )
)
```

#### AlarmSkill 边缘情况

```kotlin
val alarmEdgeCases = listOf(
    EdgeCase(
        input = "设置明天下午3点的闹钟",
        scenario = "现在是下午4点",
        expected = "设置明天下午3点（自动推断到后天）"
    ),
    EdgeCase(
        input = "设置凌晨2点的闹钟",
        scenario = "用户说"凌晨"但没说哪天的",
        expected = "追问"请问是今天还是明天的凌晨2点？""
    ),
    EdgeCase(
        input = "设置闹钟到25:00",
        scenario = "无效时间",
        expected = "提示"时间格式不正确，请说几点几分""
    ),
    EdgeCase(
        input = "设置100个闹钟",
        scenario = "闹钟数量超限",
        expected = "提示"闹钟数量已达上限，请先取消一些闹钟""
    )
)
```

#### WeatherSkill 边缘情况

```kotlin
val weatherEdgeCases = listOf(
    EdgeCase(
        input = "北京市的天气",
        scenario = "城市名包含"市"",
        expected = "自动去除"市"后查询"
    ),
    EdgeCase(
        input = "火星的天气",
        scenario = "不支持的地区",
        expected = "提示"暂时不支持查询该地区的天气""
    ),
    EdgeCase(
        input = "上周的天气",
        scenario = "不支持的历史查询",
        expected = "提示"天气查询只支持今天和未来7天""
    ),
    EdgeCase(
        input = "下周3的天气",
        scenario = "口语化日期",
        expected = "转换为具体日期后查询"
    )
)
```

### 输入长度限制

```kotlin
object InputLimits {
    const val MAX_TEXT_LENGTH = 500        // 最大文字输入
    const val MAX_HISTORY_TURNS = 20       // 对话历史最大轮次
    const val MAX_ALARM_COUNT = 10        // 最大闹钟数量
    const val MAX_DEVICE_NAME_LENGTH = 50  // 设备名称最大长度
    const val MAX_LOCATION_LENGTH = 100    // 地点字符串最大长度
    const val MAX_PROMPT_TURNS = 3        // 槽位追问最大次数
}
```

### 异常输入处理

```kotlin
/**
 * 异常输入处理器
 */
object InputSanitizer {

    fun sanitize(text: String): SanitizedResult {
        return when {
            text.isBlank() -> SanitizedResult.Invalid("输入不能为空")
            text.length > InputLimits.MAX_TEXT_LENGTH ->
                SanitizedResult.Invalid("输入过长，最大${InputLimits.MAX_TEXT_LENGTH}字")
            containsEmoji(text) && !isValidEmoji(text) ->
                SanitizedResult.Invalid("暂不支持该表情")
            containsSpecialChars(text) ->
                SanitizedResult.Sanitized(text.removeSpecialChars())
            else ->
                SanitizedResult.Valid(text.trim())
        }
    }

    private fun String.removeSpecialChars(): String {
        return this.filter { it.isLetterOrDigit() || it.isWhitespace() || isChinese(it) }
    }

    private fun isChinese(c: Char): Boolean {
        return c.code in 0x4E00..0x9FFF
    }
}

sealed class SanitizedResult {
    data class Valid(val text: String) : SanitizedResult()
    data class Sanitized(val text: String) : SanitizedResult()
    data class Invalid(val reason: String) : SanitizedResult()
}
```

---

## 3.15 技能版本管理规范

### 技能注册表

```kotlin
/**
 * 技能注册表
 * 用于管理所有可用技能及其版本
 */
class SkillRegistry {
    private val skills = ConcurrentHashMap<String, SkillEntry>()

    data class SkillEntry(
        val skill: Skill,
        val version: String,
        val enabled: Boolean = true,
        val loadedAt: Long = System.currentTimeMillis()
    )

    /**
     * 注册技能
     */
    fun register(skill: Skill, version: String = "1.0.0") {
        skills[skill.name] = SkillEntry(skill, version)
        Timber.d("Skill registered: ${skill.name} v$version")
    }

    /**
     * 获取启用的技能列表
     */
    fun getEnabledSkills(): List<Skill> {
        return skills.values
            .filter { it.enabled }
            .map { it.skill }
    }

    /**
     * 启用/禁用技能
     */
    fun setEnabled(skillName: String, enabled: Boolean) {
        skills[skillName]?.let { entry ->
            skills[skillName] = entry.copy(enabled = enabled)
            Timber.d("Skill $skillName enabled=$enabled")
        }
    }

    /**
     * 获取技能版本
     */
    fun getVersion(skillName: String): String? {
        return skills[skillName]?.version
    }
}
```

### 技能生命周期

```kotlin
/**
 * 技能生命周期钩子
 */
interface SkillLifecycle {
    /**
     * 技能加载时调用
     */
    fun onLoad()

    /**
     * 技能卸载时调用
     */
    fun onUnload()

    /**
     * 技能更新时调用
     */
    fun onUpdate(oldVersion: String, newVersion: String)
}

/**
 * 技能版本兼容性检查
 */
object SkillCompatibility {
    fun isCompatible(currentVersion: String, requiredVersion: String): Boolean {
        val current = parseVersion(currentVersion)
        val required = parseVersion(requiredVersion)

        // 主版本号相同则兼容
        return current[0] == required[0]
    }

    private fun parseVersion(version: String): List<Int> {
        return version.split(".").mapNotNull { it.toIntOrNull() }
    }
}
```

---

## 3.16 性能基准规范

### 性能指标目标

| 指标 | 目标值 | 测量方法 |
|------|--------|----------|
| **语音唤醒延迟** | < 100ms | KWS检测到 → TTS开始 |
| **语音识别延迟** | < 500ms | 语音结束 → 文字输出 |
| **意图路由延迟** | < 50ms | 文字 → 匹配技能 |
| **技能执行延迟** | < 200ms | 不含外部API调用 |
| **TTS合成延迟** | < 1000ms | 文字 → 音频开始 |
| **端到端响应延迟** | < 2000ms | 语音结束 → TTS开始播放 |

### 技能性能预算

```
┌─────────────────────────────────────────────────────────────────────┐
│                      端到端响应时间预算 (2000ms)                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  VAD结束 → ASR → 意图路由 → 技能执行 → TTS → 开始播放              │
│  100ms    500ms     50ms        200ms    1000ms   150ms           │
│                                                                     │
│  合计: 2000ms                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 性能监控

```kotlin
/**
 * 技能性能监控器
 */
class SkillPerformanceMonitor {
    private val executionTimes = ConcurrentHashMap<String, List<Long>>()

    fun recordExecution(skillName: String, durationMs: Long) {
        val times = executionTimes.computeIfAbsent(skillName) { mutableListOf() }
        synchronized(times) {
            (times as MutableList).add(durationMs)
            // 只保留最近100次
            if (times.size > 100) {
                times.removeAt(0)
            }
        }
    }

    fun getStats(skillName: String): SkillPerformanceStats {
        val times = executionTimes[skillName] ?: return SkillPerformanceStats(0, 0, 0)
        synchronized(times) {
            val sorted = times.sorted()
            return SkillPerformanceStats(
                count = times.size,
                avgMs = times.average().toLong(),
                p99Ms = sorted.getOrElse((sorted.size * 0.99).toInt()) { sorted.last() }
            )
        }
    }

    data class SkillPerformanceStats(
        val count: Int,
        val avgMs: Long,
        val p99Ms: Long
    )
}

object PerformanceBudget {
    // 各阶段预算（毫秒）
    const val VAD_BUDGET = 100
    const val ASR_BUDGET = 500
    const val ROUTING_BUDGET = 50
    const val SKILL_BUDGET = 200
    const val TTS_BUDGET = 1000
    const val TOTAL_BUDGET = 2000
}
```

### 内存占用预算

| 组件 | 内存占用 | 说明 |
|------|----------|------|
| **Sherpa-ONNX 模型** | ~100MB | KWS + VAD + ASR |
| **TTS 模型** | ~60MB | 花燕模型 |
| **对话历史** | ~1MB | 20轮对话 |
| **设备状态缓存** | ~100KB | 最多100个设备 |
| **应用总内存** | < 300MB | 峰值 |

---

## 四、技能注册与注入

### 4.1 SkillModule (Hilt DI)

**文件**: `core/src/main/java/com/voiceassistant/core/di/SkillModule.kt`

```kotlin
package com.voiceassistant.core.di

import com.voiceassistant.core.skill.*
import com.voiceassistant.core.conversation.ConversationManager
import com.voiceassistant.domain.repository.LLMRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SkillModule {

    @Provides
    @Singleton
    fun provideSkills(
        smartHomeSkill: SmartHomeSkill,
        alarmSkill: AlarmSkill,
        weatherSkill: WeatherSkill,
        musicSkill: MusicSkill,
        llmSkill: LLMSkill
    ): List<Skill> {
        return listOf(
            smartHomeSkill,
            alarmSkill,
            weatherSkill,
            musicSkill,
            llmSkill
        )
    }

    @Provides
    fun provideSmartHomeSkill(
        deviceRepository: SmartDeviceRepository
    ): SmartHomeSkill = SmartHomeSkill(deviceRepository)

    @Provides
    fun provideAlarmSkill(
        alarmScheduler: AlarmScheduler
    ): AlarmSkill = AlarmSkill(alarmScheduler)

    @Provides
    fun provideWeatherSkill(
        weatherApi: WeatherApi
    ): WeatherSkill = WeatherSkill(weatherApi)

    @Provides
    fun provideMusicSkill(
        musicRepository: MusicRepository,
        playerRepository: PlayerRepository?
    ): MusicSkill = MusicSkill(musicRepository, playerRepository)

    @Provides
    fun provideLLMSkill(
        llmRepository: LLMRepository?,
        conversationManager: ConversationManager
    ): LLMSkill = LLMSkill(llmRepository, conversationManager)
}

/**
 * LLM 兜底技能
 */
class LLMSkill(
    private val llmRepository: LLMRepository?,
    private val conversationManager: ConversationManager
) : Skill {
    override val name = "智能对话"
    override val keywords = listOf("什么", "为什么", "怎么", "如何", "是不是", "能不能", "会不会", "好不好", "聊聊", "讲讲", "知道")
    override val description = "回答各种问题"

    override suspend fun execute(context: SkillContext): SkillResult {
        val response = if (llmRepository != null) {
            val prompt = conversationManager.buildContextualPrompt(context.text)
            llmRepository.chat(prompt).getOrNull() ?: "抱歉，我暂时回答不了这个问题"
        } else {
            "需要联网才能回答这个问题哦~"
        }
        return SkillResult(response)
    }
}
```

### 4.2 缺失接口占位

**文件**: `core/src/main/java/com/voiceassistant/core/skill/MissingInterfaces.kt`

```kotlin
package com.voiceassistant.core.skill

/**
 * 智能设备仓库接口
 * 需要根据实际智能家居平台实现
 */
interface SmartDeviceRepository {
    suspend fun control(deviceType: DeviceType, action: DeviceAction, value: Int?): Boolean
    suspend fun getDeviceState(deviceId: String): DeviceState?
    suspend fun discoverDevices(): List<DiscoveredDevice>
}

/**
 * 闹钟调度器接口
 * 使用 Android AlarmManager 实现
 */
interface AlarmScheduler {
    suspend fun setAlarm(time: java.time.LocalTime, label: String?): String
    suspend fun cancelAlarm(alarmId: String): Boolean
    suspend fun getActiveAlarms(): List<AlarmInfo>
}

/**
 * 天气 API 接口
 * 需要接入天气服务
 */
interface WeatherApi {
    suspend fun getWeather(location: String, timeRange: TimeRange = TimeRange.TODAY): WeatherInfo
}

/**
 * 音乐技能
 * 将现有的 MusicRepository 适配为 Skill 接口
 */
class MusicSkill(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository?
) : Skill {
    override val name = "音乐"
    override val keywords = listOf("播放", "暂停", "继续", "下一首", "上一首", "来一首", "放歌", "听歌", "切歌", "音乐")
    override val description = "播放音乐"

    override suspend fun execute(context: SkillContext): SkillResult {
        val text = context.text

        return when {
            text.contains("播放") || text.contains("来一首") || text.contains("放歌") -> {
                val query = text.replace(Regex("(播放|来一首|放一首|放歌|听|我想听)"), "").trim()
                if (query.isEmpty()) {
                    return SkillResult("你想听什么歌曲？")
                }

                val result = musicRepository.searchSongs(query)
                result.fold(
                    onSuccess = { songs ->
                        if (songs.isEmpty()) {
                            SkillResult("没找到关于「$query」的歌曲呢")
                        } else {
                            val song = songs.first()
                            val streamUrl = musicRepository.getStreamUrl(song.id)
                            playerRepository?.play(streamUrl, song.title, song.artist ?: "未知艺术家")
                            SkillResult(
                                "好的，正在播放 ${song.title} - ${song.artist ?: "未知艺术家"}",
                                action = SkillAction.PlayMusic(query)
                            )
                        }
                    },
                    onFailure = { SkillResult("搜索歌曲失败了") }
                )
            }
            text.contains("暂停") -> {
                playerRepository?.pause()
                SkillResult("已暂停")
            }
            text.contains("继续") -> {
                playerRepository?.resume()
                SkillResult("继续播放")
            }
            text.contains("下一首") || text.contains("换一首") -> {
                SkillResult("正在播放下一首")
            }
            text.contains("上一首") -> {
                SkillResult("正在播放上一首")
            }
            else -> SkillResult("音乐操作")
        }
    }
}
```

---

## 五、UI 界面设计

### 5.1 主界面布局 (小爱风格)

**文件**: `app/src/main/res/layout/activity_main_xiaomi.xml`

```
┌─────────────────────────────────────┐
│  ☰   小爱同学           ⚙️  🔔    │  ← 顶栏
├─────────────────────────────────────┤
│                                     │
│         ┌─────────────┐             │
│         │  ╭───────╮  │             │
│         │  │       │  │             │
│         │  │  ◉    │  │  ← Avatar  │
│         │  │       │  │             │
│         │  ╰───────╯  │             │
│         │    ~~~     │  ← 表情动画  │
│         └─────────────┘             │
│                                     │
│       "我在听..." / "小爱同学"       │  ← 状态文字
│                                     │
│  ┌─────────────────────────────┐   │
│  │  👤 用户：今天天气怎么样      │   │  ← 用户气泡
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  🔊 小爱：今天北京晴朗，      │   │  ← AI 气泡
│  │       气温15到25度~          │   │
│  └─────────────────────────────┘   │
│                                     │
├─────────────────────────────────────┤
│  [🎵音乐] [💡家居] [⏰闹钟] [🌤️天气] │  ← 快捷技能
├─────────────────────────────────────┤
│                                     │
│           ◉ 按住说话                │  ← 语音按钮
│                                     │
└─────────────────────────────────────┘
```

### 5.2 Avatar 动画状态

**文件**: `app/src/main/java/com/voiceassistant/app/ui/main/AvatarView.kt`

**Avatar 尺寸规格**：

| 状态 | 尺寸 | 外环直径 | 波形区域 |
|------|------|----------|----------|
| **主界面** | 120dp | 160dp | 20dp |
| **通知栏展开** | 80dp | 110dp | 14dp |
| **小部件** | 48dp | 64dp | 8dp |

**Avatar 颜色**：

| 元素 | 浅色模式 | 深色模式 |
|------|---------|---------|
| 头像背景 | `#EEF2FF` (Indigo 50) | `#312E81` (Indigo 900) |
| 外环 (默认) | `#6366F1` (Indigo 500) | `#818CF8` (Indigo 400) |
| 外环 (脉冲) | `#6366F1` → 透明 | `#818CF8` → 透明 |
| 表情区域 | 跟随外环 | 跟随外环 |

**AvatarView 代码**：

**文件**: `app/src/main/java/com/voiceassistant/app/ui/main/AvatarView.kt`

```kotlin
package com.voiceassistant.app.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView

/**
 * 小爱同学 Avatar 视图
 * 支持多种动画状态
 */
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    enum class AvatarState {
        IDLE,           // 待机 - 轻微呼吸动画
        LISTENING,      // 聆听 - 圆环脉冲扩散
        PROCESSING,     // 处理 - 旋转加载
        SPEAKING,       // 说话 - 波形跳动
        HAPPY,          // 开心 - 跳跃
        SAD             // 失落 - 下沉
    }

    private var currentAnimator: ValueAnimator? = null
    private var ringView: View? = null  // 外环动画视图

    fun setState(state: AvatarState) {
        currentAnimator?.cancel()

        when (state) {
            AvatarState.IDLE -> startBreathingAnimation()
            AvatarState.LISTENING -> startPulseAnimation()
            AvatarState.PROCESSING -> startSpinAnimation()
            AvatarState.SPEAKING -> startWaveAnimation()
            AvatarState.HAPPY -> startJumpAnimation()
            AvatarState.SAD -> startSinkAnimation()
        }
    }

    private fun startBreathingAnimation() {
        // 轻微的呼吸缩放动画
        currentAnimator = ValueAnimator.ofFloat(1f, 1.03f, 1f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                scaleX = scale
                scaleY = scale
            }
            start()
        }
    }

    private fun startPulseAnimation() {
        // 圆环脉冲动画
        ringView?.apply {
            visibility = View.VISIBLE
            ValueAnimator.ofFloat(1f, 1.8f, 1f).apply {
                duration = 1200
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { animator ->
                    val scale = animator.animatedValue as Float
                    scaleX = scale
                    scaleY = scale
                    alpha = (2f - scale).coerceIn(0f, 1f)
                }
                start()
            }
        }
    }

    private fun startSpinAnimation() {
        // 旋转动画表示思考
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                rotation = animator.animatedValue as Float
            }
            start()
        }
    }

    private fun startWaveAnimation() {
        // 说话时的波形动画
        ValueAnimator.ofFloat(0.8f, 1.2f, 0.8f).apply {
            duration = 300
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                scaleX = scale
                scaleY = scale
            }
            start()
        }
    }

    private fun startJumpAnimation() {
        // 开心跳跃
        ValueAnimator.ofFloat(0f, -30f, 0f).apply {
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                translationY = animator.animatedValue as Float
            }
            start()
        }
    }

    private fun startSinkAnimation() {
        // 失落下沉
        ValueAnimator.ofFloat(0f, 10f).apply {
            duration = 500
            addUpdateListener { animator ->
                translationY = animator.animatedValue as Float
                alpha = 0.7f
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        currentAnimator?.cancel()
    }
}
```

### 5.3 技能快捷卡片

**文件**: `app/src/main/res/layout/view_skill_card.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="80dp"
    android:layout_height="80dp"
    app:cardCornerRadius="16dp"
    app:cardElevation="4dp"
    app:cardBackgroundColor="@color/skill_card_bg">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:gravity="center">

        <ImageView
            android:id="@+id/ivIcon"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:src="@drawable/ic_music"/>

        <TextView
            android:id="@+id/tvName"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="音乐"
            android:textSize="12sp"
            android:textColor="@color/text_primary"/>

    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
```

### 5.10 资源文件规格

#### colors.xml (颜色资源)

**文件**: `app/src/main/res/values/colors.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 主色调 -->
    <color name="primary">#6366F1</color>
    <color name="primary_dark">#4F46E5</color>
    <color name="primary_light">#818CF8</color>
    <color name="on_primary">#FFFFFF</color>

    <!-- 次要色调 -->
    <color name="secondary">#EC4899</color>
    <color name="secondary_dark">#DB2777</color>
    <color name="on_secondary">#FFFFFF</color>

    <!-- 背景色 -->
    <color name="background">#FFFFFF</color>
    <color name="on_background">#1E293B</color>

    <!-- 表面色 -->
    <color name="surface">#F8FAFC</color>
    <color name="surface_variant">#F1F5F9</color>
    <color name="on_surface">#1E293B</color>
    <color name="on_surface_variant">#64748B</color>

    <!-- 边框/分割线 -->
    <color name="outline">#E2E8F0</color>
    <color name="outline_variant">#CBD5E1</color>

    <!-- 错误色 -->
    <color name="error">#EF4444</color>
    <color name="on_error">#FFFFFF</color>

    <!-- Avatar 专用色 -->
    <color name="avatar_background">#EEF2FF</color>
    <color name="avatar_ring">#6366F1</color>
    <color name="avatar_ring_pulse">#6366F1</color>

    <!-- 气泡颜色 -->
    <color name="bubble_user">#6366F1</color>
    <color name="bubble_user_text">#FFFFFF</color>
    <color name="bubble_ai">#F8FAFC</color>
    <color name="bubble_ai_text">#1E293B</color>

    <!-- 技能卡片 -->
    <color name="skill_card_bg">#F1F5F9</color>
    <color name="skill_card_bg_pressed">#E0E7FF</color>
    <color name="skill_card_border">#CBD5E1</color>
</resources>
```

**深色模式**: `res/values-night/colors.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 主色调 -->
    <color name="primary">#818CF8</color>
    <color name="primary_dark">#6366F1</color>
    <color name="primary_light">#A5B4FC</color>
    <color name="on_primary">#1E1B4B</color>

    <!-- 次要色调 -->
    <color name="secondary">#F472B6</color>
    <color name="secondary_dark">#EC4899</color>
    <color name="on_secondary">#4C0519</color>

    <!-- 背景色 -->
    <color name="background">#121212</color>
    <color name="on_background">#F8FAFC</color>

    <!-- 表面色 -->
    <color name="surface">#1E293B</color>
    <color name="surface_variant">#334155</color>
    <color name="on_surface">#F8FAFC</color>
    <color name="on_surface_variant">#94A3B8</color>

    <!-- 边框/分割线 -->
    <color name="outline">#475569</color>
    <color name="outline_variant">#64748B</color>

    <!-- 错误色 -->
    <color name="error">#F87171</color>
    <color name="on_error">#7F1D1D</color>

    <!-- Avatar 专用色 -->
    <color name="avatar_background">#312E81</color>
    <color name="avatar_ring">#818CF8</color>
    <color name="avatar_ring_pulse">#818CF8</color>

    <!-- 气泡颜色 -->
    <color name="bubble_user">#818CF8</color>
    <color name="bubble_user_text">#1E1B4B</color>
    <color name="bubble_ai">#334155</color>
    <color name="bubble_ai_text">#F8FAFC</color>

    <!-- 技能卡片 -->
    <color name="skill_card_bg">#334155</color>
    <color name="skill_card_bg_pressed">#4338CA</color>
    <color name="skill_card_border">#475569</color>
</resources>
```

#### dimens.xml (间距资源)

**文件**: `app/src/main/res/values/dimens.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 间距 -->
    <dimen name="spacing_xs">4dp</dimen>
    <dimen name="spacing_sm">8dp</dimen>
    <dimen name="spacing_md">16dp</dimen>
    <dimen name="spacing_lg">24dp</dimen>
    <dimen name="spacing_xl">32dp</dimen>
    <dimen name="spacing_xxl">48dp</dimen>

    <!-- 圆角 -->
    <dimen name="corner_sm">8dp</dimen>
    <dimen name="corner_md">12dp</dimen>
    <dimen name="corner_lg">16dp</dimen>
    <dimen name="corner_xl">18dp</dimen>
    <dimen name="corner_full">50dp</dimen>

    <!-- Avatar -->
    <dimen name="avatar_size_main">120dp</dimen>
    <dimen name="avatar_ring_diameter">160dp</dimen>
    <dimen name="avatar_wave_area">20dp</dimen>
    <dimen name="avatar_size_small">80dp</dimen>
    <dimen name="avatar_size_widget">48dp</dimen>

    <!-- 技能卡片 -->
    <dimen name="skill_card_size">80dp</dimen>
    <dimen name="skill_icon_size">32dp</dimen>
    <dimen name="skill_card_corner">16dp</dimen>

    <!-- 触摸目标 -->
    <dimen name="touch_target_min">44dp</dimen>

    <!-- 阴影 -->
    <dimen name="elevation_card">4dp</dimen>
    <dimen name="elevation_button">12dp</dimen>
    <dimen name="elevation_dialog">48dp</dimen>
</resources>
```

### 5.4 设计系统规格

#### 颜色系统 (Color Palette)

| 用途 | 浅色模式 | 深色模式 | 说明 |
|------|---------|---------|------|
| **Primary** | `#6366F1` (Indigo 500) | `#818CF8` (Indigo 400) | 主色调，按钮/强调 |
| **Secondary** | `#EC4899` (Pink 500) | `#F472B6` (Pink 400) | 次要强调，语音动画 |
| **Background** | `#FFFFFF` | `#121212` | 页面背景 |
| **Surface** | `#F8FAFC` (Slate 50) | `#1E293B` (Slate 800) | 卡片/气泡背景 |
| **SurfaceVariant** | `#F1F5F9` (Slate 100) | `#334155` (Slate 700) | 技能卡片背景 |
| **OnPrimary** | `#FFFFFF` | `#1E1B4B` | Primary 上文字 |
| **OnBackground** | `#1E293B` (Slate 800) | `#F8FAFC` (Slate 50) | 主文字 |
| **OnSurfaceVariant** | `#64748B` (Slate 500) | `#94A3B8` (Slate 400) | 次要文字 |
| **Outline** | `#E2E8F0` (Slate 200) | `#475569` (Slate 600) | 边框/分割线 |
| **Error** | `#EF4444` (Red 500) | `#F87171` (Red 400) | 错误状态 |

#### 字体系统 (Typography)

| 样式 | 字体 | 大小 | 行高 | 字重 | 用途 |
|------|------|------|------|------|------|
| **DisplayLarge** | Noto Sans SC | 32sp | 40sp | 700 | 欢迎语/状态文字 |
| **TitleLarge** | Noto Sans SC | 22sp | 28sp | 600 | 顶栏标题 |
| **TitleMedium** | Noto Sans SC | 16sp | 24sp | 600 | 技能卡片名称 |
| **BodyLarge** | Noto Sans SC | 16sp | 24sp | 400 | 对话气泡文字 |
| **BodyMedium** | Noto Sans SC | 14sp | 20sp | 400 | 辅助说明文字 |
| **LabelLarge** | Noto Sans SC | 14sp | 20sp | 500 | 按钮文字 |
| **LabelSmall** | Noto Sans SC | 11sp | 16sp | 500 | 时间戳/标签 |

#### 间距系统 (Spacing)

基于 4dp 网格系统：

| 名称 | 数值 | 用途 |
|------|------|------|
| **xs** | 4dp | 图标与文字间距 |
| **sm** | 8dp | 卡片内边距 |
| **md** | 16dp | 组件间距/页面边距 |
| **lg** | 24dp | 区块间距 |
| **xl** | 32dp | 大区块分隔 |
| **xxl** | 48dp | Avatar 与内容间距 |

#### 圆角系统 (Corner Radius)

| 组件 | 圆角 |
|------|------|
| 气泡 (用户) | 18dp (左侧圆) |
| 气泡 (AI) | 18dp (右侧圆) |
| 技能卡片 | 16dp |
| 按钮 | 12dp |
| 输入框 | 12dp |
| Avatar 外环 | 50% (圆形) |

#### 阴影系统 (Elevation)

| 组件 | 高度 | 阴影 |
|------|------|------|
| 技能卡片 | 2dp | `elevation: 4dp` |
| 顶栏 | 0dp | `elevation: 0dp` (需设置阴影) |
| 语音按钮 | 6dp | `elevation: 12dp` |
| 对话气泡 | 0dp | 无阴影，自然层叠 |
| 弹窗/Dialog | 24dp | `elevation: 48dp` |

### 5.5 动画规格

#### 动画时序 (Animation Timing)

| 动画类型 | 时长 | 缓动函数 |
|----------|------|----------|
| 微交互 (hover/press) | 150ms | `ease-out` |
| 状态切换 (fade/scale) | 200ms | `ease-in-out` |
| 面板展开/收起 | 300ms | `ease-out` |
| Avatar 状态动画 | 300-1500ms | `accelerate-decelerate` |
| 语音波形脉冲 | 1200ms (循环) | `linear` |
| 呼吸动画 | 3000ms (循环) | `accelerate-decelerate` |

#### Avatar 状态动画详细说明

| 状态 | 动画效果 | 参数 |
|------|---------|------|
| **IDLE** | 轻微呼吸缩放 | `scale: 1.0 → 1.03 → 1.0`, 周期 3000ms |
| **LISTENING** | 外环脉冲扩散 | `scale: 1.0 → 1.8 → 1.0`, `alpha: 1.0 → 0.2`, 周期 1200ms |
| **PROCESSING** | 360° 旋转 | `rotation: 0° → 360°`, 周期 1500ms |
| **SPEAKING** | 波形跳动 | `scaleX/Y: 0.8 → 1.2 → 0.8`, 周期 300ms |
| **HAPPY** | 向上跳跃 | `translationY: 0 → -30dp → 0`, 周期 500ms |
| **SAD** | 下沉渐隐 | `translationY: 0 → 10dp`, `alpha: 1.0 → 0.7`, 周期 500ms |

#### 手势交互

| 交互 | 行为 |
|------|------|
| 语音按钮按下 | `scale: 1.0 → 0.92`, `alpha: 1.0 → 0.8`, 持续至松开 |
| 技能卡片点击 | `scale: 1.0 → 0.95 → 1.0`, 涟漪效果从中心扩散 |
| 对话气泡点击 | 背景色加深 5%，显示复制/删除选项 |

### 5.6 组件状态规格

#### 语音按钮状态

| 状态 | 背景色 | 边框 | 图标 | 说明 |
|------|--------|------|------|------|
| **默认** | Primary | 无 | 麦克风图标 | 等待用户操作 |
| **按下** | Primary Dark | 加深 10% | 麦克风图标 + 波纹 | 正在录音 |
| **禁用** | Slate 300 | 虚线 | 麦克风图标 (灰) | 服务不可用 |
| **加载中** | Primary | 无 | 旋转圆环 | 处理中 |

#### 技能卡片状态

| 状态 | 背景色 | 边框 | 缩放 |
|------|--------|------|------|
| **默认** | SurfaceVariant | 无 | 1.0 |
| **按下** | Primary/10% | Primary/30% | 0.95 |
| **禁用** | Slate 100 | 无 | 0.98 |
| **选中** | Primary/15% | Primary 2dp | 1.0 |

#### 对话气泡状态

| 类型 | 背景色 | 圆角 | 最大宽度 |
|------|--------|------|----------|
| **用户气泡** | Primary | 18dp (左侧全圆) | 屏幕宽 - 80dp |
| **AI 气泡** | Surface | 18dp (右侧全圆) | 屏幕宽 - 80dp |
| **系统消息** | SurfaceVariant | 8dp | 屏幕宽 - 32dp |

### 5.7 无障碍设计 (Accessibility)

| 要求 | 规格 |
|------|------|
| **触摸目标最小尺寸** | 44×44dp (符合 WCAG 2.1) |
| **文字对比度** | 主文字 4.5:1，次要文字 3:1 |
| **内容描述** | Avatar 需提供 `contentDescription="小爱同学头像"` |
| **语音按钮** | `contentDescription="按住说话"` |
| **技能图标** | 每个图标需有 `contentDescription` |
| **动态效果** | 检测 `ReduceMotion`，必要时禁用动画 |
| **字体缩放** | 支持最大 200% 系统字体缩放 |

### 5.8 响应式布局

#### 断点定义

| 设备 | 屏幕宽度 | 布局调整 |
|------|----------|----------|
| 手机竖屏 | < 600dp | 单列，技能卡片 4 个/行 |
| 手机横屏 | 600-840dp | 单列，技能卡片 5 个/行 |
| 平板竖屏 | > 840dp | 双列对话区，右侧面板显示快捷操作 |

#### 安全区域

| 区域 | 边距 |
|------|------|
| 状态栏下方 | 至少 24dp 或使用 `WindowInsetsCompat` |
| 导航栏下方 | 至少 16dp 或使用 `WindowInsetsCompat` |
| 侧边与内容 | 至少 16dp |

### 5.9 深色模式适配

深色模式关键适配点：

| 组件 | 浅色 | 深色 | 注意 |
|------|------|------|------|
| **背景** | `#FFFFFF` | `#121212` | 避免纯黑 `#000000` |
| **气泡 (用户)** | `#6366F1` | `#818CF8` | 对比度需 ≥ 4.5:1 |
| **气泡 (AI)** | `#F8FAFC` | `#1E293B` | 文字 `#F8FAFC` |
| **技能卡片** | `#F1F5F9` | `#334155` | 边框 `#475569` |
| **顶栏** | `#FFFFFF` | `#1E293B` | 标题 `#F8FAFC` |
| **头像背景** | `#EEF2FF` | `#312E81` | Avatar 外环发光色 |

---

## 六、Weather API 推荐

| API | 免费额度 | 说明 |
|-----|---------|------|
| **和风天气** | 1000次/天 | 国内数据准确，支持分钟级预报 |
| **OpenWeatherMap** | 60次/分钟 | 国际支持好 |
| **彩云天气** | 500次/天 | 分钟级预报，API 简洁 |

### 和风天气 API 示例

```kotlin
class HefengWeatherApi(private val apiKey: String) : WeatherApi {

    private val baseUrl = "https://devapi.qweather.com/v7"

    override suspend fun getWeather(location: String, timeRange: TimeRange): WeatherInfo {
        // 实现和风天气 API 调用
        // GET https://devapi.qweather.com/v7/weather/now?key=XXX&location=北京
        // GET https://devapi.qweather.com/v7/weather/3d?key=XXX&location=北京
    }
}
```

---

## 七、实施计划

### Phase 1: 核心框架 (1-2周)

| 任务 | 文件 | 说明 |
|------|------|------|
| 创建 Skill 接口 | `core/skill/Skill.kt` | 技能基类定义 |
| 创建 SkillContext | `core/skill/SkillContext.kt` | 上下文数据类 |
| 创建 ConversationManager | `core/conversation/ConversationManager.kt` | 对话管理 |
| 重构 IntentRouter | `core/intent/IntentRouter.kt` | 技能路由 |
| 创建 SkillModule | `core/di/SkillModule.kt` | Hilt 注入配置 |

### Phase 2: 基础技能 (1周)

| 任务 | 文件 | 说明 |
|------|------|------|
| MusicSkill | `core/skill/MusicSkill.kt` | 音乐播放技能 |
| AlarmSkill | `core/skill/AlarmSkill.kt` | 闹钟技能 |
| WeatherSkill | `core/skill/WeatherSkill.kt` | 天气技能 |
| 实现 AlarmScheduler | `data/alarm/AndroidAlarmScheduler.kt` | Android 闹钟实现 |
| 实现 WeatherApi | `data/weather/HefengWeatherApi.kt` | 天气 API 实现 |

### Phase 3: 智能家居 (1-2周)

| 任务 | 文件 | 说明 |
|------|------|------|
| SmartHomeSkill | `core/skill/SmartHomeSkill.kt` | 家居控制技能 |
| SmartDeviceRepository | `data/smarthome/SmartDeviceRepository.kt` | 设备抽象层 |
| 设备发现 | `data/smarthome/DeviceDiscovery.kt` | SSDP/mDNS 发现 |
| 设备控制 | `data/smarthome/DeviceController.kt` | 设备控制实现 |

### Phase 4: UI 优化 (1周)

| 任务 | 文件 | 说明 |
|------|------|------|
| AvatarView | `app/ui/main/AvatarView.kt` | 动画头像 |
| 小爱风格布局 | `res/layout/activity_main_xiaomi.xml` | 新布局 |
| 技能快捷卡片 | `app/ui/main/SkillCards.kt` | 快捷技能入口 |
| 状态动画 | `app/ui/main/StateAnimator.kt` | 状态切换动画 |

---

## 八、关键文件清单

### 新增文件

```
core/src/main/java/com/voiceassistant/core/
├── skill/
│   ├── Skill.kt              # 技能接口
│   ├── SkillContext.kt      # 上下文
│   ├── SkillResult.kt        # 结果
│   ├── SmartHomeSkill.kt     # 家居技能
│   ├── AlarmSkill.kt         # 闹钟技能
│   ├── WeatherSkill.kt       # 天气技能
│   └── MusicSkill.kt         # 音乐技能
├── conversation/
│   └── ConversationManager.kt # 对话管理
└── di/
    └── SkillModule.kt        # 注入模块

data/src/main/java/com/voiceassistant/data/
├── alarm/
│   └── AndroidAlarmScheduler.kt  # 闹钟实现
├── weather/
│   └── HefengWeatherApi.kt       # 天气API
└── smarthome/
    ├── SmartDeviceRepository.kt   # 设备仓库
    └── DeviceDiscovery.kt         # 设备发现
```

### 修改文件

```
core/src/main/java/com/voiceassistant/core/
└── intent/
    └── IntentRouter.kt      # 重构为技能路由

app/src/main/java/com/voiceassistant/app/
├── ui/main/
│   ├── MainActivity.kt      # 集成新UI
│   ├── AvatarView.kt        # 新增动画头像
│   └── SkillCards.kt        # 技能快捷入口
└── service/
    └── VoiceAssistantService.kt  # 集成 ConversationManager
```

---

## 九、向后兼容

### 保持现有功能

1. **VoicePipeline** - 保持不变，继续管理语音处理流程
2. **Sherpa-ONNX** - 保持不变，KWS/VAD/ASR/TTS 继续使用
3. **MusicRepository** - 保持接口兼容，MusicSkill 适配
4. **LLMRepository** - 保持接口兼容，作为技能路由的兜底

### Skill 接口适配

现有代码通过适配器模式接入 Skills 系统：

```kotlin
/**
 * 将现有 IntentRouter 作为 Skill 适配
 */
class LegacyIntentRouterSkill(
    private val intentRouter: IntentRouter
) : Skill {
    override val name = "原有意图"
    override val keywords = listOf("播放", "暂停", "继续")
    override val description = "兼容原有功能"

    override suspend fun execute(context: SkillContext): SkillResult {
        val response = intentRouter.handle(context.text)
        return SkillResult(response)
    }
}
```

---

## 十、总结

本方案将现有 `voice-assistant` 项目扩展为小爱同学风格的智能语音助手，核心改进：

| 维度 | 现有 | 增强后 |
|------|------|--------|
| 意图识别 | 规则匹配 | 技能系统 + LLM 兜底 |
| 对话能力 | 无状态 | 上下文记忆 + 人格化 |
| 技能扩展 | 硬编码 | 插件化 Skill 接口 |
| 智能家居 | 无 | SmartHomeSkill |
| 闹钟提醒 | 无 | AlarmSkill |
| 天气查询 | 依赖 LLM | 专业 WeatherSkill |
| UI 体验 | 基础对话 | 小爱风格 Avatar + 动画 |

**实施建议**: 按 Phase 顺序逐步实现，保持每个阶段的可用性。
