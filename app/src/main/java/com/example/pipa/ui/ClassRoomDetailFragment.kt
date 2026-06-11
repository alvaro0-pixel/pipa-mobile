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
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pipa.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import com.kizitonwose.calendar.view.ViewContainer
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
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

    // Formatador ISO para chaves do mapa (yyyy-MM-dd), igual ao banco
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private var selectedDate: LocalDate = LocalDate.now()
    private var currentMonth: YearMonth = YearMonth.now()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Configuração do CalendarView (Kizitonwose)
    // -------------------------------------------------------------------------

    private fun setupCalendar() {
        // Intervalo: 3 meses para trás, 3 meses para frente
        val startMonth = currentMonth.minusMonths(3)
        val endMonth   = currentMonth.plusMonths(3)

        calendarView.setup(startMonth, endMonth, firstDayOfWeekFromLocale())
        calendarView.scrollToMonth(currentMonth)

        // Binder de cada célula de dia
        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)

            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.tvDay.text = data.date.dayOfMonth.toString()

                // Dias de outros meses ficam translúcidos
                if (data.position != DayPosition.MonthDate) {
                    container.tvDay.alpha = 0.3f
                    container.dot.visibility = View.INVISIBLE
                    container.view.setOnClickListener(null)
                    return
                }

                container.tvDay.alpha = 1f

                // Destaque do dia selecionado
                if (data.date == selectedDate) {
                    container.tvDay.setBackgroundResource(R.drawable.bg_btn)
                } else {
                    container.tvDay.setBackgroundResource(0)
                }

                // Dot de status
                val events = eventsByDate[data.date]
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

                // Clique no dia
                container.view.setOnClickListener {
                    val previousDate = selectedDate
                    selectedDate = data.date
                    // Re-renderiza apenas as duas células afetadas
                    calendarView.notifyDateChanged(previousDate)
                    calendarView.notifyDateChanged(selectedDate)
                    showEventsForDate(selectedDate)
                }
            }
        }

        // Navegação de mês com os botões de seta
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

    /** Atualiza o TextView com o nome do mês e o ano (ex: "Junho 2025") */
    private fun updateMonthYearTitle(yearMonth: YearMonth) {
        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
            .replaceFirstChar { it.uppercase() }
        tvMonthYear.text = "$monthName ${yearMonth.year}"
    }

    // ViewContainer para cada célula do calendário
    inner class DayViewContainer(val view: View) : ViewContainer(view) {
        val tvDay: TextView = view.findViewById(R.id.tv_day_text)
        val dot: View       = view.findViewById(R.id.view_dot)
    }

    // -------------------------------------------------------------------------
    // Carregamento de dados do Firestore
    // -------------------------------------------------------------------------

    private fun loadData() {
        if (classroomId == null) return

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // 1. Papel do usuário logado
                val userSnapshot = db.collection("Users").document(currentUid).get().await()
                currentUserRole = userSnapshot.getString("role") ?: "student"

                // 2. Detalhes da turma
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

                // 3. Agenda e horários
                loadCalendarAndEvents()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao carregar dados: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun loadCalendarAndEvents() {
        eventsByDate.clear()

        // Eventos reais no Firestore
        val eventsSnapshot = db.collection("Events")
            .whereEqualTo("classroom-id", classroomId)
            .get().await()

        for (doc in eventsSnapshot.documents) {
            val dateStr      = doc.getString("date") ?: continue // formato yyyy-MM-dd
            val status       = doc.getString("status") ?: "pending"
            val participants = doc.get("participants") as? List<*> ?: emptyList<Any>()
            val isParticipant = participants.contains(currentUid) || teacherId == currentUid

            val localDate = LocalDate.parse(dateStr, isoFormatter)

            val event = EventItem(
                id            = doc.id,
                title         = if (status == "confirmed") "Confirmado - $teacherName" else "Pendente - $teacherName",
                dateStr       = dateStr,
                status        = status,
                isParticipant = isParticipant,
                classroom_id  = classroomId ?: ""
            )

            eventsByDate.getOrPut(localDate) { mutableListOf() }.add(event)
        }

        // Para alunos: preencher slots disponíveis com base na availability do professor
        if (currentUserRole == "student" && teacherId != null) {
            val teacherSnapshot = db.collection("Users").document(teacherId!!).get().await()
            @Suppress("UNCHECKED_CAST")
            val availability = teacherSnapshot.get("availability") as? Map<String, Any> ?: emptyMap()
            generateAvailableSlots(availability)
        }

        // Re-renderiza o calendário inteiro e atualiza a lista do dia selecionado
        calendarView.notifyCalendarChanged()
        showEventsForDate(selectedDate)
    }

    /**
     * Igual ao set_available_dates() do format.js:
     * para cada dia nos próximos 30 dias, se o dia da semana estiver na availability
     * do professor e ainda não houver evento marcado, adiciona um slot "available".
     */
    private fun generateAvailableSlots(availability: Map<String, Any>) {
        val weekDaysMap = mapOf(
            "sunday"    to 7, // java.time: DayOfWeek.SUNDAY = 7
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
            val dayOfWeek   = futureDate.dayOfWeek.value // 1 (Mon) … 7 (Sun)
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
                    eventsByDate[futureDate] = mutableListOf(availableEvent)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Interação com a lista de eventos
    // -------------------------------------------------------------------------

    private fun showEventsForDate(date: LocalDate) {
        val dayEvents = eventsByDate[date] ?: emptyList()
        eventsAdapter.updateEvents(dayEvents)
    }

    private fun handleEventClick(event: EventItem) {
        val formattedDate = formatToPtBr(event.dateStr)

        when (event.status) {
            "available" -> {
                if (currentUserRole == "student") {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Agendar Atendimento")
                        .setMessage("Deseja marcar um agendamento para o dia $formattedDate com o(a) professor(a) $teacherName?")
                        .setPositiveButton("Agendar") { _, _ -> executeScheduling(event.dateStr) }
                        .setNegativeButton("Cancelar", null)
                        .show()
                } else {
                    Toast.makeText(requireContext(), "Este dia está listado como livre para seus alunos.", Toast.LENGTH_SHORT).show()
                }
            }

            "pending" -> {
                if (currentUserRole == "teacher") {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Confirmar Agendamento")
                        .setMessage("Deseja aprovar ou rejeitar o agendamento de $curricularUnit para o dia $formattedDate?")
                        .setPositiveButton("Confirmar") { _, _ -> executeConfirmation(event.id) }
                        .setNegativeButton("Cancelar Agendamento") { _, _ -> executeCancellation(event.id) }
                        .setNeutralButton("Fechar", null)
                        .show()
                } else {
                    if (event.isParticipant) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Agendamento Pendente")
                            .setMessage("Você solicitou atendimento em $formattedDate. Deseja cancelar a solicitação?")
                            .setPositiveButton("Sim, Cancelar") { _, _ -> executeCancellation(event.id) }
                            .setNegativeButton("Não", null)
                            .show()
                    } else {
                        Toast.makeText(requireContext(), "Horário indisponível (reservado por outro aluno).", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            "confirmed" -> {
                if (event.isParticipant) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Agendamento Confirmado")
                        .setMessage("Você tem um compromisso dia $formattedDate.\nDeseja cancelar o agendamento?")
                        .setPositiveButton("Cancelar Presença") { _, _ -> executeCancellation(event.id) }
                        .setNegativeButton("Voltar", null)
                        .show()
                } else {
                    Toast.makeText(requireContext(), "Horário ocupado.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Operações no Firestore (mesma lógica dos .php)
    // -------------------------------------------------------------------------

    /** Equivalente ao schedule_event.php */
    private fun executeScheduling(dateStr: String) {
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

                teacherId?.let { tId ->
                    db.collection("Users").document(tId)
                        .update("calendar", FieldValue.arrayUnion(eventId)).await()
                }

                Toast.makeText(requireContext(), "Solicitação enviada ao professor!", Toast.LENGTH_SHORT).show()
                loadCalendarAndEvents()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    /** Equivalente ao confirm_event.php */
    private fun executeConfirmation(eventId: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                db.collection("Events").document(eventId).update("status", "confirmed").await()
                Toast.makeText(requireContext(), "Agendamento confirmado!", Toast.LENGTH_SHORT).show()
                loadCalendarAndEvents()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao confirmar: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    /**
     * Equivalente ao cancel_event.php.
     *
     * Lógica PHP preservada:
     *   - Aluno:    remove-se dos participants; se não restar mais ninguém, deleta o evento;
     *               caso contrário apenas atualiza participants. Remove eventId do próprio calendar.
     *   - Professor: deleta o evento diretamente, sem mexer nos participants individualmente.
     *               Remove eventId do próprio calendar.
     */
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
                    // Professor: deleta o evento direto
                    db.collection("Events").document(eventId).delete().await()

                    db.collection("Users").document(currentUid)
                        .update("calendar", FieldValue.arrayRemove(eventId)).await()
                }

                Toast.makeText(requireContext(), "Agendamento cancelado com sucesso.", Toast.LENGTH_SHORT).show()
                loadCalendarAndEvents()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao cancelar: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utilitários
    // -------------------------------------------------------------------------

    private fun formatToPtBr(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr, isoFormatter)
            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (e: Exception) {
            dateStr
        }
    }

    // -------------------------------------------------------------------------
    // Modelos de dados e Adapter
    // -------------------------------------------------------------------------

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