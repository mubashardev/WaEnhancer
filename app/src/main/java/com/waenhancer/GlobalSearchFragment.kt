package com.waenhancer

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class GlobalSearchFragment : Fragment() {

    data class SearchItem(val title: String, val description: String, val actionId: Int)

    private val allItems = listOf(
        SearchItem("Freeze Last Seen", "Prevent others from seeing your online presence updates", R.id.action_search_to_privacyPref),
        SearchItem("Hide Read Receipts (Blue ticks)", "Read messages without sending blue tick confirmations", R.id.action_search_to_privacyPref),
        SearchItem("Anti-Delete Messages", "Keep copy of messages that other users revoke", R.id.action_search_to_privacyPref),
        SearchItem("High Quality Images", "Upload and send pictures in high resolution", R.id.action_search_to_mediaPref),
        SearchItem("Video Size Limits", "Override standard size caps for sending videos", R.id.action_search_to_mediaPref),
        SearchItem("Unlimited Pin Chats", "Pin more than the default 3 chats in WhatsApp", R.id.action_search_to_chatsPref),
        SearchItem("Custom Chats Tags", "Organize chats with custom categorizations", R.id.action_search_to_chatsPref),
        SearchItem("Save Status Stories", "Download status photo and video updates", R.id.action_search_to_statusPref),
        SearchItem("Hide Status Views", "Watch status updates without letting them know", R.id.action_search_to_statusPref),
        SearchItem("Custom App Themes", "Apply alternative skins and background structures", R.id.action_search_to_interfacePref),
        SearchItem("System Font Style", "Customize typography family and scale", R.id.action_search_to_interfacePref),
        SearchItem("Clear Cache Cleaner", "Flush temporary files and save storage space", R.id.action_search_to_utilitiesPref),
        SearchItem("Local Preference Backups", "Save your configurations settings safely", R.id.action_search_to_utilitiesPref)
    )

    private val displayedItems = ArrayList<SearchItem>()
    private lateinit var adapter: ArrayAdapter<SearchItem>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_global_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            findNavController().navigateUp()
        }

        val etSearch = view.findViewById<EditText>(R.id.et_search)
        val listView = view.findViewById<ListView>(R.id.search_results)
        val emptyState = view.findViewById<View>(R.id.empty_state)

        displayedItems.addAll(allItems)

        adapter = object : ArrayAdapter<SearchItem>(requireContext(), android.R.layout.simple_list_item_2, android.R.id.text1, displayedItems) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val item = getItem(position)
                if (item != null) {
                    val text1 = v.findViewById<TextView>(android.R.id.text1)
                    val text2 = v.findViewById<TextView>(android.R.id.text2)
                    text1.text = item.title
                    text1.setTextColor(resources.getColor(R.color.text_primary, null))
                    text1.textSize = 15f
                    
                    text2.text = item.description
                    text2.setTextColor(resources.getColor(R.color.text_secondary, null))
                    text2.textSize = 12f
                    
                    v.setPadding(32, 24, 32, 24)
                    v.setBackgroundColor(resources.getColor(R.color.background_color, null))
                }
                return v
            }
        }

        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = displayedItems[position]
            findNavController().navigate(selectedItem.actionId)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterResults(s.toString())
                if (displayedItems.isEmpty()) {
                    listView.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                } else {
                    listView.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterResults(query: String) {
        displayedItems.clear()
        if (query.isEmpty()) {
            displayedItems.addAll(allItems)
        } else {
            val lowerQuery = query.lowercase()
            for (item in allItems) {
                if (item.title.lowercase().contains(lowerQuery) || item.description.lowercase().contains(lowerQuery)) {
                    displayedItems.add(item)
                }
            }
        }
        adapter.notifyDataSetChanged()
    }
}
