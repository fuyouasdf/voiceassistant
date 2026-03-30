package com.voiceassistant.core.pipeline

/**
 * Converts Chinese characters to pinyin for Sherpa KWS keywords.txt format.
 *
 * Sherpa KWS expects pinyin format: "x iǎo ài tóng xué" (space-separated syllables)
 * This converter uses a mapping of common Chinese characters to their pinyin.
 */
class PinyinConverter {

    // Common Chinese characters to pinyin mapping
    // Format: pinyin with tone markers (Sherpa handles this)
    private val charToPinyin: Map<Char, String> = buildMapping()

    /**
     * Convert Chinese text to Sherpa-compatible pinyin format.
     * @param chinese Chinese text (e.g., "小爱同学")
     * @return Pinyin with spaces (e.g., "x iǎo ài tóng xué")
     */
    fun convert(chinese: String): String {
        return chinese.map { char ->
            charToPinyin[char] ?: char.toString()
        }.joinToString(" ") { it }
    }

    /**
     * Check if a character is supported (has pinyin mapping).
     */
    fun isCharSupported(char: Char): Boolean = charToPinyin.containsKey(char)

    /**
     * Check if all characters in a string are supported.
     */
    fun canConvert(chinese: String): Boolean = chinese.all { isCharSupported(it) }

    private fun buildMapping(): Map<Char, String> {
        return mapOf(
            // Common wake word characters
            '小' to "x iǎo",
            '爱' to "ài",
            '同' to "tóng",
            '学' to "xué",
            '同' to "tóng",
            '学' to "xué",

            // More common characters
            '你' to "n ǐ",
            '好' to "h ǎo",
            '问' to "w èn",
            '林' to "l ín",
            '美' to "m ěi",
            '丽' to "l ì",
            '西' to "x ī",
            '哥' to "g ē",
            '蛋' to "d àn",
            '艺' to "y ì",
            '米' to "m ǐ",
            '军' to "j ūn",

            // Additional common characters
            '在' to "z ài",
            '我' to "w ǒ",
            '吗' to "ma",
            '是' to "shì",
            '不' to "bù",
            '了' to "le",
            '一' to "yī",
            '个' to "gè",
            '人' to "rén",
            '们' to "men",
            '这' to "zhè",
            '那' to "nà",
            '什' to "shén",
            '么' to "me",
            '都' to "dōu",
            '可' to "kě",
            '以' to "yǐ",
            '他' to "tā",
            '她' to "tā",
            '它' to "tā",
            '和' to "hé",
            '的' to "de",
            '有' to "yǒu",
            '没' to "méi",
            '会' to "huì",
            '能' to "néng",
            '说' to "shuō",
            '看' to "kàn",
            '听' to "tīng",
            '来' to "lái",
            '去' to "qù",
            '走' to "zǒu",
            '出' to "chū",
            '入' to "rù",
            '开' to "kāi",
            '关' to "guān",
            '上' to "shàng",
            '下' to "xià",
            '左' to "zuǒ",
            '右' to "yòu",
            '前' to "qián",
            '后' to "hòu",
            '快' to "kuài",
            '慢' to "màn",
            '大' to "dà",
            '小' to "xiǎo",
            '长' to "cháng",
            '短' to "duǎn",
            '高' to "gāo",
            '低' to "dī",
            '多' to "duō",
            '少' to "shǎo",
            '冷' to "lěng",
            '热' to "rè",
            '新' to "xīn",
            '旧' to "jiù",
            '早' to "zǎo",
            '晚' to "wǎn",
            '现' to "xiàn",
            '在' to "zài",
            '时' to "shí",
            '候' to "hou",
            '今' to "jīn",
            '天' to "tiān",
            '明' to "míng",
            '日' to "rì",
            '月' to "yuè",
            '年' to "nián",
            '春' to "chūn",
            '夏' to "xià",
            '秋' to "qiū",
            '冬' to "dōng",
            '东' to "dōng",
            '西' to "xī",
            '南' to "nán",
            '北' to "běi",
            '中' to "zhōng",
            '国' to "guó",
            '人' to "rén",
            '中' to "zhōng",
            '文' to "wén",
            '英' to "yīng",
            '语' to "yǔ",
            '歌' to "gē",
            '曲' to "qǔ",
            '音' to "yīn",
            '乐' to "yuè",
            '播' to "bō",
            '放' to "fàng",
            '停' to "tíng",
            '始' to "shǐ",
            '继' to "jì",
            '续' to "xù",
            '再' to "zài",
            '次' to "cì",
            '第' to "dì",
            '一' to "yī",
            '二' to "èr",
            '三' to "sān",
            '四' to "sì",
            '五' to "wǔ",
            '六' to "liù",
            '七' to "qī",
            '八' to "bā",
            '九' to "jiǔ",
            '十' to "shí",
            '百' to "bǎi",
            '千' to "qiān",
            '万' to "wàn",
            '元' to "yuán",
            '块' to "kuài",
            '钱' to "qián",
            '买' to "mǎi",
            '卖' to "mài",
            '价' to "jià",
            '便' to "pián",
            '贵' to "guì",
            '便' to "biàn",
            '否' to "fǒu",
            '喜' to "xǐ",
            '欢' to "huān",
            '讨' to "tǎo",
            '厌' to "yàn",
            '想' to "xiǎng",
            '觉' to "jué",
            '感' to "gǎn",
            '知' to "zhī",
            '道' to "dào",
            '记' to "jì",
            '忘' to "wàng",
            '懂' to "dǒng",
            '明' to "míng",
            '白' to "bái"
        )
    }
}
