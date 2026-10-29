package com.waenhancer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.preference.PreferenceFragmentCompat
import androidx.navigation.fragment.findNavController

abstract class BasePrefFragment : PreferenceFragmentCompat() {

    abstract val titleResId: Int
    abstract val preferencesXmlResId: Int

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_preference_base, container, false)
        val listContainer = view.findViewById<ViewGroup>(android.R.id.list_container)
        
        val preferenceView = super.onCreateView(inflater, listContainer, savedInstanceState)
        listContainer.addView(preferenceView)

        view.findViewById<TextView>(R.id.tv_title).text = getString(titleResId)

        view.findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            findNavController().navigateUp()
        }

        return view
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(preferencesXmlResId, rootKey)
    }
}
