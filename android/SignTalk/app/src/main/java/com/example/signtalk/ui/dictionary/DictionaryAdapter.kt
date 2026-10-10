package com.example.signtalk.ui.dictionary

import android.view.LayoutInflater
import android.view.View
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
            val (language, group) = splitCategory(entry.category)
            binding.entryEmoji.text = entry.emoji
            binding.entryName.text = entry.displayName
            binding.entryLanguage.text = language.orEmpty()
            binding.entryLanguage.visibility = if (language == null) View.GONE else View.VISIBLE
            binding.entryCategory.text = group
            binding.entryCategory.visibility = if (group.isBlank()) View.GONE else View.VISIBLE
            binding.root.contentDescription = "${entry.displayName}, ${entry.category}"
            binding.root.setOnClickListener { onEntryClick(entry) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<DictionaryEntry>() {
        override fun areItemsTheSame(oldItem: DictionaryEntry, newItem: DictionaryEntry) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DictionaryEntry, newItem: DictionaryEntry) = oldItem == newItem
    }

    companion object {
        /**
         * Seeded categories look like "FSL - Colors" / "ASL - Greetings": split them into a
         * language badge ("FSL") and the group ("Colors"). Anything else (e.g. a custom
         * user category) has no badge and is shown as-is.
         */
        fun splitCategory(category: String): Pair<String?, String> {
            val parts = category.split(" - ", limit = 2)
            return if (parts.size == 2 && parts[0].trim().length <= 4) {
                parts[0].trim() to parts[1].trim()
            } else {
                null to category.trim()
            }
        }
    }
}
