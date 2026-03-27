package com.voiceassistant.app.ui.music

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * 音乐页面 ViewPager 适配器
 */
class MusicPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> MusicLibraryFragment()
            1 -> PlaylistsFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
