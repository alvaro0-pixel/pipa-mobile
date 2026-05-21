package com.example.pipa.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.example.pipa.R
import com.example.pipa.databinding.FragmentLoginBinding
import com.example.pipa.ui.BaseFragment
import com.example.pipa.util.FirebaseHelper
import com.example.pipa.util.showBottomSheet

class LoginFragment : BaseFragment() {

    private var _binding:  FragmentLoginBinding? = null
    private val binding get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListener()
    }

    private fun initListener(){
        binding.buttonLogin.setOnClickListener {
            validateData()
        }
        binding.btnRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        binding.btnRecover.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_recoverAccountFragment)
        }
    }

    private fun validateData() {
        val email = binding.editextEmail.text.toString().trim()
        val senha = binding.editextSenha.text.toString().trim()

        if (email.isNotBlank()) {
            if (senha.isNotBlank()) {
                binding.progressBar.isVisible = true
                loginUser(email, senha)
            } else {
                showBottomSheet(message = getString(R.string.password_empty))}
        } else {
            showBottomSheet(message = getString(R.string.email_empty))
        }
    }

    private fun loginUser(email: String, password: String) {
        try {
            FirebaseHelper.getAuth().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener {
                    task ->
                    if (task.isSuccessful) {
                        findNavController().navigate(R.id.action_global_homeFragment)
                    } else {
                        binding.progressBar.isVisible = false
                        showBottomSheet(message = getString(FirebaseHelper.validError(task.exception?.message.toString())))
                    }
                }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), e.message.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}