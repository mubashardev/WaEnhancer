package com.waenhancer

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class SplashFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Handler(Looper.getMainLooper()).postDelayed({
            if (isAdded) {
                val sharedPref = requireActivity().getSharedPreferences("WaEnhancerX_Prefs", Context.MODE_PRIVATE)
                val isFirstRun = sharedPref.getBoolean("is_first_run", true)

                if (isFirstRun) {
                    findNavController().navigate(R.id.action_splash_to_welcome)
                } else {
                    findNavController().navigate(R.id.action_splash_to_dashboard)
                }
            }
        }, 1500)
    }
}
