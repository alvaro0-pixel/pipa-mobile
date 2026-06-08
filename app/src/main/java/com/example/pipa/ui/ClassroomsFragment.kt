// ClassroomsFragment.kt
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
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pipa.R
import com.example.pipa.util.FirebaseHelper
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
        adapter = ClassroomAdapter(classroomList) { classroomId ->
            val action = MainMenuFragmentDirections.actionHomeFragmentToClassroomDetailFragment(classroomId)
            requireParentFragment().findNavController().navigate(action)
        }
        recyclerView.adapter = adapter

        loadClassrooms()
    }

    private fun loadClassrooms() {
        val currentUser = FirebaseHelper.getAuth().currentUser ?: return
        progressBar.visibility = View.VISIBLE
        classroomList.clear()

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
                    val teacherId = getTeacherId(classroomDoc)
                    val teacherName = if (teacherId != null) getTeacherName(teacherId) else "Professor não informado"

                    items.add(ClassroomItem(classId, name, teacherName))
                }

                if (isAdded) {
                    classroomList.addAll(items)
                    adapter.notifyDataSetChanged()
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar turmas", e)
                if (isAdded) progressBar.visibility = View.GONE
            }
        }
    }

    /**
     * Obtém os IDs das turmas do usuário.
     * Prioridade:
     * 1. Campo direto "classrooms" no documento do usuário.
     * 2. Subcoleção "enrollments" dentro do documento do usuário.
     * 3. Coleção global "Enrollments" filtrando por userId.
     */
    private suspend fun fetchClassroomIds(userId: String): List<String> {
        val userDoc = db.collection("Users").document(userId).get().await()

        // Tentativa 1: campo "classrooms" (array de strings)
        val classroomsField = userDoc.get("classrooms") as? List<*>
        if (!classroomsField.isNullOrEmpty()) {
            return classroomsField.filterIsInstance<String>()
        }

        // Tentativa 2: subcoleção "enrollments" dentro de Users/{userId}
        val enrollmentsSnapshot = db.collection("Users")
            .document(userId)
            .collection("enrollments")
            .get()
            .await()
        val idsFromSub = enrollmentsSnapshot.documents.mapNotNull { it.getString("classroomId") }
        if (idsFromSub.isNotEmpty()) return idsFromSub

        // Tentativa 3: coleção global "Enrollments"
        val globalEnrollments = db.collection("Enrollments")
            .whereEqualTo("userId", userId)
            .get()
            .await()
        return globalEnrollments.documents.mapNotNull { it.getString("classroomId") }
    }

    private fun getTeacherId(classroomDoc: com.google.firebase.firestore.DocumentSnapshot): String? {
        val field = classroomDoc.get("tenured-teacher")
        return when (field) {
            is String -> field
            is com.google.firebase.firestore.DocumentReference -> field.id
            else -> null
        }
    }

    private suspend fun getTeacherName(teacherId: String): String {
        return try {
            val teacherDoc = db.collection("Users").document(teacherId).get().await()
            val name = teacherDoc.getString("name") ?: ""
            val lastName = teacherDoc.getString("lastname") ?: ""
            "$name $lastName".trim().ifEmpty { "Professor não encontrado" }
        } catch (e: Exception) {
            "Erro ao carregar professor"
        }
    }

    // Adapter com click listener
    inner class ClassroomAdapter(
        private val items: List<ClassroomItem>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<ClassroomAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_classroom, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
            holder.itemView.setOnClickListener { onItemClick(items[position].id) }
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

    data class ClassroomItem(val id: String, val name: String, val teacher: String)
}