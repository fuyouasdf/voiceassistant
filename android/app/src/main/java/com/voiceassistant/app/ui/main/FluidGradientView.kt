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

package com.voiceassistant.app.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.voiceassistant.app.R
import com.voiceassistant.core.pipeline.PipelineState
import kotlin.math.cos
import kotlin.math.sin

/**
 * 流体渐变球体 View
 * 用于显示语音助手状态的视觉中心
 * 支持 6 种状态的渐变颜色和动画效果
 */
class FluidGradientView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint 对象
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 状态颜色
    private var currentState: PipelineState = PipelineState.IDLE

    // 动画相关
    private var breathAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private var waveAnimator: ValueAnimator? = null

    // 动画参数
    private var breathScale = 0.85f
    private var pulseAlpha = 0f
    private var waveOffset = 0f

    // 尺寸参数
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private var glowRadius = 0f

    // 音频波形数据
    private var audioData: FloatArray = FloatArray(0)
    private var waveformAmplitudes: FloatArray = FloatArray(32) { 0f }

    // 状态颜色配置
    private data class StateColors(
        val startColor: Int,
        val endColor: Int,
        val glowColor: Int
    )

    private val stateColors = mapOf(
        PipelineState.IDLE to StateColors(
            ContextCompat.getColor(context, R.color.gradient_idle_start),
            ContextCompat.getColor(context, R.color.gradient_idle_end),
            ContextCompat.getColor(context, R.color.glow_primary)
        ),
        PipelineState.LISTENING to StateColors(
            ContextCompat.getColor(context, R.color.gradient_listening_start),
            ContextCompat.getColor(context, R.color.gradient_listening_end),
            ContextCompat.getColor(context, R.color.glow_listening)
        ),
        PipelineState.RECORDING to StateColors(
            ContextCompat.getColor(context, R.color.gradient_recording_start),
            ContextCompat.getColor(context, R.color.gradient_recording_end),
            ContextCompat.getColor(context, R.color.glow_recording)
        ),
        PipelineState.RECOGNIZING to StateColors(
            ContextCompat.getColor(context, R.color.gradient_recognizing_start),
            ContextCompat.getColor(context, R.color.gradient_recognizing_end),
            ContextCompat.getColor(context, R.color.warning)
        ),
        PipelineState.THINKING to StateColors(
            ContextCompat.getColor(context, R.color.gradient_thinking_start),
            ContextCompat.getColor(context, R.color.gradient_thinking_end),
            ContextCompat.getColor(context, R.color.glow_thinking)
        ),
        PipelineState.SPEAKING to StateColors(
            ContextCompat.getColor(context, R.color.gradient_speaking_start),
            ContextCompat.getColor(context, R.color.gradient_speaking_end),
            ContextCompat.getColor(context, R.color.glow_speaking)
        )
    )

    init {
        // 初始化呼吸动画
        startBreathAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = minOf(w, h) / 2f * 0.75f
        glowRadius = radius * 1.4f

        updateGradient()
    }

    private fun updateGradient() {
        val colors = stateColors[currentState] ?: stateColors[PipelineState.IDLE]!!

        // 外发光渐变
        glowPaint.shader = RadialGradient(
            centerX, centerY, glowRadius,
            intArrayOf(
                adjustAlpha(colors.glowColor, 0.4f),
                adjustAlpha(colors.glowColor, 0.2f),
                adjustAlpha(colors.glowColor, 0f)
            ),
            floatArrayOf(0.5f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )

        // 主球体渐变
        mainPaint.shader = RadialGradient(
            centerX - radius * 0.3f, centerY - radius * 0.3f, radius * 1.5f,
            intArrayOf(
                adjustColor(colors.startColor, 1.3f),
                colors.startColor,
                colors.endColor,
                adjustColor(colors.endColor, 0.8f)
            ),
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )

        // 内部高光
        highlightPaint.shader = RadialGradient(
            centerX - radius * 0.25f, centerY - radius * 0.25f, radius * 0.6f,
            intArrayOf(
                adjustAlpha(0xFFFFFFFF.toInt(), 0.4f),
                adjustAlpha(0xFFFFFFFF.toInt(), 0f)
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val colors = stateColors[currentState] ?: stateColors[PipelineState.IDLE]!!

        when (currentState) {
            PipelineState.INITIALIZING -> drawIdleState(canvas, colors)
            PipelineState.IDLE -> drawIdleState(canvas, colors)
            PipelineState.WAKEWORD_DETECTED -> drawListeningState(canvas, colors) // Use listening visual for wake word
            PipelineState.LISTENING -> drawListeningState(canvas, colors)
            PipelineState.RECORDING -> drawRecordingState(canvas, colors)
            PipelineState.RECOGNIZING -> drawRecognizingState(canvas, colors)
            PipelineState.THINKING -> drawThinkingState(canvas, colors)
            PipelineState.SPEAKING -> drawSpeakingState(canvas, colors)
        }

        // 绘制中心高光
        canvas.save()
        canvas.scale(breathScale, breathScale, centerX, centerY)
        canvas.drawCircle(centerX - radius * 0.25f, centerY - radius * 0.25f, radius * 0.3f, highlightPaint)
        canvas.restore()
    }

    private fun drawIdleState(canvas: Canvas, colors: StateColors) {
        // 外发光
        canvas.drawCircle(centerX, centerY, glowRadius * breathScale, glowPaint)

        // 主球体 - 带呼吸效果
        canvas.save()
        canvas.scale(breathScale, breathScale, centerX, centerY)
        canvas.drawCircle(centerX, centerY, radius, mainPaint)
        canvas.restore()

        // 内部波纹动画
        drawInnerRipples(canvas, colors)
    }

    private fun drawListeningState(canvas: Canvas, colors: StateColors) {
        // 脉动外发光
        val pulseRadius = glowRadius * (1f + pulseAlpha * 0.2f)
        val pulseGlowPaint = Paint(glowPaint).apply {
            alpha = (255 * (1f - pulseAlpha * 0.5f)).toInt()
        }
        canvas.drawCircle(centerX, centerY, pulseRadius, pulseGlowPaint)

        // 主球体
        canvas.drawCircle(centerX, centerY, radius * 1.05f, mainPaint)

        // 内光晕
        innerGlowPaint.shader = RadialGradient(
            centerX, centerY, radius * 0.9f,
            intArrayOf(
                adjustAlpha(colors.endColor, 0.3f + pulseAlpha * 0.3f),
                adjustAlpha(colors.endColor, 0f)
            ),
            floatArrayOf(0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius * 0.9f, innerGlowPaint)
    }

    private fun drawRecordingState(canvas: Canvas, colors: StateColors) {
        // 外发光
        canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)

        // 主球体
        canvas.drawCircle(centerX, centerY, radius, mainPaint)

        // 音频波形可视化
        drawWaveform(canvas, colors)
    }

    private fun drawRecognizingState(canvas: Canvas, colors: StateColors) {
        // 外发光
        canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)

        // 主球体
        canvas.drawCircle(centerX, centerY, radius, mainPaint)

        // 旋转加载环
        drawRotatingRing(canvas, colors)
    }

    private fun drawThinkingState(canvas: Canvas, colors: StateColors) {
        // 外发光
        canvas.drawCircle(centerX, centerY, glowRadius * breathScale, glowPaint)

        // 主球体（轻微变形）
        canvas.save()
        val scaleX = 1f + sin(waveOffset * 2) * 0.05f
        val scaleY = 1f - sin(waveOffset * 2) * 0.05f
        canvas.scale(scaleX, scaleY, centerX, centerY)
        canvas.drawCircle(centerX, centerY, radius, mainPaint)
        canvas.restore()

        // 流动的光点
        drawFlowingParticles(canvas, colors)
    }

    private fun drawSpeakingState(canvas: Canvas, colors: StateColors) {
        // 扩散波纹
        val rippleCount = 3
        for (i in 0 until rippleCount) {
            val rippleProgress = (waveOffset + i * 0.33f) % 1f
            val rippleRadius = radius + rippleProgress * radius * 0.8f
            val rippleAlpha = (1f - rippleProgress) * 0.5f

            val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f - rippleProgress * 3f
                color = adjustAlpha(colors.glowColor, rippleAlpha)
            }
            canvas.drawCircle(centerX, centerY, rippleRadius, ripplePaint)
        }

        // 主球体
        canvas.drawCircle(centerX, centerY, radius, mainPaint)
    }

    private fun drawWaveform(canvas: Canvas, colors: StateColors) {
        val barCount = waveformAmplitudes.size
        val barWidth = radius * 0.8f / barCount
        val maxHeight = radius * 0.5f
        val startX = centerX - radius * 0.7f
        val baselineY = centerY

        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.startColor
        }

        for (i in 0 until barCount) {
            val amplitude = waveformAmplitudes[i]
            val barHeight = amplitude * maxHeight

            val left = startX + i * barWidth
            val top = baselineY - barHeight
            val right = left + barWidth * 0.7f
            val bottom = baselineY + barHeight

            // 渐变色
            barPaint.shader = LinearGradient(
                left, top, left, bottom,
                intArrayOf(
                    adjustColor(colors.startColor, 1.5f),
                    colors.startColor,
                    adjustColor(colors.endColor, 0.8f)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )

            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, barWidth * 0.3f, barWidth * 0.3f, barPaint)
        }
    }

    private fun drawRotatingRing(canvas: Canvas, colors: StateColors) {
        val ringRadius = radius * 0.7f
        val segmentCount = 8
        val segmentLength = 0.3f // 弧长比例

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            color = colors.startColor
        }

        val rotation = waveOffset * 360
        for (i in 0 until segmentCount) {
            val startAngle = rotation + i * (360f / segmentCount)
            val sweepAngle = 360f / segmentCount * segmentLength

            val rect = RectF(
                centerX - ringRadius,
                centerY - ringRadius,
                centerX + ringRadius,
                centerY + ringRadius
            )
            canvas.drawArc(rect, startAngle, sweepAngle, false, ringPaint)
        }
    }

    private fun drawFlowingParticles(canvas: Canvas, colors: StateColors) {
        val particleCount = 12
        val particleRadius = 8f
        val orbitRadius = radius * 0.5f

        val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = adjustColor(colors.startColor, 1.5f)
        }

        for (i in 0 until particleCount) {
            val angle = (waveOffset * 360 + i * (360f / particleCount)) * (Math.PI / 180)
            val x = centerX + (orbitRadius * cos(angle)).toFloat()
            val y = centerY + (orbitRadius * sin(angle)).toFloat()

            // 渐变粒子
            particlePaint.shader = RadialGradient(
                x, y, particleRadius * 2,
                intArrayOf(
                    adjustColor(colors.startColor, 1.5f),
                    adjustAlpha(colors.startColor, 0.5f),
                    adjustAlpha(colors.startColor, 0f)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )

            canvas.drawCircle(x, y, particleRadius, particlePaint)
        }
    }

    private fun drawInnerRipples(canvas: Canvas, colors: StateColors) {
        val rippleCount = 2
        for (i in 0 until rippleCount) {
            val rippleProgress = (waveOffset + i * 0.5f) % 1f
            val rippleRadius = radius * (0.3f + rippleProgress * 0.4f)
            val rippleAlpha = (1f - rippleProgress) * 0.15f

            val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = adjustAlpha(colors.startColor, rippleAlpha)
            }
            canvas.drawCircle(centerX, centerY, rippleRadius, ripplePaint)
        }
    }

    /**
     * 设置当前状态，触发相应的动画
     */
    fun setState(state: PipelineState) {
        if (currentState == state) return

        currentState = state
        updateGradient()
        updateAnimationsForState()

        invalidate()
    }

    /**
     * 设置音频数据，用于波形显示
     */
    fun setAudioData(data: FloatArray) {
        audioData = data
        if (data.isNotEmpty()) {
            // 采样生成波形
            val sampleStep = data.size / waveformAmplitudes.size
            for (i in waveformAmplitudes.indices) {
                val startIdx = i * sampleStep
                val endIdx = minOf(startIdx + sampleStep, data.size)
                if (startIdx < data.size) {
                    var sum = 0f
                    for (j in startIdx until endIdx) {
                        sum += kotlin.math.abs(data[j])
                    }
                    waveformAmplitudes[i] = (sum / (endIdx - startIdx)) * 3f // 放大
                }
            }
        }
        invalidate()
    }

    private fun startBreathAnimation() {
        breathAnimator?.cancel()
        breathAnimator = ValueAnimator.ofFloat(0.85f, 1.0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                breathScale = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                pulseAlpha = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startWaveAnimation() {
        waveAnimator?.cancel()
        waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = null // Linear
            addUpdateListener { animator ->
                waveOffset = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun updateAnimationsForState() {
        when (currentState) {
            PipelineState.INITIALIZING -> {
                // Same as IDLE while initializing
                startBreathAnimation()
                startWaveAnimation()
                pulseAnimator?.cancel()
                pulseAlpha = 0f
            }
            PipelineState.IDLE -> {
                startBreathAnimation()
                startWaveAnimation()
                pulseAnimator?.cancel()
                pulseAlpha = 0f
            }
            PipelineState.WAKEWORD_DETECTED -> {
                // Wake word detected - show pulse animation briefly
                breathAnimator?.cancel()
                breathScale = 1f
                startPulseAnimation()
                startWaveAnimation()
            }
            PipelineState.LISTENING -> {
                breathAnimator?.cancel()
                breathScale = 1f
                startPulseAnimation()
                startWaveAnimation()
            }
            PipelineState.RECORDING -> {
                breathAnimator?.cancel()
                pulseAnimator?.cancel()
                breathScale = 1f
                pulseAlpha = 0f
                startWaveAnimation()
            }
            PipelineState.RECOGNIZING -> {
                breathAnimator?.cancel()
                pulseAnimator?.cancel()
                breathScale = 1f
                startWaveAnimation()
            }
            PipelineState.THINKING -> {
                startBreathAnimation()
                startWaveAnimation()
            }
            PipelineState.SPEAKING -> {
                breathAnimator?.cancel()
                breathScale = 1f
                startWaveAnimation()
            }
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (0xFF * factor).toInt().coerceIn(0, 0xFF)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun adjustColor(color: Int, factor: Float): Int {
        val r = ((color shr 16) and 0xFF) * factor
        val g = ((color shr 8) and 0xFF) * factor
        val b = (color and 0xFF) * factor
        return (0xFF000000.toInt()) or
                ((r.toInt().coerceIn(0, 0xFF) shl 16)) or
                ((g.toInt().coerceIn(0, 0xFF) shl 8)) or
                b.toInt().coerceIn(0, 0xFF)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        breathAnimator?.cancel()
        pulseAnimator?.cancel()
        waveAnimator?.cancel()
    }
}