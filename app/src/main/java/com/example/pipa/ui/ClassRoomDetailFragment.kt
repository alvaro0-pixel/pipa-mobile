package com.example.pipa.ui

import android.app.AlertDialog
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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
    private var teacherId: String? = null
    private var teacherName: String = "Desconhecido"
    private var curricularUnit: String = "Sem nome"

    // Variáveis do Usuário Atual Logado
    private var currentUid: String = FirebaseAuth.getInstance().currentUser?.uid ?: "placeholder-uid"
    private var currentUserRole: String = "student" // Altere dinamicamente para "teacher" de acordo com o login

    private val eventsByDate = mutableMapOf<String, MutableList<EventItem>>()
    private lateinit var eventsAdapter: EventsAdapter
    private var selectedDateCalendar: Calendar = Calendar.getInstance()

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
        // Caso queira passar a role dinamicamente por argumentos:
        currentUserRole = arguments?.getString("userRole") ?: "student"

        if (classroomId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "ID da sala inválido", Toast.LENGTH_SHORT).show()
            requireActivity().onBackPressed()
            return
        }

        rvEvents.layoutManager = LinearLayoutManager(requireContext())
        eventsAdapter = EventsAdapter(emptyList()) { eventItem ->
            handleEventClick(eventItem)
        }
        rvEvents.adapter = eventsAdapter

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDateCalendar.set(year, month, dayOfMonth)

            // 1. Atualiza a lista inferior normalmente
            showEventsForDate(selectedDateCalendar)

            // 2. Busca se existe alguma disponibilidade ou evento mapeado para este dia
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDateCalendar.time)
            val eventsOnDay = eventsByDate[dateKey]

            if (!eventsOnDay.isNullOrEmpty()) {
                // Se houver eventos ou um slot "Disponível", pega o primeiro (idêntico à lógica JS: d_events[0])
                handleEventClick(eventsOnDay[0])
            } else {
                // Caso seja um dia sem nenhuma definição de horário ou evento
                Toast.makeText(requireContext(), "Não há horários definidos para este dia.", Toast.LENGTH_SHORT).show()
            }
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

                curricularUnit = classroomDoc.getString("curricular-unit") ?: "Sem nome"
                tvClassName.text = curricularUnit

                teacherId = when (val ref = classroomDoc.get("tenured-teacher")) {
                    is String -> ref
                    is com.google.firebase.firestore.DocumentReference -> ref.id
                    else -> null
                }

                var availabilityMap: Map<String, Any>? = null

                if (teacherId != null) {
                    try {
                        val teacherDoc = db.collection("Users").document(teacherId!!).get().await()
                        val name = teacherDoc.getString("name") ?: ""
                        val lastName = teacherDoc.getString("lastname") ?: ""
                        teacherName = "$name $lastName".trim()
                        tvTeacherName.text = "Professor(a): $teacherName"

                        availabilityMap = teacherDoc.get("availability") as? Map<String, Any>
                    } catch (e: Exception) {
                        tvTeacherName.text = "Professor não encontrado"
                    }
                } else {
                    tvTeacherName.text = "Professor não informado"
                }

                loadEventsAndAvailability(availabilityMap)

            } catch (e: Exception) {
                showErrorAndFinish("Erro ao carregar dados: ${e.message}")
            }
        }
    }

    private suspend fun loadEventsAndAvailability(availability: Map<String, Any>?) {
        try {
            val eventsSnapshot = db.collection("Events")
                .whereEqualTo("classroom-id", classroomId)
                .get()
                .await()

            eventsByDate.clear()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            for (doc in eventsSnapshot.documents) {
                val status = doc.getString("status") ?: "pending"
                val dateStr = doc.getString("date") ?: continue
                val participants = doc.get("participants") as? List<String> ?: emptyList()

                val displayTitle = when (status) {
                    "confirmed" -> "Confirmado - $teacherName"
                    "pending" -> "Pendente - $teacherName"
                    else -> "Desconhecido - $teacherName"
                }

                eventsByDate.getOrPut(dateStr) { mutableListOf() }.add(
                    EventItem(
                        eventId = doc.id,
                        title = displayTitle,
                        dateStr = dateStr,
                        status = status,
                        participants = participants
                    )
                )
            }

            if (availability != null) {
                val weekDaysMap = mapOf(
                    "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY,
                    "tuesday" to Calendar.TUESDAY, "wednesday" to Calendar.WEDNESDAY,
                    "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY,
                    "saturday" to Calendar.SATURDAY
                )

                val today = Calendar.getInstance()
                for (i in 0 until 30) {
                    val currentDate = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
                    val currentDayOfWeek = currentDate.get(Calendar.DAY_OF_WEEK)

                    for ((dayName, isAvailable) in availability) {
                        if (isAvailable == true || isAvailable.toString().toBoolean()) {
                            val targetDayNum = weekDaysMap[dayName.lowercase(Locale.ROOT)]
                            if (targetDayNum == currentDayOfWeek) {
                                val dateKey = dateFormat.format(currentDate.time)

                                if (!eventsByDate.containsKey(dateKey)) {
                                    eventsByDate.getOrPut(dateKey) { mutableListOf() }.add(
                                        EventItem(
                                            eventId = null,
                                            title = "Disponível",
                                            dateStr = dateKey,
                                            status = "available",
                                            participants = emptyList()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isAdded) {
                progressBar.visibility = View.GONE
                showEventsForDate(selectedDateCalendar)
            }
        } catch (e: Exception) {
            if (isAdded) {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Erro ao processar agenda: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEventsForDate(date: Calendar) {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date.time)
        val events = eventsByDate[dateKey] ?: emptyList()
        eventsAdapter.updateEvents(events)
    }

    // Gerenciador de Cliques baseado em Perfis (Equivalente ao student_featured_date e teacher_featured_date)
    private fun handleEventClick(event: EventItem) {
        val formatBr = formatToPtBr(event.dateStr)

        when (event.status) {
            "available" -> {
                if (currentUserRole == "student") {
                    // Lógica do Modal student_featured_date -> Pergunta se deseja Agendar
                    AlertDialog.Builder(requireContext())
                        .setTitle("Confirmar Agendamento")
                        .setMessage("Tem certeza que deseja agendar um atendimento com o(a) professor(a) $teacherName no dia $formatBr?")
                        .setPositiveButton("Sim, Agendar") { _, _ -> scheduleEvent(event.dateStr) }
                        .setNegativeButton("Não", null)
                        .show()
                } else {
                    // Professor clicando em dia disponível apenas visualiza
                    Toast.makeText(requireContext(), "Esta data está disponível para agendamento dos alunos.", Toast.LENGTH_SHORT).show()
                }
            }
            "pending" -> {
                if (currentUserRole == "teacher") {
                    // Lógica do teacher_featured_date para eventos pendentes
                    AlertDialog.Builder(requireContext())
                        .setTitle("Agendamento Pendente")
                        .setMessage("Deseja confirmar ou cancelar o agendamento da disciplina de $curricularUnit no dia $formatBr?")
                        .setPositiveButton("Confirmar") { _, _ -> confirmEvent(event.eventId!!) }
                        .setNegativeButton("Cancelar Agendamento") { _, _ -> cancelEvent(event) }
                        .setNeutralButton("Fechar", null)
                        .show()
                } else {
                    // Aluno visualiza ou cancela se ele for o participante do agendamento pendente
                    if (event.participants.contains(currentUid)) {
                        showCancelDialogForStudent(event, formatBr)
                    } else {
                        Toast.makeText(requireContext(), "Há um agendamento pendente de outro aluno nesta data.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            "confirmed" -> {
                // Evento confirmado permite cancelamento por ambas as partes
                if (currentUserRole == "teacher") {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Agendamento Confirmado")
                        .setMessage("Você tem um agendamento confirmado para o dia $formatBr.\nDeseja cancelar este agendamento?")
                        .setPositiveButton("Sim, Cancelar") { _, _ -> cancelEvent(event) }
                        .setNegativeButton("Não", null)
                        .show()
                } else {
                    if (event.participants.contains(currentUid)) {
                        showCancelDialogForStudent(event, formatBr)
                    } else {
                        Toast.makeText(requireContext(), "Data reservada para outro aluno.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showCancelDialogForStudent(event: EventItem, dateBr: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Cancelamento")
            .setMessage("Tem certeza que deseja cancelar o agendamento com o(a) professor(a) $teacherName da disciplina de $curricularUnit no dia $dateBr?")
            .setPositiveButton("Sim, Cancelar") { _, _ -> cancelEvent(event) }
            .setNegativeButton("Não", null)
            .show()
    }

    // ================= SOLICITAR AGENDAMENTO (schedule_event.php) =================
    private fun scheduleEvent(dateStr: String) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                // 1. Prepara e salva o novo documento na coleção "Events"
                val eventData = hashMapOf(
                    "classroom-id" to classroomId,
                    "date" to dateStr,
                    "description" to "none",
                    "participants" to listOf(currentUid),
                    "status" to "pending"
                )
                val eventDocRef = db.collection("Events").add(eventData).await()
                val newEventId = eventDocRef.id

                // 2. Insere o ID do evento no array "calendar" do estudante atual
                db.collection("Users").document(currentUid)
                    .update("calendar", FieldValue.arrayUnion(newEventId)).await()

                // 3. Insere o ID do evento no array "calendar" do professor titular
                if (!teacherId.isNullOrEmpty()) {
                    db.collection("Users").document(teacherId!!)
                        .update("calendar", FieldValue.arrayUnion(newEventId)).await()
                }

                Toast.makeText(requireContext(), "Atendimento solicitado! Aguarde confirmação do professor.", Toast.LENGTH_LONG).show()
                loadClassroomDetails() // Recarrega os dados dinamicamente

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Erro ao agendar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ================= CONFIRMAR AGENDAMENTO (confirm_event.php) =================
    private fun confirmEvent(eventId: String) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                db.collection("Events").document(eventId)
                    .update("status", "confirmed").await()

                Toast.makeText(requireContext(), "Agendamento confirmado com sucesso!", Toast.LENGTH_SHORT).show()
                loadClassroomDetails()

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Erro ao confirmar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ================= CANCELAR AGENDAMENTO (cancel_event.php) =================
    private fun cancelEvent(event: EventItem) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                val eventDocRef = db.collection("Events").document(event.eventId!!)

                if (currentUserRole == "student") {
                    // Lógica do Aluno: se remove do array de participantes e limpa seu calendário
                    db.collection("Users").document(currentUid)
                        .update("calendar", FieldValue.arrayRemove(event.eventId)).await()

                    val updatedParticipants = event.participants.toMutableList().apply { remove(currentUid) }

                    if (updatedParticipants.isEmpty()) {
                        // Se não sobrarem participantes, deleta o evento conforme cancel_event.php
                        eventDocRef.delete().await()
                    } else {
                        eventDocRef.update("participants", updatedParticipants).await()
                    }
                } else {
                    // Lógica do Professor: Deleta o evento e varre limpando o calendário dos envolvidos
                    // Remove do próprio professor
                    db.collection("Users").document(currentUid)
                        .update("calendar", FieldValue.arrayRemove(event.eventId)).await()

                    // Remove de todos os alunos participantes do evento
                    for (participantUid in event.participants) {
                        db.collection("Users").document(participantUid)
                            .update("calendar", FieldValue.arrayRemove(event.eventId)).await()
                    }

                    // Por fim, apaga o documento de evento por completo
                    eventDocRef.delete().await()
                }

                Toast.makeText(requireContext(), "Agendamento cancelado com sucesso.", Toast.LENGTH_SHORT).show()
                loadClassroomDetails()

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Erro ao cancelar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatToPtBr(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            parser.parse(dateStr)?.let { formatter.format(it) } ?: dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun showErrorAndFinish(message: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            requireActivity().onBackPressed()
        }
    }

    // Estrutura de dados adaptada com suporte a IDs de controle do Firestore
    data class EventItem(
        val eventId: String?,
        val title: String,
        val dateStr: String,
        val status: String,
        val participants: List<String>
    )

    inner class EventsAdapter(
        private var events: List<EventItem>,
        private val onItemClick: (EventItem) -> Unit
    ) : RecyclerView.Adapter<EventsAdapter.ViewHolder>() {

        fun updateEvents(newEvents: List<EventItem>) {
            events = newEvents
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val event = events[position]
            holder.tvTitle.text = event.title

            if (event.status == "available") {
                holder.tvDate.text = "Livre para agendamento"
                holder.itemView.setBackgroundResource(R.drawable.bg_event_available)
                holder.tvTitle.setTextColor(holder.itemView.context.getColor(R.color.white))
                holder.tvDate.setTextColor(holder.itemView.context.getColor(R.color.white))
            } else {
                holder.tvDate.text = formatToPtBr(event.dateStr)
                holder.tvTitle.setTextColor(holder.itemView.context.getColor(R.color.black))
                holder.tvDate.setTextColor(holder.itemView.context.getColor(R.color.color_default_dark))

                when (event.status) {
                    "confirmed" -> holder.itemView.setBackgroundResource(R.drawable.bg_event_confirmed)
                    "pending" -> holder.itemView.setBackgroundResource(R.drawable.bg_event_pending)
                    else -> holder.itemView.setBackgroundResource(R.drawable.bg_event_default)
                }
            }

            holder.itemView.setOnClickListener { onItemClick(event) }
        }

        override fun getItemCount() = events.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.event_title)
            val tvDate: TextView = itemView.findViewById(R.id.event_date)
        }
    }
}