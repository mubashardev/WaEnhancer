package com.waenhancer

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class WelcomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btn_get_started).setOnClickListener {
            val sharedPref = requireActivity().getSharedPreferences("WaEnhancerX_Prefs", Context.MODE_PRIVATE)
            sharedPref.edit().putBoolean("is_first_run", false).apply()

            findNavController().navigate(R.id.action_welcome_to_dashboard)
        }
    }
}
