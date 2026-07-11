package com.enabwf.periodic

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

abstract class AnalyticsTabFragment(layoutId: Int) : Fragment(layoutId) {
    protected val viewModel: AnalyticsViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
    }

    override fun onResume() {
        super.onResume()
        viewModel.uiState.value?.let(::render)
    }

    protected abstract fun render(state: AnalyticsUiState)
}
