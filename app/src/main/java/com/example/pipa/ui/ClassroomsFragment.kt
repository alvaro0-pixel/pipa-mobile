package com.example.pipa.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pipa.R
import com.example.pipa.util.FirebaseHelper
import com.google.firebase.firestore.FirebaseFirestore

class ClassroomsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ClassroomAdapter
    private val classroomList = mutableListOf<ClassroomItem>()

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
        val currentUser = FirebaseHelper.Companion.getAuth().currentUser ?: return
        progressBar.visibility = View.VISIBLE
        classroomList.clear()

        val db = FirebaseFirestore.getInstance()

        db.collection("Users").document(currentUser.uid).get()
            .addOnSuccessListener { userDoc ->
                // Verifica se o fragmento ainda está anexado à tela antes de continuar
                if (!isAdded) return@addOnSuccessListener

                val classroomIds = userDoc.get("classrooms") as? List<String> ?: emptyList()
                if (classroomIds.isEmpty()) {
                    progressBar.visibility = View.GONE
                    return@addOnSuccessListener
                }

                var loadedCount = 0

                for (id in classroomIds) {
                    db.collection("Classrooms").document(id).get()
                        .addOnSuccessListener { classDoc ->
                            if (!isAdded) return@addOnSuccessListener

                            val name = classDoc.getString("curricular-unit") ?: "Sem nome"
                            val teacherId = classDoc.getString("tenured-teacher")

                            if (teacherId != null) {
                                db.collection("Users").document(teacherId).get()
                                    .addOnSuccessListener { teacherDoc ->
                                        if (!isAdded) return@addOnSuccessListener

                                        val teacherName = teacherDoc.getString("name") ?: ""
                                        val lastName = teacherDoc.getString("lastname") ?: ""
                                        val fullTeacher = "$teacherName $lastName".trim()

                                        atualizarListaUI(ClassroomItem(name, fullTeacher), ++loadedCount, classroomIds.size)
                                    }
                                    .addOnFailureListener {
                                        if (!isAdded) return@addOnFailureListener
                                        atualizarListaUI(ClassroomItem(name, "Professor não encontrado"), ++loadedCount, classroomIds.size)
                                    }
                            } else {
                                atualizarListaUI(ClassroomItem(name, "Sem professor"), ++loadedCount, classroomIds.size)
                            }
                        }
                        .addOnFailureListener {
                            if (!isAdded) return@addOnFailureListener
                            loadedCount++
                            if (loadedCount == classroomIds.size) {
                                activity?.runOnUiThread { progressBar.visibility = View.GONE }
                            }
                        }
                }
            }
            .addOnFailureListener {
                if (isAdded) progressBar.visibility = View.GONE
            }
    }

    // Função centralizada e segura para atualizar a lista na Main Thread
    private fun atualizarListaUI(item: ClassroomItem, currentCount: Int, totalSize: Int) {
        activity?.runOnUiThread {
            if (!isAdded) return@runOnUiThread

            classroomList.add(item)
            adapter.notifyItemInserted(classroomList.size - 1) // Melhor e mais seguro que notifyDataSetChanged()

            if (currentCount == totalSize) {
                progressBar.visibility = View.GONE
            }
        }
    }

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
            // Definição com SAFE CALL (TextView?) para evitar fechar o app caso o ID esteja errado no XML
            private val tvName: TextView? = itemView.findViewById(R.id.tv_class_name)
            private val tvTeacher: TextView? = itemView.findViewById(R.id.tv_class_teacher)

            fun bind(item: ClassroomItem) {
                tvName?.text = item.name
                tvTeacher?.text = "Professor(a): ${item.teacher}"
            }
        }
    }
}