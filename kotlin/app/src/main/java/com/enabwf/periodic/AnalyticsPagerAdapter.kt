package com.enabwf.periodic

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class AnalyticsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> AnalyticsOverviewFragment()
        1 -> AnalyticsTimelineFragment()
        2 -> AnalyticsAdherenceFragment()
        3 -> AnalyticsPatternsFragment()
        else -> throw IllegalArgumentException("Unknown analytics tab: $position")
    }
}
