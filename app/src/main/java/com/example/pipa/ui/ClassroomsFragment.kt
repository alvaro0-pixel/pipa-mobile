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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pipa.R
import com.example.pipa.util.FirebaseHelper
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ClassroomsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ClassroomAdapter
    private val classroomList = mutableListOf<ClassroomItem>()
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "ClassroomsFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_classrooms, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.rv_classrooms)
        progressBar = view.findViewById(R.id.progress_classrooms)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ClassroomAdapter(classroomList)
        recyclerView.adapter = adapter

        loadClassrooms()
    }

    private fun loadClassrooms() {
        val currentUser = FirebaseHelper.getAuth().currentUser
        if (currentUser == null) {
            Log.e(TAG, "Usuário não autenticado")
            return
        }

        progressBar.visibility = View.VISIBLE
        classroomList.clear()

        // Usar corrotinas para código assíncrono mais limpo
        lifecycleScope.launch {
            try {
                val classroomIds = fetchClassroomIds(currentUser.uid)
                if (classroomIds.isEmpty()) {
                    progressBar.visibility = View.GONE
                    return@launch
                }

                val items = mutableListOf<ClassroomItem>()
                for (classId in classroomIds) {
                    val classroomDoc = db.collection("Classrooms").document(classId).get().await()
                    if (!classroomDoc.exists()) continue

                    val name = classroomDoc.getString("curricular-unit") ?: "Sem nome"
                    val teacherRef = classroomDoc.get("tenured-teacher")
                    var teacherName = "Professor não informado"

                    when (teacherRef) {
                        is String -> {
                            if (teacherRef.isNotEmpty()) {
                                teacherName = getTeacherName(teacherRef)
                            }
                        }
                        is DocumentReference -> {
                            teacherName = getTeacherName(teacherRef.id)
                        }
                    }

                    items.add(ClassroomItem(name, teacherName))
                }

                // Atualiza UI na main thread
                if (isAdded) {
                    classroomList.addAll(items)
                    adapter.notifyDataSetChanged()
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar turmas", e)
                if (isAdded) {
                    progressBar.visibility = View.GONE
                    // Opcional: mostrar Toast
                }
            }
        }
    }

    /**
     * Busca os IDs das turmas do usuário.
     * Primeiro tenta o campo direto "classrooms" no documento do usuário.
     * Se não existir, busca na subcoleção "enrollments".
     */
    private suspend fun fetchClassroomIds(userId: String): List<String> {
        val userDoc = db.collection("Users").document(userId).get().await()

        // 1. Tentar campo "classrooms" (array de strings)
        val classroomsField = userDoc.get("classrooms") as? List<*>
        if (!classroomsField.isNullOrEmpty()) {
            return classroomsField.filterIsInstance<String>()
        }

        // 2. Buscar subcoleção "enrollments" (ou "classrooms")
        val enrollmentsSnapshot = db.collection("Users")
            .document(userId)
            .collection("enrollments")
            .get()
            .await()

        val idsFromSubcollection = mutableListOf<String>()
        for (doc in enrollmentsSnapshot.documents) {
            val classId = doc.getString("classroomId")
            if (!classId.isNullOrEmpty()) {
                idsFromSubcollection.add(classId)
            }
        }

        if (idsFromSubcollection.isNotEmpty()) {
            return idsFromSubcollection
        }

        // 3. Fallback: tentar coleção global "Enrollments" (userId -> classroomId)
        val globalEnrollments = db.collection("Enrollments")
            .whereEqualTo("userId", userId)
            .get()
            .await()

        return globalEnrollments.documents.mapNotNull { it.getString("classroomId") }
    }

    /**
     * Obtém o nome completo do professor a partir do ID do usuário.
     */
    private suspend fun getTeacherName(teacherId: String): String {
        return try {
            val teacherDoc = db.collection("Users").document(teacherId).get().await()
            val name = teacherDoc.getString("name") ?: ""
            val lastName = teacherDoc.getString("lastname") ?: ""
            "$name $lastName".trim().ifEmpty { "Professor não encontrado" }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar professor $teacherId", e)
            "Erro ao carregar professor"
        }
    }

    // ==================== ADAPTER ====================

    data class ClassroomItem(val name: String, val teacher: String)

    inner class ClassroomAdapter(private val items: List<ClassroomItem>) :
        RecyclerView.Adapter<ClassroomAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_classroom, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView? = itemView.findViewById(R.id.tv_class_name)
            private val tvTeacher: TextView? = itemView.findViewById(R.id.tv_class_teacher)

            fun bind(item: ClassroomItem) {
                tvName?.text = item.name
                tvTeacher?.text = "Professor(a): ${item.teacher}"
            }
        }
    }
}