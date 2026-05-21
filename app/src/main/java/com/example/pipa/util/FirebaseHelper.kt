package com.example.pipa.util

import com.google.firebase.Firebase
import com.example.pipa.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.database

class FirebaseHelper {
    companion object {

        // Serviço de conexão com o banco de dados
        fun getDatabase() = Firebase.database.reference

        // Serviço de autenticação
        fun getAuth() = FirebaseAuth.getInstance()

        // Retorna o id do usuário
        fun getIdUser() = getAuth().currentUser?.uid ?: ""

        // Verifica se o usuário está autenticado => true = autenticado / false = não autenticado
        fun isAuthenticated() = getAuth().currentUser != null

        fun validError(error: String): Int {
            return when {
                error.contains(other = "There is no user record corresponding to this identifier") -> {
                    R.string.account_not_registered_register_fragment
                }

                error.contains(other = "The email address is badly formatted") -> {
                    R.string.invalid_email_register_fragment
                }

                error.contains(other = "The password is invalid or the user does not have a password") -> {
                    R.string.invalid_password_register_fragment
                }

                error.contains(other = "The email address is already in use by another account") -> {
                    R.string.email_in_user_register_fragment
                }

                error.contains(other = "Password should be at least 6 characters") -> {
                    R.string.strong_password_register_fragment
                }

                else -> {
                    R.string.error_generic
                }
            }
        }
    }
}