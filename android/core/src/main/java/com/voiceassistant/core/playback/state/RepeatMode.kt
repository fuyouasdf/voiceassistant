/*
 * Copyright (c) 2021 Auxio Project
 * RepeatMode.kt is part of Auxio.
 * Adapted for Voice Assistant project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.voiceassistant.core.playback.state

/**
 * Represents the current repeat mode of the player.
 */
enum class RepeatMode {
    /**
     * Do not repeat. Songs are played immediately, and playback is paused when the queue repeats.
     */
    NONE,

    /**
     * Repeat the whole queue. Songs are played immediately, and playback continues when the queue
     * repeats.
     */
    ALL,

    /**
     * Repeat the current song. A Song will be continuously played until skipped.
     */
    TRACK;

    /**
     * Increment the mode.
     *
     * @return If [NONE], [ALL]. If [ALL], [TRACK]. If [TRACK], [NONE].
     */
    fun increment() =
        when (this) {
            NONE -> ALL
            ALL -> TRACK
            TRACK -> NONE
        }
}
