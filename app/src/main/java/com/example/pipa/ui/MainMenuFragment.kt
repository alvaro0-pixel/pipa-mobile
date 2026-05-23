package com.example.pipa.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.pipa.R
import com.example.pipa.databinding.FragmentHomeNewBinding // nome gerado a partir do layout
import com.example.pipa.util.FirebaseHelper
import com.example.pipa.util.showBottomSheet
import com.google.android.material.navigation.NavigationView

class MainMenuFragment : Fragment() {

    private var _binding: FragmentHomeNewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configurar toolbar como action bar
        (activity as AppCompatActivity).setSupportActionBar(binding.toolbar)
        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // Configurar NavigationView do drawer
        val navigationView = binding.drawerLayout.findViewById<NavigationView>(R.id.nav_view)
        // Se você não tiver um NavigationView no test.xml, precisará adicionar. Veja observação abaixo.
        navigationView?.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    logout()
                    true
                }
                else -> false
            }
        }

        // Configurar BottomNavigationView
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_home -> {
                    Toast.makeText(requireContext(), "Início", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.bottom_class -> {
                    Toast.makeText(requireContext(), "Salas", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.bottom_card -> {
                    Toast.makeText(requireContext(), "Pipa ID", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.bottom_calendar -> {
                    Toast.makeText(requireContext(), "Agendamento", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        // Configurar FAB
        binding.fab.setOnClickListener {
            Toast.makeText(requireContext(), "Ação do FAB", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logout() {
        showBottomSheet(
            titleButton = R.string.text_button_dialog_confirma_logout,
            titleDialog = R.string.text_title_dialog_confirma_logout,
            message = getString(R.string.text_message_dialog_confirma_logout),
            onClick = {
                FirebaseHelper.getAuth().signOut()
                findNavController().navigate(R.id.action_homeFragment_to_autentication)
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}