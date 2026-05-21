package com.example.pipa.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.example.pipa.R
import com.example.pipa.databinding.FragmentRegisterBinding
import com.example.pipa.ui.BaseFragment
import com.example.pipa.util.initToolbar
import com.google.firebase.auth.FirebaseAuth


class RegisterFragment : BaseFragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar(binding.toolbar)
        initListener()
    }



    private fun validateData() {
        val email = binding.editextEmail.text.toString().trim()
        val senha = binding.editextSenha.text.toString().trim()
        if (email.isNotBlank()) {
            if (senha.isNotBlank()) {
                hideKeyboard()
                binding.progressBar.isVisible = true
                registerUser(email, senha)
            } else {
                Toast.makeText(requireContext(), R.string.password_empty_register_fragment, Toast. LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), R.string.email_empty_register_fragment, Toast. LENGTH_SHORT).show()
        }
    }

    private fun registerUser(email: String, password: String) {
        try {
            val auth = FirebaseAuth.getInstance()

            auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener{ task ->
                if (task.isSuccessful){
                    //Encaminha para tela de home
                    findNavController().navigate(R.id.action_global_homeFragment)
                } else {
                    binding.progressBar.isVisible = false
                    Toast.makeText(requireContext(), task.exception?.message, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), e.message.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    private fun initListener() {
        binding.buttonRegister.setOnClickListener {
            validateData()
            initToolbar(binding.toolbar)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}