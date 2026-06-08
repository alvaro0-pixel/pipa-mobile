package com.example.pipa.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pipa.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class ClassroomDetailFragment : Fragment() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvClassName: TextView
    private lateinit var tvTeacherName: TextView
    private lateinit var calendarView: CalendarView
    private lateinit var rvEvents: RecyclerView
    private lateinit var progressBar: ProgressBar
    private val db = FirebaseFirestore.getInstance()
    private var classroomId: String? = null

    private val eventsByDate = mutableMapOf<String, MutableList<EventItem>>()
    private lateinit var eventsAdapter: EventsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_class_room_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnBack = view.findViewById(R.id.btn_back)
        tvClassName = view.findViewById(R.id.tv_classroom_name)
        tvTeacherName = view.findViewById(R.id.tv_teacher_name)
        calendarView = view.findViewById(R.id.calendar_view)
        rvEvents = view.findViewById(R.id.rv_events)
        progressBar = view.findViewById(R.id.progress_bar)

        btnBack.setOnClickListener { requireActivity().onBackPressed() }

        classroomId = arguments?.getString("classroomId")
        if (classroomId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "ID da sala inválido", Toast.LENGTH_SHORT).show()
            requireActivity().onBackPressed()
            return
        }

        rvEvents.layoutManager = LinearLayoutManager(requireContext())
        eventsAdapter = EventsAdapter(emptyList())
        rvEvents.adapter = eventsAdapter

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
            }
            showEventsForDate(selectedDate)
        }

        loadClassroomDetails()
    }

    private fun loadClassroomDetails() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                val classroomDoc = db.collection("Classrooms").document(classroomId!!).get().await()
                if (!classroomDoc.exists()) {
                    showErrorAndFinish("Sala não encontrada")
                    return@launch
                }

                tvClassName.text = classroomDoc.getString("curricular-unit") ?: "Sem nome"

                val teacherId = when (val ref = classroomDoc.get("tenured-teacher")) {
                    is String -> ref
                    is com.google.firebase.firestore.DocumentReference -> ref.id
                    else -> null
                }
                tvTeacherName.text = if (teacherId != null) {
                    try {
                        val teacherDoc = db.collection("Users").document(teacherId).get().await()
                        val name = teacherDoc.getString("name") ?: ""
                        val lastName = teacherDoc.getString("lastname") ?: ""
                        "Professor(a): ${name} $lastName".trim()
                    } catch (e: Exception) {
                        "Professor não encontrado"
                    }
                } else {
                    "Professor não informado"
                }

                loadEvents()
            } catch (e: Exception) {
                showErrorAndFinish("Erro ao carregar dados: ${e.message}")
            }
        }
    }

    private suspend fun loadEvents() {
        try {
            val eventsSnapshot = db.collection("Events")
                .whereEqualTo("classroomId", classroomId)
                .get()
                .await()

            eventsByDate.clear()
            for (doc in eventsSnapshot.documents) {
                val title = doc.getString("title") ?: continue
                val timestamp = doc.getTimestamp("date")
                val date = timestamp?.toDate() ?: continue
                val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
                eventsByDate.getOrPut(dateKey) { mutableListOf() }.add(EventItem(title, date))
            }

            if (isAdded) {
                progressBar.visibility = View.GONE
                val today = Calendar.getInstance()
                showEventsForDate(today)
            }
        } catch (e: Exception) {
            if (isAdded) {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Erro ao carregar eventos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEventsForDate(date: Calendar) {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date.time)
        val events = eventsByDate[dateKey] ?: emptyList()
        eventsAdapter.updateEvents(events)
    }

    private fun showErrorAndFinish(message: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            requireActivity().onBackPressed()
        }
    }

    data class EventItem(val title: String, val date: Date)

    inner class EventsAdapter(private var events: List<EventItem>) :
        RecyclerView.Adapter<EventsAdapter.ViewHolder>() {
        private val hourFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        fun updateEvents(newEvents: List<EventItem>) {
            events = newEvents
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_event, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val event = events[position]
            holder.tvTitle.text = event.title
            holder.tvDate.text = "${dateFormat.format(event.date)} às ${hourFormat.format(event.date)}"
        }

        override fun getItemCount() = events.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.event_title)
            val tvDate: TextView = itemView.findViewById(R.id.event_date)
        }
    }
}