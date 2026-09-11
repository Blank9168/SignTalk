package com.example.signtalk.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.signtalk.R
import com.example.signtalk.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recognitionCard.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_recognition)
        }
        binding.translateCard.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_translate)
        }
        binding.dictionaryCard.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_dictionary)
        }
        binding.settingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_settings)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
