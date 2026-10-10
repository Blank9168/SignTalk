package com.example.signtalk.ui.dictionary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.signtalk.R
import com.example.signtalk.SignTalkApp
import com.example.signtalk.databinding.FragmentDictionaryListBinding
import com.example.signtalk.domain.model.DictionaryEntry
import kotlinx.coroutines.launch

class DictionaryListFragment : Fragment() {

    private var _binding: FragmentDictionaryListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DictionaryViewModel by viewModels {
        viewModelFactory {
            initializer {
                val appContainer = (requireActivity().application as SignTalkApp).container
                DictionaryViewModel(appContainer.dictionaryRepository)
            }
        }
    }

    private lateinit var adapter: DictionaryAdapter

    /** Latest search results from the ViewModel, before the category filter is applied. */
    private var searchResults: List<DictionaryEntry> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDictionaryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DictionaryAdapter { entry ->
            findNavController().navigate(
                R.id.action_dictionaryList_to_detail,
                bundleOf("entryId" to entry.id)
            )
        }
        binding.entriesRecyclerView.layoutManager = GridLayoutManager(requireContext(), GRID_COLUMNS)
        binding.entriesRecyclerView.adapter = adapter

        // Shrink the "Add sign" button to just its icon while scrolling down the grid.
        binding.entriesRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) binding.addEntryFab.shrink() else if (dy < 0) binding.addEntryFab.extend()
            }
        })

        binding.searchInput.doAfterTextChanged { text ->
            viewModel.onQueryChange(text?.toString().orEmpty())
        }

        binding.filterChips.setOnCheckedStateChangeListener { _, _ -> showFilteredEntries() }

        binding.addEntryFab.setOnClickListener {
            AddDictionaryEntryDialogFragment().show(childFragmentManager, "add_dictionary_entry")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.entries.collect { entries ->
                    searchResults = entries
                    showFilteredEntries()
                }
            }
        }
    }

    private fun showFilteredEntries() {
        val filtered = searchResults.filter { matchesFilter(it) }
        adapter.submitList(filtered)
        binding.emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.resultCountText.text = if (filtered.size == 1) "1 sign" else "${filtered.size} signs"
    }

    /** Matches the seeded category strings, e.g. "FSL - Numbers", "ASL - Basic Responses". */
    private fun matchesFilter(entry: DictionaryEntry): Boolean {
        val category = entry.category
        return when (binding.filterChips.checkedChipId) {
            R.id.chipFsl -> category.startsWith("FSL")
            R.id.chipAsl -> category.startsWith("ASL")
            R.id.chipNumbers -> category.contains("Numbers")
            R.id.chipColors -> category.contains("Colors")
            R.id.chipGreetings -> category.contains("Greetings")
            R.id.chipResponses -> category.contains("Responses")
            R.id.chipPeople -> category.contains("Family") || category.contains("People")
            else -> true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val GRID_COLUMNS = 2
    }
}
