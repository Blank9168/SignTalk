package com.example.signtalk.ui.dictionary

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.signtalk.databinding.ItemDictionaryEntryBinding
import com.example.signtalk.domain.model.DictionaryEntry

class DictionaryAdapter(
    private val onEntryClick: (DictionaryEntry) -> Unit
) : ListAdapter<DictionaryEntry, DictionaryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDictionaryEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDictionaryEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: DictionaryEntry) {
            binding.entryEmoji.text = entry.emoji
            binding.entryName.text = entry.displayName
            binding.entryCategory.text = entry.category
            binding.root.setOnClickListener { onEntryClick(entry) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<DictionaryEntry>() {
        override fun areItemsTheSame(oldItem: DictionaryEntry, newItem: DictionaryEntry) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DictionaryEntry, newItem: DictionaryEntry) = oldItem == newItem
    }
}
