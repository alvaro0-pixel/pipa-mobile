package com.example.pipa.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pipa.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HomeFragment : Fragment() {

    private lateinit var tvWelcome: TextView
    private lateinit var tvNextEventTitle: TextView
    private lateinit var tvNextEventDate: TextView
    private lateinit var tvRecentClassName: TextView
    private lateinit var tvRecentClassTeacher: TextView
    private lateinit var progressBar: ProgressBar

    private val db = FirebaseFirestore.getInstance()
    private var currentUid: String = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    companion object {
        private const val TAG = "PIPA_HOME"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvWelcome            = view.findViewById(R.id.tv_welcome)
        tvNextEventTitle     = view.findViewById(R.id.tv_next_event_title)
        tvNextEventDate      = view.findViewById(R.id.tv_next_event_date)
        tvRecentClassName    = view.findViewById(R.id.tv_recent_class_name)
        tvRecentClassTeacher = view.findViewById(R.id.tv_recent_class_teacher)
        progressBar          = view.findViewById(R.id.progress_bar_home)

        loadHomeDashboardData()
    }

    private fun loadHomeDashboardData() {
        if (currentUid.isEmpty()) return

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val today = LocalDate.now()

                // 1. Carrega dados básicos do Usuário Atual
                val userSnapshot = db.collection("Users").document(currentUid).get().await()
                if (userSnapshot.exists()) {
                    val userName = userSnapshot.getString("name") ?: "Usuário"
                    tvWelcome.text = "Olá, $userName!"

                    val userCalendarIDs = userSnapshot.get("calendar") as? List<*> ?: emptyList<Any>()

                    // 2. Busca o agendamento futuro mais próximo de acontecer
                    var closestEventDate: LocalDate? = null
                    var closestEventTitle = ""
                    var closestEventStatus = ""

                    for (anyId in userCalendarIDs) {
                        val eventId = anyId.toString()
                        val eventDoc = db.collection("Events").document(eventId).get().await()

                        if (eventDoc.exists()) {
                            val dateStr     = eventDoc.getString("date") ?: continue
                            val status      = eventDoc.getString("status") ?: "pending"
                            val classroomId = eventDoc.getString("classroom-id") ?: ""

                            val eventDate = try {
                                LocalDate.parse(dateStr, isoFormatter)
                            } catch (e: Exception) {
                                continue
                            }

                            // Filtra apenas agendamentos a partir de hoje
                            if (!eventDate.isBefore(today)) {
                                if (closestEventDate == null || eventDate.isBefore(closestEventDate)) {
                                    closestEventDate = eventDate
                                    closestEventStatus = if (status == "confirmed") "Confirmado" else "Pendente"

                                    if (classroomId.isNotEmpty()) {
                                        val classDoc = db.collection("Classrooms").document(classroomId).get().await()
                                        closestEventTitle = classDoc.getString("curricular-unit") ?: "Atendimento"
                                    } else {
                                        closestEventTitle = "Atendimento"
                                    }
                                }
                            }
                        }
                    }

                    if (closestEventDate != null) {
                        tvNextEventTitle.text = "$closestEventStatus - $closestEventTitle"
                        tvNextEventDate.text = formatToPtBr(closestEventDate.format(isoFormatter))
                    } else {
                        tvNextEventTitle.text = "Nenhum agendamento futuro"
                        tvNextEventDate.text = "Fique atento às suas turmas"
                    }

                    // 3. Busca a sala vinculada
                    val userRole = userSnapshot.getString("role") ?: "student"

                    if (userRole == "teacher") {
                        val teacherClasses = db.collection("Classrooms")
                            .whereEqualTo("tenured-teacher", currentUid)
                            .limit(1)
                            .get().await()

                        if (!teacherClasses.isEmpty) {
                            val targetClass = teacherClasses.documents[0]
                            tvRecentClassName.text = targetClass.getString("curricular-unit") ?: "Sem nome"
                            tvRecentClassTeacher.text = "Você é o professor regente"
                        }
                    } else {
                        val studentClasses = db.collection("Classrooms")
                            .limit(1)
                            .get().await()

                        if (!studentClasses.isEmpty) {
                            val targetClass = studentClasses.documents[0]
                            tvRecentClassName.text = targetClass.getString("curricular-unit") ?: "Sem nome"

                            val teacherId = targetClass.getString("tenured-teacher") ?: ""
                            if (teacherId.isNotEmpty()) {
                                val teacherDoc = db.collection("Users").document(teacherId).get().await()
                                val teacherName = teacherDoc.getString("name") ?: "Desconhecido"
                                tvRecentClassTeacher.text = "Prof: $teacherName"
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao popular Dashboard do HomeFragment: ${e.message}", e)
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun formatToPtBr(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr, isoFormatter)
            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (e: Exception) {
            dateStr
        }
    }
}