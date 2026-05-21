package com.example.pipa.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import com.example.pipa.R
import com.example.pipa.databinding.FragmentRecoverAccountBinding
import com.example.pipa.ui.BaseFragment
import com.example.pipa.util.FirebaseHelper
import com.example.pipa.util.initToolbar
import com.example.pipa.util.showBottomSheet
import com.google.firebase.auth.FirebaseAuth


class RecoverAccountFragment : BaseFragment() {
    private var _binding: FragmentRecoverAccountBinding? = null
    private val binding get() = _binding!!

    private lateinit var  auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecoverAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar(binding.toolbar)

        auth = FirebaseAuth.getInstance()

        initListener()
    }

    private fun initListener() {
        binding.buttonEnviar.setOnClickListener{
            validateData()
        }
    }

    private fun validateData() {
        val email = binding.editextEmail.text.toString().trim()

        if (email.isNotBlank()) {
            hideKeyboard()
            binding.progressBar.isVisible=true
            Toast.makeText(requireContext(), "Tudo OK!", Toast.LENGTH_SHORT).show()
        } else {
            showBottomSheet(message = getString(R.string.email_empty))
        }
    }

    private fun recoverAccountUser(email: String) {
        try {
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    binding.progressBar.isVisible = false
                    if (task.isSuccessful) {
                        showBottomSheet(message = getString(R.string.text_message_recover_account_fragment))
                    } else {
                        showBottomSheet(message = getString(FirebaseHelper.validError(task.exception?.message.toString())))
                    }
                }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), e.message.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}