package com.voiceassistant.core.intent

import timber.log.Timber

/**
 * 中文模糊匹配器
 * 用于语音识别结果与音乐库歌名/歌手名的模糊匹配
 *
 * 支持：
 * - 拼音首字母匹配（如 "ZJL" 匹配 "周杰伦"）
 * - 编辑距离匹配（处理同音字错误）
 */
object ChineseMatcher {

    /**
     * 汉字到拼音首字母的映射（简化版，仅包含常用汉字）
     * 格式：汉字 -> 首字母大写
     */
    private val charToInitial: Map<Char, Char> = mapOf(
        // 声母 b
        '八' to 'B', '把' to 'B', '爸' to 'B', '吧' to 'B', '白' to 'B', '百' to 'B', '拜' to 'B', '班' to 'B', '半' to 'B', '办' to 'B', '帮' to 'B', '保' to 'B', '报' to 'B', '北' to 'B', '被' to 'B', '本' to 'B', '比' to 'B', '笔' to 'B', '边' to 'B', '变' to 'B', '别' to 'B', '病' to 'B', '不' to 'B', '步' to 'B', '部' to 'B',
        // 声母 c
        '菜' to 'C', '彩' to 'C', '参' to 'C', '草' to 'C', '层' to 'C', '查' to 'C', '茶' to 'C', '差' to 'C', '产' to 'C', '常' to 'C', '场' to 'C', '唱' to 'C', '超' to 'C', '朝' to 'C', '车' to 'C', '成' to 'C', '城' to 'C', '吃' to 'C', '出' to 'C', '除' to 'C', '楚' to 'C', '穿' to 'C', '春' to 'C', '词' to 'C', '此' to 'C', '次' to 'C', '从' to 'C', '村' to 'C', '错' to 'C',
        // 声母 d
        '大' to 'D', '带' to 'D', '代' to 'D', '单' to 'D', '但' to 'D', '蛋' to 'D', '道' to 'D', '得' to 'D', '灯' to 'D', '等' to 'D', '低' to 'D', '底' to 'D', '点' to 'D', '电' to 'D', '店' to 'D', '顶' to 'D', '定' to 'D', '丢' to 'D', '东' to 'D', '冬' to 'D', '懂' to 'D', '动' to 'D', '都' to 'D', '读' to 'D', '短' to 'D', '段' to 'D', '对' to 'D', '队' to 'D', '多' to 'D', '夺' to 'D', '朵' to 'D',
        // 声母 f
        '发' to 'F', '法' to 'F', '翻' to 'F', '反' to 'F', '方' to 'F', '房' to 'F', '放' to 'F', '飞' to 'F', '非' to 'F', '费' to 'F', '分' to 'F', '纷' to 'F', '风' to 'F', '封' to 'F', '服' to 'F', '福' to 'F', '父' to 'F', '付' to 'F', '负' to 'F', '附' to 'F', '复' to 'F', '副' to 'F', '富' to 'F',
        // 声母 g
        '该' to 'G', '改' to 'G', '感' to 'G', '刚' to 'G', '高' to 'G', '告' to 'G', '哥' to 'G', '歌' to 'G', '个' to 'G', '给' to 'G', '根' to 'G', '跟' to 'G', '更' to 'G', '工' to 'G', '公' to 'G', '共' to 'G', '狗' to 'G', '够' to 'G', '古' to 'G', '故' to 'G', '瓜' to 'G', '挂' to 'G', '关' to 'G', '管' to 'G', '光' to 'G', '广' to 'G', '贵' to 'G', '国' to 'G', '果' to 'G', '过' to 'G',
        // 声母 h
        '哈' to 'H', '还' to 'H', '孩' to 'H', '海' to 'H', '害' to 'H', '汉' to 'H', '号' to 'H', '喝' to 'H', '河' to 'H', '黑' to 'H', '很' to 'H', '红' to 'H', '后' to 'H', '候' to 'H', '呼' to 'H', '湖' to 'H', '虎' to 'H', '护' to 'H', '花' to 'H', '华' to 'H', '化' to 'H', '画' to 'H', '话' to 'H', '坏' to 'H', '欢' to 'H', '还' to 'H', '换' to 'H', '黄' to 'H', '回' to 'H', '会' to 'H', '汇' to 'H',
        // 声母 j
        '机' to 'J', '鸡' to 'J', '级' to 'J', '极' to 'J', '几' to 'J', '己' to 'J', '记' to 'J', '季' to 'J', '继' to 'J', '济' to 'J', '家' to 'J', '加' to 'J', '架' to 'J', '假' to 'J', '嫁' to 'J', '尖' to 'J', '间' to 'J', '见' to 'J', '建' to 'J', '江' to 'J', '姜' to 'J', '将' to 'J', '讲' to 'J', '奖' to 'J', '交' to 'J', '脚' to 'J', '角' to 'J', '叫' to 'J', '街' to 'J', '节' to 'J', '姐' to 'J', '今' to 'J', '金' to 'J', '近' to 'J', '进' to 'J', '京' to 'J', '经' to 'J', '精' to 'J', '井' to 'J', '静' to 'J', '九' to 'J', '酒' to 'J', '久' to 'J', '旧' to 'J', '就' to 'J', '举' to 'J', '句' to 'J', '巨' to 'J', '具' to 'J', '据' to 'J', '距' to 'J', '觉' to 'J',
        // 声母 k
        '开' to 'K', '看' to 'K', '考' to 'K', '靠' to 'K', '科' to 'K', '可' to 'K', '课' to 'K', '刻' to 'K', '客' to 'K', '口' to 'K', '哭' to 'K', '苦' to 'K', '快' to 'K', '块' to 'K',
        // 声母 l
        '拉' to 'L', '来' to 'L', '赖' to 'L', '蓝' to 'L', '老' to 'L', '乐' to 'L', '雷' to 'L', '累' to 'L', '冷' to 'L', '离' to 'L', '里' to 'L', '理' to 'L', '礼' to 'L', '力' to 'L', '历' to 'L', '立' to 'L', '利' to 'L', '连' to 'L', '恋' to 'L', '练' to 'L', '凉' to 'L', '两' to 'L', '亮' to 'L', '量' to 'L', '林' to 'L', '零' to 'L', '领' to 'L', '另' to 'L', '令' to 'L', '留' to 'L', '流' to 'L', '六' to 'L', '龙' to 'L', '楼' to 'L', '漏' to 'L', '路' to 'L', '露' to 'L', '旅' to 'L', '绿' to 'L', '乱' to 'L', '略' to 'L',
        // 声母 m
        '妈' to 'M', '马' to 'M', '吗' to 'M', '买' to 'M', '卖' to 'M', '慢' to 'M', '满' to 'M', '忙' to 'M', '毛' to 'M', '没' to 'M', '每' to 'M', '美' to 'M', '妹' to 'M', '门' to 'M', '们' to 'M', '米' to 'M', '面' to 'M', '民' to 'M', '明' to 'M', '名' to 'M', '命' to 'M', '母' to 'M', '木' to 'M', '目' to 'M', '牧' to 'M', '拿' to 'N', '哪' to 'N', '那' to 'N', '奶' to 'N', '男' to 'N', '南' to 'N', '呢' to 'N', '内' to 'N', '能' to 'N', '你' to 'N', '年' to 'N', '念' to 'N', '娘' to 'N', '鸟' to 'N', '您' to 'N', '牛' to 'N', '农' to 'N', '弄' to 'N', '女' to 'N', '暖' to 'N',
        // 声母 p
        '怕' to 'P', '拍' to 'P', '排' to 'P', '旁' to 'P', '跑' to 'P', '朋' to 'P', '皮' to 'P', '片' to 'P', '票' to 'P', '漂' to 'P', '品' to 'P', '平' to 'P', '评' to 'P', '破' to 'P', '普' to 'P',
        // 声母 q
        '七' to 'Q', '期' to 'Q', '其' to 'Q', '奇' to 'Q', '骑' to 'Q', '起' to 'Q', '气' to 'Q', '汽' to 'Q', '器' to 'Q', '恰' to 'Q', '千' to 'Q', '前' to 'Q', '钱' to 'Q', '浅' to 'Q', '强' to 'Q', '墙' to 'Q', '桥' to 'Q', '巧' to 'Q', '青' to 'Q', '轻' to 'Q', '清' to 'Q', '晴' to 'Q', '情' to 'Q', '请' to 'Q', '秋' to 'Q', '求' to 'Q', '球' to 'Q', '区' to 'Q', '去' to 'Q', '趣' to 'Q', '全' to 'Q', '却' to 'Q',
        // 声母 r
        '然' to 'R', '让' to 'R', '热' to 'R', '人' to 'R', '认' to 'R', '日' to 'R', '容' to 'R', '肉' to 'R', '如' to 'R', '入' to 'R',
        // 声母 s
        '撒' to 'S', '三' to 'S', '嗓' to 'S', '色' to 'S', '山' to 'S', '上' to 'S', '少' to 'S', '社' to 'S', '深' to 'S', '什' to 'S', '生' to 'S', '声' to 'S', '师' to 'S', '十' to 'S', '时' to 'S', '实' to 'S', '食' to 'S', '始' to 'S', '使' to 'S', '世' to 'S', '市' to 'S', '事' to 'S', '是' to 'S', '室' to 'S', '试' to 'S', '视' to 'S', '收' to 'S', '手' to 'S', '首' to 'S', '受' to 'S', '书' to 'S', '树' to 'S', '双' to 'S', '水' to 'S', '税' to 'S', '顺' to 'S', '说' to 'S', '思' to 'S', '死' to 'S', '四' to 'S', '送' to 'S', '诉' to 'S', '速' to 'S', '素' to 'S', '宿' to 'S', '算' to 'S', '虽' to 'S', '随' to 'S', '岁' to 'S',
        // 声母 t
        '他' to 'T', '她' to 'T', '它' to 'T', '台' to 'T', '太' to 'T', '态' to 'T', '谈' to 'T', '汤' to 'T', '糖' to 'T', '特' to 'T', '疼' to 'T', '提' to 'T', '题' to 'T', '体' to 'T', '天' to 'T', '田' to 'T', '条' to 'T', '铁' to 'T', '听' to 'T', '停' to 'T', '通' to 'T', '同' to 'T', '头' to 'T', '图' to 'T', '土' to 'T', '团' to 'T', '推' to 'T', '腿' to 'T', '脱' to 'T',
        // 声母 w
        '挖' to 'W', '瓦' to 'W', '外' to 'W', '玩' to 'W', '晚' to 'W', '万' to 'W', '王' to 'W', '往' to 'W', '网' to 'W', '望' to 'W', '忘' to 'W', '危' to 'W', '位' to 'W', '文' to 'W', '问' to 'W', '我' to 'W', '屋' to 'W', '五' to 'W', '午' to 'W', '物' to 'W', '务' to 'W',
        // 声母 x
        '西' to 'X', '息' to 'X', '希' to 'X', '习' to 'X', '洗' to 'X', '喜' to 'X', '系' to 'X', '细' to 'X', '夏' to 'X', '先' to 'X', '现' to 'X', '线' to 'X', '想' to 'X', '向' to 'X', '象' to 'X', '像' to 'X', '小' to 'X', '校' to 'X', '笑' to 'X', '些' to 'X', '写' to 'X', '谢' to 'X', '新' to 'X', '心' to 'X', '信' to 'X', '星' to 'X', '行' to 'X', '形' to 'X', '醒' to 'X', '姓' to 'X', '休' to 'X', '修' to 'X', '需' to 'X', '许' to 'X', '学' to 'X', '雪' to 'X',
        // 声母 y
        '压' to 'Y', '呀' to 'Y', '牙' to 'Y', '言' to 'Y', '研' to 'Y', '眼' to 'Y', '演' to 'Y', '阳' to 'Y', '养' to 'Y', '样' to 'Y', '药' to 'Y', '要' to 'Y', '爷' to 'Y', '也' to 'Y', '夜' to 'Y', '叶' to 'Y', '业' to 'Y', '一' to 'Y', '医' to 'Y', '衣' to 'Y', '以' to 'Y', '已' to 'Y', '意' to 'Y', '易' to 'Y', '因' to 'Y', '音' to 'Y', '银' to 'Y', '印' to 'Y', '英' to 'Y', '影' to 'Y', '用' to 'Y', '由' to 'Y', '油' to 'Y', '游' to 'Y', '友' to 'Y', '有' to 'Y', '又' to 'Y', '右' to 'Y', '鱼' to 'Y', '雨' to 'Y', '语' to 'Y', '元' to 'Y', '原' to 'Y', '园' to 'Y', '远' to 'Y', '院' to 'Y', '愿' to 'Y', '月' to 'Y', '乐' to 'Y', '越' to 'Y', '云' to 'Y', '运' to 'Y',
        // 声母 z
        '在' to 'Z', '再' to 'Z', '早' to 'Z', '怎' to 'Z', '造' to 'Z', '噪' to 'Z', '责' to 'Z', '贼' to 'Z', '怎' to 'Z', '增' to 'Z', '站' to 'Z', '张' to 'Z', '找' to 'Z', '照' to 'Z', '者' to 'Z', '这' to 'Z', '真' to 'Z', '正' to 'Z', '政' to 'Z', '之' to 'Z', '只' to 'Z', '知' to 'Z', '执' to 'Z', '直' to 'Z', '值' to 'Z', '指' to 'Z', '至' to 'Z', '治' to 'Z', '中' to 'Z', '钟' to 'Z', '周' to 'Z', '州' to 'Z', '主' to 'Z', '住' to 'Z', '注' to 'Z', '祝' to 'Z', '著' to 'Z', '抓' to 'Z', '专' to 'Z', '转' to 'Z', '装' to 'Z', '准' to 'Z', '子' to 'Z', '自' to 'Z', '字' to 'Z', '走' to 'Z', '租' to 'Z', '足' to 'Z', '组' to 'Z', '最' to 'Z', '昨' to 'Z', '左' to 'Z', '作' to 'Z', '做' to 'Z', '坐' to 'Z', '座' to 'Z',
        // 特殊字符
        '周' to 'Z', '杰' to 'J', '伦' to 'L',  // 周杰伦
        '双' to 'S', '截' to 'J', '棍' to 'G',  // 双截棍
        '夜' to 'Y', '曲' to 'Q',  // 夜曲
        '青' to 'Q', '花' to 'H', '瓷' to 'C'  // 青花瓷
    )

