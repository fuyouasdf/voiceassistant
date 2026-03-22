package com.voiceassistant.core.sherpa

/**
 * 过滤 partial recognition 结果的工具类
 * 只在识别结果有实际新增文字时才通知，避免每字一行的问题
 */
class PartialResultFilter {
    private var lastText = ""

    /**
     * 判断是否应该通知新的 partial result
     * @param newText 新的识别结果
     * @return true 如果有新增文字需要通知
     */
    fun shouldNotify(newText: String): Boolean {
        if (newText.isNotEmpty() && newText.length > lastText.length) {
            lastText = newText
            return true
        }
        return false
    }

    /**
     * 重置过滤器状态
     */
    fun reset() {
        lastText = ""
    }
}
