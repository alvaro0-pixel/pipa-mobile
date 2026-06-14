package com.example.pipa.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pipa.R
import com.example.pipa.util.FirebaseHelper
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PipaIdFragment : Fragment() {

    private lateinit var tvFullName: TextView
    private lateinit var tvSemester: TextView
    private lateinit var tvCourse: TextView
    private lateinit var tvRegistration: TextView
    private lateinit var tvStatus: TextView

    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "PipaID"
    }

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

        loadPipaIdData()
    }

    private fun loadPipaIdData() {
        val currentUser = FirebaseHelper.getAuth().currentUser
        if (currentUser == null) {
            Log.e(TAG, "Usuário não autenticado")
            tvFullName.text = "Usuário não autenticado"
            return
        }

        // Extrai o identificador (matrícula) limpando o sufixo "@app.com" do Firebase Auth
        val userEmail = currentUser.email ?: ""
        val extractedRegistration = if (userEmail.contains("@")) {
            userEmail.substringBefore("@")
        } else {
            "—"
        }

        lifecycleScope.launch {
            try {
                val doc = db.collection("Users").document(currentUser.uid).get().await()

                if (!doc.exists()) {
                    Log.e(TAG, "Documento do usuário não existe no Firestore")
                    tvFullName.text = "Dados não encontrados"
                    return@launch
                }

                val name          = doc.getString("name")                ?: ""
                val lastName      = doc.getString("lastname")            ?: ""
                val role          = doc.getString("role")                ?: "student"
                val course        = doc.getString("course")              ?: ""
                val entrySemester = doc.getString("entry-semester")      ?: "—"
                val status        = doc.getString("registration-status") ?: "—"

                // Define o Nome Completo na carteirinha
                tvFullName.text = "$name $lastName".trim()

                // Customização baseada na ROLE (Professor vs Aluno) usando a matrícula extraída do Auth
                if (role == "teacher") {
                    // Define os dados específicos do Professor
                    tvCourse.text = course.ifEmpty { "Corpo Docente" }
                    tvRegistration.text = "Matrícula: $extractedRegistration"

                    // Esconde os campos irrelevantes para o docente para não deixar lacunas em branco
                    tvSemester.visibility = View.GONE
                    tvStatus.visibility = View.GONE
                } else {
                    // Comportamento padrão para os Alunos (Mantém tudo visível)
                    tvCourse.text = course.ifEmpty { "Estudante" }
                    tvSemester.text = "Ingresso: $entrySemester"
                    tvRegistration.text = "Matrícula: $extractedRegistration"

                    val statusText = when (status) {
                        "active"   -> "Ativa"
                        "inactive" -> "Inativa"
                        else       -> status
                    }
                    tvStatus.text = "Situação: $statusText"

                    // Certifica a visibilidade caso a view tenha sido reciclada
                    tvSemester.visibility = View.VISIBLE
                    tvStatus.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao buscar documento do Pipa ID: ${e.message}", e)
                tvFullName.text = "Erro ao carregar dados"
            }
        }
    }
}