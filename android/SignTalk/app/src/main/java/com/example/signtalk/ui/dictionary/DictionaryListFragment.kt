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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.signtalk.R
import com.example.signtalk.SignTalkApp
import com.example.signtalk.databinding.FragmentDictionaryListBinding
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
        binding.entriesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.entriesRecyclerView.adapter = adapter

        binding.searchInput.doAfterTextChanged { text ->
            viewModel.onQueryChange(text?.toString().orEmpty())
        }

        binding.addEntryFab.setOnClickListener {
            AddDictionaryEntryDialogFragment().show(childFragmentManager, "add_dictionary_entry")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.entries.collect { entries ->
                    adapter.submitList(entries)
                    binding.emptyText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
