/*
 * Copyright (c) 2024 Auxio Project
 * QueueFragment.kt is part of Auxio.
 * Adapted for Voice Assistant project.
 */

package com.voiceassistant.app.ui.playback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voiceassistant.app.databinding.FragmentQueueBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Queue fragment showing the current playback queue.
 */
@AndroidEntryPoint
class QueueFragment : Fragment() {

    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!

    private val playbackViewModel: PlaybackViewModel by activityViewModels()

    private val queueAdapter = QueueAdapter(
        onItemClick = { index ->
            playbackViewModel.goto(index)
        },
        onRemoveClick = { index ->
            playbackViewModel.removeQueueItem(index)
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupWindowInsets()
        setupRecyclerView()
        setupListeners()
        observeQueue()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                systemBars.top,
                v.paddingRight,
                systemBars.bottom
            )
            insets
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerQueue.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = queueAdapter
        }

        // Setup drag and drop for reordering
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                playbackViewModel.moveQueueItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                playbackViewModel.removeQueueItem(position)
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerQueue)
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun observeQueue() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    playbackViewModel.queue.collectLatest { queue ->
                        queueAdapter.submitList(queue)
                        binding.tvEmpty.visibility =
                            if (queue.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    playbackViewModel.queueIndex.collectLatest { index ->
                        queueAdapter.setCurrentIndex(index)
                    }
                }
            }
        }
    }
}