    /**
     * 将汉字转换为拼音首字母
     * 例如："周杰伦" -> "ZJL"
     */
    fun toPinyinInitial(chinese: String): String {
        return chinese.map { char ->
            charToInitial[char] ?: char.uppercaseChar()
        }.joinToString("")
    }

    /**
     * 计算编辑距离（Levenshtein Distance）
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        if (m == 0) return n
        if (n == 0) return m

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // 删除
                    dp[i][j - 1] + 1,      // 插入
                    dp[i - 1][j - 1] + cost // 替换
                )
            }
        }

        return dp[m][n]
    }

    /**
     * 计算相似度分数 (0.0 ~ 1.0)
     * 基于编辑距离
     */
    fun similarity(s1: String, s2: String): Double {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val maxLen = maxOf(s1.length, s2.length)
        val distance = levenshteinDistance(s1, s2)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    /**
     * 计算拼音首字母相似度
     */
    fun pinyinInitialSimilarity(s1: String, s2: String): Double {
        val p1 = toPinyinInitial(s1)
        val p2 = toPinyinInitial(s2)
        return similarity(p1, p2)
    }

    /**
     * 综合模糊匹配分数
     * 结合编辑距离和拼音首字母相似度
     */
    fun fuzzyScore(input: String, target: String): Double {
        if (input.isEmpty() || target.isEmpty()) return 0.0

        // 直接匹配
        if (input == target) return 1.0

        // 包含匹配
        if (target.contains(input) || input.contains(target)) {
            return 0.8 + (minOf(input.length, target.length).toDouble() / maxOf(input.length, target.length)) * 0.2
        }

        // 拼音首字母匹配（权重 60%）
        val pinyinScore = pinyinInitialSimilarity(input, target)

        // 编辑距离匹配（权重 40%）
        val editScore = similarity(input, target)

        return pinyinScore * 0.6 + editScore * 0.4
    }

    /**
     * 在候选列表中找到最佳匹配
     * @param input 用户输入
     * @param candidates 候选列表（如歌名列表）
     * @param threshold 匹配阈值，默认 0.5
     * @return 最佳匹配的 (候选, 分数) 或 null
     */
    fun findBestMatch(input: String, candidates: List<String>, threshold: Double = 0.5): Pair<String, Double>? {
        if (input.isEmpty() || candidates.isEmpty()) return null

        var bestMatch: Pair<String, Double>? = null
        var bestScore = threshold

        for (candidate in candidates) {
            val score = fuzzyScore(input, candidate)
            if (score > bestScore) {
                bestScore = score
                bestMatch = candidate to score
            }
        }

        return bestMatch
    }

    /**
     * 在候选列表中找到所有超过阈值的匹配
     */
    fun findAllMatches(input: String, candidates: List<String>, threshold: Double = 0.5): List<Pair<String, Double>> {
        if (input.isEmpty() || candidates.isEmpty()) return emptyList()

        return candidates.mapNotNull { candidate ->
            val score = fuzzyScore(input, candidate)
            if (score >= threshold) candidate to score else null
        }.sortedByDescending { it.second }
    }
}