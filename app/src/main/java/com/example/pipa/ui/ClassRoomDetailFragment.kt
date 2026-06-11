package com.example.pipa.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

class ClassroomDetailFragment : Fragment() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvClassName: TextView
    private lateinit var tvTeacherName: TextView
    private lateinit var tvMonthYear: TextView
    private lateinit var btnPrevMonth: ImageButton
    private lateinit var btnNextMonth: ImageButton
    private lateinit var calendarView: CalendarView
    private lateinit var rvEvents: RecyclerView
    private lateinit var progressBar: ProgressBar

    private val db = FirebaseFirestore.getInstance()
    private var classroomId: String? = null
    private var teacherId: String? = null
    private var teacherName: String = "Desconhecido"
    private var curricularUnit: String = "Sem nome"

    private var currentUid: String = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var currentUserRole: String = "student"

    private val eventsByDate = mutableMapOf<LocalDate, MutableList<EventItem>>()
    private lateinit var eventsAdapter: EventsAdapter

    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private var selectedDate: LocalDate = LocalDate.now()
    private var currentMonth: YearMonth = YearMonth.now()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_classroom_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        classroomId = arguments?.getString("id")

        btnBack       = view.findViewById(R.id.btn_back)
        tvClassName   = view.findViewById(R.id.tv_class_name)
        tvTeacherName = view.findViewById(R.id.tv_teacher_name)
        tvMonthYear   = view.findViewById(R.id.tv_month_year)
        btnPrevMonth  = view.findViewById(R.id.btn_prev_month)
        btnNextMonth  = view.findViewById(R.id.btn_next_month)
        calendarView  = view.findViewById(R.id.calendar_view)
        rvEvents      = view.findViewById(R.id.rv_events)
        progressBar   = view.findViewById(R.id.progress_bar)

        btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        rvEvents.layoutManager = LinearLayoutManager(requireContext())
        eventsAdapter = EventsAdapter(emptyList()) { selectedEvent ->
            handleEventClick(selectedEvent)
        }
        rvEvents.adapter = eventsAdapter

        setupCalendar()
        loadData()
    }

    private fun setupCalendar() {
        val startMonth = currentMonth.minusMonths(3)
        val endMonth   = currentMonth.plusMonths(3)

        calendarView.setup(startMonth, endMonth, firstDayOfWeekFromLocale())
        calendarView.scrollToMonth(currentMonth)

        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)

            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.tvDay.text = data.date.dayOfMonth.toString()
                val context = container.view.context

                if (data.position != DayPosition.MonthDate) {
                    container.tvDay.alpha = 0.3f
                    container.dot.visibility = View.INVISIBLE
                    container.view.setBackgroundResource(0)
                    container.view.setOnClickListener(null)
                    return
                }

                container.tvDay.alpha = 1f
                val events = eventsByDate[data.date]

                if (!events.isNullOrEmpty()) {
                    val backgroundRes = when (events[0].status) {
                        "available" -> R.drawable.bg_event_available
                        "pending"   -> R.drawable.bg_event_pending
                        "confirmed" -> R.drawable.bg_event_confirmed
                        else        -> R.drawable.bg_event_default
                    }
                    container.view.setBackgroundResource(backgroundRes)
                    container.tvDay.setTextColor(context.getColor(R.color.white))
                } else {
                    container.view.setBackgroundResource(0)
                    container.tvDay.setTextColor(context.getColor(R.color.white))
                }

                if (data.date == selectedDate) {
                    container.tvDay.setBackgroundResource(R.drawable.bg_btn)
                    container.tvDay.setTextColor(context.getColor(R.color.black))
                } else if (events.isNullOrEmpty()) {
                    container.tvDay.setBackgroundResource(0)
                }

                if (!events.isNullOrEmpty()) {
                    val dotRes = when (events[0].status) {
                        "available" -> R.drawable.bg_dot_green
                        "pending"   -> R.drawable.bg_dot_orange
                        "confirmed" -> R.drawable.bg_dot_blue
                        else        -> R.drawable.bg_dot_default
                    }
                    container.dot.setBackgroundResource(dotRes)
                    container.dot.visibility = View.VISIBLE
                } else {
                    container.dot.visibility = View.INVISIBLE
                }

                container.view.setOnClickListener {
                    val previousDate = selectedDate
                    selectedDate = data.date
                    calendarView.notifyDateChanged(previousDate)
                    calendarView.notifyDateChanged(selectedDate)
                    showEventsForDate(selectedDate)

                    if (events.isNullOrEmpty()) {
                        showModalNonFeaturedDateClick(data.date.format(isoFormatter))
                    }
                }
            }
        }

        calendarView.monthScrollListener = { month ->
            currentMonth = month.yearMonth
            updateMonthYearTitle(currentMonth)
        }

        btnPrevMonth.setOnClickListener {
            calendarView.findFirstVisibleMonth()?.let {
                calendarView.smoothScrollToMonth(it.yearMonth.minusMonths(1))
            }
        }

        btnNextMonth.setOnClickListener {
            calendarView.findFirstVisibleMonth()?.let {
                calendarView.smoothScrollToMonth(it.yearMonth.plusMonths(1))
            }
        }

        updateMonthYearTitle(currentMonth)
    }

    private fun updateMonthYearTitle(yearMonth: YearMonth) {
        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
            .replaceFirstChar { it.uppercase() }
        tvMonthYear.text = "$monthName ${yearMonth.year}"
    }

    inner class DayViewContainer(view: View) : ViewContainer(view) {
        val tvDay: TextView = view.findViewById(R.id.tv_day_text)
        val dot: View       = view.findViewById(R.id.view_dot)
    }

    private fun loadData() {
        if (classroomId == null) return

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val userSnapshot = db.collection("Users").document(currentUid).get().await()
                currentUserRole = userSnapshot.getString("role") ?: "student"

                val classSnapshot = db.collection("Classrooms").document(classroomId!!).get().await()
                if (classSnapshot.exists()) {
                    curricularUnit = classSnapshot.getString("curricular-unit") ?: "Sem nome"
                    teacherId      = classSnapshot.getString("tenured-teacher")
                    tvClassName.text = curricularUnit

                    teacherId?.let { tId ->
                        val teacherSnapshot = db.collection("Users").document(tId).get().await()
                        teacherName = teacherSnapshot.getString("name") ?: "Desconhecido"
                        tvTeacherName.text = "Prof: $teacherName"
                    }
                }

                loadCalendarAndEvents()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao carregar dados: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    // BASEADO DIRETAMENTE EM: get_calendar_data(2).php e format(2).js
    private suspend fun loadCalendarAndEvents() {
        eventsByDate.clear()

        // 1. Obtém a lista oficial de IDs de eventos vinculados ao usuário logado
        val userSnapshot = db.collection("Users").document(currentUid).get().await()
        val userCalendarIDs = userSnapshot.get("calendar") as? List<*> ?: emptyList<Any>()

        // 2. Filtra os eventos que pertencem a esta Turma específica
        val eventsSnapshot = db.collection("Events")
            .whereEqualTo("classroom-id", classroomId)
            .get().await()

        for (doc in eventsSnapshot.documents) {
            val eventId = doc.id

            // Se for visão de Aluno, ele só enxerga eventos cujo ID esteja listado no array "calendar" dele
            if (currentUserRole == "student" && !userCalendarIDs.contains(eventId)) {
                continue
            }

            val dateStr = doc.getString("date") ?: continue
            val status  = doc.getString("status") ?: "pending"

            val participants  = doc.get("participants") as? List<*> ?: emptyList<Any>()
            val isParticipant = participants.contains(currentUid) || teacherId == currentUid

            val localDate = LocalDate.parse(dateStr, isoFormatter)

            // Formatação idêntica ao format_event() do JS
            val displayStatus = when (status) {
                "pending"   -> "Pendente"
                "confirmed" -> "Confirmado"
                else        -> "Desconhecido"
            }
            val eventTitle = "$displayStatus - $teacherName"

            val event = EventItem(
                id            = eventId,
                title         = eventTitle,
                dateStr       = dateStr,
                status        = status, // Mantém a string original ("pending"/"confirmed") para controle lógico
                isParticipant = isParticipant,
                classroom_id  = classroomId ?: ""
            )

            eventsByDate.getOrPut(localDate) { mutableListOf() }.add(event)
        }

        // 3. Se for Aluno, injeta os dias livres com base na disponibilidade do Professor (set_available_dates)
        if (currentUserRole == "student" && teacherId != null) {
            val teacherSnapshot = db.collection("Users").document(teacherId!!).get().await()
            @Suppress("UNCHECKED_CAST")
            val availability = teacherSnapshot.get("availability") as? Map<String, Any> ?: emptyMap()
            generateAvailableSlots(availability)
        }

        calendarView.notifyCalendarChanged()
        showEventsForDate(selectedDate)
    }

    private fun generateAvailableSlots(availability: Map<String, Any>) {
        val weekDaysMap = mapOf(
            "sunday"    to 7, // LocalDate considera Domingo como 7
            "monday"    to 1,
            "tuesday"   to 2,
            "wednesday" to 3,
            "thursday"  to 4,
            "friday"    to 5,
            "saturday"  to 6
        )

        val today = LocalDate.now()
        for (i in 0 until 30) {
            val futureDate  = today.plusDays(i.toLong())
            val dayOfWeek   = futureDate.dayOfWeek.value
            val dateStr     = futureDate.format(isoFormatter)

            for ((dayName, _) in availability) {
                val targetDay = weekDaysMap[dayName.lowercase()] ?: continue

                if (targetDay == dayOfWeek && !eventsByDate.containsKey(futureDate)) {
                    val availableEvent = EventItem(
                        id            = "avail_$dateStr",
                        title         = "Disponível",
                        dateStr       = dateStr,
                        status        = "available",
                        isParticipant = false,
                        classroom_id  = classroomId ?: ""
                    )
                    eventsByDate.getOrPut(futureDate) { mutableListOf() }.add(availableEvent)
                }
            }
        }
    }

    private fun showEventsForDate(date: LocalDate) {
        val dayEvents = eventsByDate[date] ?: emptyList()
        eventsAdapter.updateEvents(dayEvents)
    }

    private fun handleEventClick(event: EventItem) {
        when (event.status) {
            "available" -> {
                if (currentUserRole == "student") {
                    showModalConfirmScheduleEvent(event.dateStr)
                } else {
                    Toast.makeText(requireContext(), "Este dia está listado como livre para seus alunos.", Toast.LENGTH_SHORT).show()
                }
            }
            "pending" -> {
                if (currentUserRole == "teacher") {
                    showModalConfirmScheduling(event.id, event.dateStr)
                } else {
                    if (event.isParticipant) {
                        showModalConfirmCancelEvent("student", event.id, event.dateStr)
                    } else {
                        Toast.makeText(requireContext(), "Horário ocupado por outro aluno.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            "confirmed" -> {
                if (event.isParticipant) {
                    showModalConfirmCancelEvent(currentUserRole, event.id, event.dateStr)
                } else {
                    Toast.makeText(requireContext(), "Horário ocupado.", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                showModalNonFeaturedDateClick(event.dateStr)
            }
        }
    }

    private fun showModalNonFeaturedDateClick(dateStr: String) {
        val formattedDate = formatToPtBr(dateStr)
        AlertDialog.Builder(requireContext())
            .setTitle("Nada nesta data")
            .setMessage("Não há agendamentos para o dia $formattedDate.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showModalConfirmScheduleEvent(dateStr: String) {
        val formattedDate = formatToPtBr(dateStr)

        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Agendamento")
            .setMessage("Tem certeza que deseja agendar um atendimento com o(a) professor(a) $teacherName no dia $formattedDate?")
            .setPositiveButton("Sim, Agendar") { dialog, _ ->
                dialog.dismiss()
                executeScheduling(dateStr)
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun showModalConfirmScheduling(eventId: String, dateStr: String) {
        val formattedDate = formatToPtBr(dateStr)

        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Agendamento")
            .setMessage("Tem certeza que deseja confirmar o agendamento da disciplina de $curricularUnit no dia $formattedDate?")
            .setPositiveButton("Sim, Confirmar") { dialog, _ ->
                dialog.dismiss()
                executeConfirmation(eventId)
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun showModalConfirmCancelEvent(role: String, eventId: String, dateStr: String) {
        val formattedDate = formatToPtBr(dateStr)

        val message = if (role == "student") {
            "Tem certeza que deseja cancelar o agendamento com o(a) professor(a) $teacherName da disciplina de $curricularUnit no dia $formattedDate?"
        } else {
            "Tem certeza que deseja cancelar o agendamento da disciplina de $curricularUnit no dia $formattedDate?"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Cancelamento")
            .setMessage(message)
            .setPositiveButton("Sim, Cancelar") { dialog, _ ->
                dialog.dismiss()
                executeCancellation(eventId)
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun executeScheduling(dateStr: String) {
        if (classroomId == null) return
        progressBar.visibility = View.VISIBLE

        val newEvent = hashMapOf(
            "classroom-id" to classroomId,
            "date"         to dateStr,
            "description"  to "none",
            "participants" to listOf(currentUid),
            "status"       to "pending"
        )

        lifecycleScope.launch {
            try {
                val docRef  = db.collection("Events").add(newEvent).await()
                val eventId = docRef.id

                db.collection("Users").document(currentUid)
                    .update("calendar", FieldValue.arrayUnion(eventId)).await()

                if (!teacherId.isNullOrEmpty()) {
                    db.collection("Users").document(teacherId!!)
                        .update("calendar", FieldValue.arrayUnion(eventId)).await()
                }

                AlertDialog.Builder(requireContext())
                    .setTitle("Feito!")
                    .setMessage("Atendimento feito com sucesso, aguarde a confirmação de seu professor.")
                    .setPositiveButton("OK", null)
                    .show()

                loadCalendarAndEvents()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun executeConfirmation(eventId: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                db.collection("Events").document(eventId).update("status", "confirmed").await()

                AlertDialog.Builder(requireContext())
                    .setTitle("Feito!")
                    .setMessage("Agendamento confirmado com sucesso.")
                    .setPositiveButton("OK", null)
                    .show()

                loadCalendarAndEvents()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao confirmar: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun executeCancellation(eventId: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                if (currentUserRole == "student") {
                    val eventSnapshot = db.collection("Events").document(eventId).get().await()
                    @Suppress("UNCHECKED_CAST")
                    val participants = (eventSnapshot.get("participants") as? MutableList<String>)
                        ?.toMutableList() ?: mutableListOf()

                    participants.remove(currentUid)

                    if (participants.isEmpty()) {
                        db.collection("Events").document(eventId).delete().await()
                    } else {
                        db.collection("Events").document(eventId)
                            .update("participants", participants).await()
                    }

                    db.collection("Users").document(currentUid)
                        .update("calendar", FieldValue.arrayRemove(eventId)).await()
                } else {
                    db.collection("Events").document(eventId).delete().await()
                    db.collection("Users").document(currentUid)
                        .update("calendar", FieldValue.arrayRemove(eventId)).await()
                }

                AlertDialog.Builder(requireContext())
                    .setTitle("Feito!")
                    .setMessage("Agendamento cancelado com sucesso.")
                    .setPositiveButton("OK", null)
                    .show()

                loadCalendarAndEvents()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao cancelar: ${e.message}", Toast.LENGTH_SHORT).show()
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

    data class EventItem(
        val id: String,
        val title: String,
        val dateStr: String,
        val status: String,
        val isParticipant: Boolean,
        val classroom_id: String
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
            val context = holder.itemView.context

            if (event.status == "available") {
                holder.tvDate.text = "Livre para agendamento"
                holder.itemView.setBackgroundResource(R.drawable.bg_event_available)
                holder.tvTitle.setTextColor(context.getColor(R.color.white))
                holder.tvDate.setTextColor(context.getColor(R.color.white))
            } else {
                holder.tvDate.text = formatToPtBr(event.dateStr)
                holder.tvTitle.setTextColor(context.getColor(R.color.black))
                holder.tvDate.setTextColor(context.getColor(R.color.color_default_dark))

                when (event.status) {
                    "confirmed" -> holder.itemView.setBackgroundResource(R.drawable.bg_event_confirmed)
                    "pending"   -> holder.itemView.setBackgroundResource(R.drawable.bg_event_pending)
                    else        -> holder.itemView.setBackgroundResource(R.drawable.bg_event_default)
                }
            }

            holder.itemView.setOnClickListener { onItemClick(event) }
        }

        override fun getItemCount() = events.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.event_title)
            val tvDate: TextView  = itemView.findViewById(R.id.event_date)
        }
    }
}