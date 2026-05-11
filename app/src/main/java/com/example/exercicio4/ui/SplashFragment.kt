package com.example.exercicio4.ui

import android.os.Bundle
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.postDelayed
import androidx.navigation.fragment.findNavController
import com.example.exercicio4.R
import com.example.exercicio4.databinding.FragmentSplashBinding
import com.google.firebase.auth.FirebaseAuth
import java.util.logging.Handler

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        android.os.Handler(Looper.getMainLooper()).postDelayed({checkAuth()}, 3000)
    }

    private fun checkAuth(){
        try {
            val currentUser = auth.currentUser

            if (currentUser != null) {
                //home
                findNavController().navigate(R.id.action_splashFragment_to_home)
            } else {
                //login
                findNavController().navigate(R.id.action_splashFragment_to_autentication)
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