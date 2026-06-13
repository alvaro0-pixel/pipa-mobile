package com.example.pipa.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.pipa.R
import com.example.pipa.databinding.FragmentMainMenuBinding
import com.example.pipa.util.FirebaseHelper
import com.example.pipa.util.showBottomSheet
import com.google.android.material.navigation.NavigationView

class MainMenuFragment : Fragment() {

    private var _binding: FragmentMainMenuBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configurar toolbar e drawer global
        (activity as AppCompatActivity).setSupportActionBar(binding.toolbar)
        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        val navigationView = binding.navView
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> { logout(); true }
                else -> false
            }
        }

        // Adicionar o fragmento inicial (Home) se o container estiver vazio
        if (childFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        // Configurar a navegação inferior
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.bottom_class -> {
                    replaceFragment(ClassroomsFragment())
                    true
                }
                R.id.bottom_card -> {
                    replaceFragment(PipaIdFragment())
                    true
                }
                R.id.bottom_calendar -> {
                    replaceFragment(CalendarFragment())
                    true
                }
                else -> false
            }
        }

        // Ajuste de insets das barras do sistema
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomAppbar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        childFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun logout() {
        showBottomSheet(
            titleButton = R.string.text_button_dialog_confirma_logout,
            titleDialog = R.string.text_title_dialog_confirma_logout,
            message = getString(R.string.text_message_dialog_confirma_logout),
            onClick = {
                FirebaseHelper.getAuth().signOut()

                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.autentication, true)
                    .build()

                findNavController().navigate(R.id.autentication, null, navOptions)
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}