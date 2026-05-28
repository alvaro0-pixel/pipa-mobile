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

        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { userDoc ->
                val classroomIds = userDoc.get("classrooms") as? List<String> ?: emptyList()
                if (classroomIds.isEmpty()) {
                    progressBar.visibility = View.GONE
                    return@addOnSuccessListener
                }
                // Busca cada curricular-unit
                for (id in classroomIds) {
                    db.collection("curricular-unit").document(id).get()
                        .addOnSuccessListener { classDoc ->
                            val name = classDoc.getString("curricular-unit") ?: "Sem nome"
                            val teacherId = classDoc.getString("tenured-teacher")
                            if (teacherId != null) {
                                // Busca nome do professor
                                db.collection("users").document(teacherId).get()
                                    .addOnSuccessListener { teacherDoc ->
                                        val teacherName = teacherDoc.getString("name") ?: ""
                                        val lastName = teacherDoc.getString("lastname") ?: ""
                                        val fullTeacher = "$teacherName $lastName".trim()
                                        classroomList.add(ClassroomItem(name, fullTeacher))
                                        adapter.notifyDataSetChanged()
                                    }
                                    .addOnFailureListener {
                                        classroomList.add(ClassroomItem(name, "Professor não encontrado"))
                                        adapter.notifyDataSetChanged()
                                    }
                            } else {
                                classroomList.add(ClassroomItem(name, "Sem professor"))
                                adapter.notifyDataSetChanged()
                            }
                            progressBar.visibility = View.GONE
                        }
                        .addOnFailureListener {
                            progressBar.visibility = View.GONE
                        }
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
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
            private val tvName = itemView.findViewById<TextView>(R.id.tv_class_name)
            private val tvTeacher = itemView.findViewById<TextView>(R.id.tv_class_teacher)
            fun bind(item: ClassroomItem) {
                tvName.text = item.name
                tvTeacher.text = "Professor(a): ${item.teacher}"
            }
        }
    }
}