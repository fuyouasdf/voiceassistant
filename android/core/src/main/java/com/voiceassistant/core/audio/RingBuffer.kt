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

package com.voiceassistant.core.audio

import timber.log.Timber

/**
 * Ring buffer for storing audio data in a circular fashion.
 * When the buffer is full, oldest data is overwritten.
 *
 * Used to preserve pre-wake audio for ASR context.
 */
class RingBuffer(
    private val capacitySamples: Int
) {
    private val buffer = FloatArray(capacitySamples)
    private var writeIndex = 0
    private var readIndex = 0
    private var size = 0

    /**
     * Write audio samples to the ring buffer.
     * If buffer is full, oldest data is overwritten.
     */
    fun write(samples: FloatArray) {
        for (sample in samples) {
            buffer[writeIndex] = sample
            writeIndex = (writeIndex + 1) % capacitySamples

            if (size < capacitySamples) {
                size++
            } else {
                // Buffer is full, move readIndex forward (oldest data discarded)
                readIndex = (readIndex + 1) % capacitySamples
            }
        }
    }

    /**
     * Read all available data from the ring buffer.
     * Returns data in chronological order (oldest first).
     */
    fun read(): FloatArray {
        if (size == 0) return FloatArray(0)

        val result = FloatArray(size)
        val startIndex = if (size < capacitySamples) 0 else readIndex

        for (i in 0 until size) {
            result[i] = buffer[(startIndex + i) % capacitySamples]
        }

        return result
    }

    /**
     * Read the most recent N samples.
     * If fewer than n samples available, returns all available.
     */
    fun readLast(n: Int): FloatArray {
        if (size == 0) return FloatArray(0)

        val count = minOf(n, size)
        val result = FloatArray(count)

        for (i in 0 until count) {
            val index = (writeIndex - 1 - i + capacitySamples) % capacitySamples
            result[count - 1 - i] = buffer[index]
        }

        return result
    }

    /**
     * Clear the buffer.
     */
    fun clear() {
        writeIndex = 0
        readIndex = 0
        size = 0
    }

    /**
     * Get current number of samples in buffer.
     */
    fun availableSamples(): Int = size

    /**
     * Check if buffer is empty.
     */
    fun isEmpty(): Boolean = size == 0

    /**
     * Check if buffer is full.
     */
    fun isFull(): Boolean = size == capacitySamples
}
