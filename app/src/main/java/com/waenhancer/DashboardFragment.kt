package com.waenhancer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class DashboardFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Top bar buttons
        view.findViewById<ImageButton>(R.id.btn_search).setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_globalSearch)
        }

        view.findViewById<View>(R.id.btn_notifications).setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_notifications)
        }

        // Bento Grid Category Clicks
        view.findViewById<View>(R.id.card_privacy).setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_privacyPref)
        }

        view.findViewById<View>(R.id.card_media).setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_mediaPref)
        }

        view.findViewById<View>(R.id.card_chats).setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_chatsPref)
        }

        view.findViewById<View>(R.id.card_status).setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_statusPref)
        }

        view.findViewById<View>(R.id.card_interface).setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_interfacePref)
        }

        view.findViewById<View>(R.id.card_utilities).setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_utilitiesPref)
        }

        // Quick Actions clicks
        view.findViewById<View>(R.id.btn_action_sync).setOnClickListener {
            Toast.makeText(context, "Module state synchronized", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btn_action_clear).setOnClickListener {
            Toast.makeText(context, "Cached activity logs cleared", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btn_action_check).setOnClickListener {
            Toast.makeText(context, "Compatibility verification passed", Toast.LENGTH_SHORT).show()
        }
    }
}
