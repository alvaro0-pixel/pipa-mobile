// PipaIdFragment.kt
package com.example.pipa.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.example.pipa.R
import com.example.pipa.util.FirebaseHelper
import com.google.firebase.firestore.FirebaseFirestore

class PipaIdFragment : Fragment() {

    private lateinit var tvFullName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvRole: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pipa_id, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvFullName = view.findViewById(R.id.tv_full_name)
        tvEmail = view.findViewById(R.id.tv_email)
        tvRole = view.findViewById(R.id.tv_role)

        val currentUser = FirebaseHelper.getAuth().currentUser ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: ""
                val lastName = doc.getString("lastname") ?: ""
                val email = doc.getString("email") ?: currentUser.email ?: ""
                val role = doc.getString("role") ?: ""
                val roleText = when (role) {
                    "studie" -> "Estudante"
                    "teach" -> "Professor(a)"
                    else -> role
                }
                tvFullName.text = "$name $lastName".trim()
                tvEmail.text = email
                tvRole.text = roleText
            }
    }
}