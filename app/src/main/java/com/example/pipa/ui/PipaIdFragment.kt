package com.example.pipa.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.pipa.R
import com.example.pipa.util.FirebaseHelper
import com.google.firebase.firestore.FirebaseFirestore

class PipaIdFragment : Fragment() {

    private lateinit var tvFullName: TextView
    private lateinit var tvSemester: TextView
    private lateinit var tvCourse: TextView
    private lateinit var tvRegistration: TextView
    private lateinit var tvStatus: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pipa_id, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvFullName     = view.findViewById(R.id.tv_full_name)
        tvSemester     = view.findViewById(R.id.tv_semester)
        tvCourse       = view.findViewById(R.id.tv_course)
        tvRegistration = view.findViewById(R.id.tv_registration)
        tvStatus       = view.findViewById(R.id.tv_status)

        val currentUser = FirebaseHelper.getAuth().currentUser
        if (currentUser == null) {
            Log.e("PipaID", "Usuário não autenticado")
            tvFullName.text = "Usuário não autenticado"
            return
        }

        Log.d("PipaID", "UID do usuário logado: ${currentUser.uid}")

        FirebaseFirestore.getInstance()
            .collection("Users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { doc ->
                Log.d("PipaID", "Documento existe: ${doc.exists()}")
                Log.d("PipaID", "Dados brutos: ${doc.data}")

                if (!doc.exists()) {
                    Log.w("PipaID", "Documento não encontrado para uid: ${currentUser.uid}")
                    tvFullName.text = "Dados não encontrados"
                    return@addOnSuccessListener
                }

                val name          = doc.getString("name")                ?: ""
                val lastName      = doc.getString("lastname")            ?: ""
                val course        = doc.getString("course")              ?: "—"
                val entrySemester = doc.getString("entry-semester")      ?: "—"
                val registration  = doc.getString("registration")        ?: "—"
                val status        = doc.getString("registration-status") ?: "—"

                Log.d("PipaID", "name=$name, lastname=$lastName, course=$course")
                Log.d("PipaID", "entry-semester=$entrySemester, registration=$registration, status=$status")

                val statusText = when (status) {
                    "active"   -> "ativa"
                    "inactive" -> "inativa"
                    else       -> status
                }

                tvFullName.text     = "$name $lastName".trim()
                tvSemester.text     = "Ingresso: $entrySemester"
                tvCourse.text       = course
                tvRegistration.text = "Matrícula: $registration"
                tvStatus.text       = "Matrícula: $statusText"
            }
            .addOnFailureListener { e ->
                Log.e("PipaID", "Erro ao buscar documento: ${e.message}", e)
                tvFullName.text = "Erro ao carregar dados"
            }
    }
}