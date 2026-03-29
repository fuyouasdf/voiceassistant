# 小爱同学风格语音助手设计方案

> **版本**: 1.1
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
