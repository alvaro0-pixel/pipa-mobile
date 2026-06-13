package com.example.pipa.ui

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
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

class CalendarFragment : Fragment() {

    private lateinit var tvMonthYear: TextView
    private lateinit var btnPrevMonth: ImageButton
    private lateinit var btnNextMonth: ImageButton
    private lateinit var calendarView: CalendarView
    private lateinit var rvEvents: RecyclerView
    private lateinit var progressBar: ProgressBar

    private val db = FirebaseFirestore.getInstance()
    private var currentUid: String = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var currentUserRole: String = "student"

    private val eventsByDate = mutableMapOf<LocalDate, MutableList<UserEventItem>>()
    private lateinit var eventsAdapter: UserEventsAdapter

    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private var selectedDate: LocalDate = LocalDate.now()
    private var currentMonth: YearMonth = YearMonth.now()

    companion object {
        private const val TAG = "PIPA_USER_CALENDAR"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvMonthYear   = view.findViewById(R.id.tv_month_year)
        btnPrevMonth  = view.findViewById(R.id.btn_prev_month)
        btnNextMonth  = view.findViewById(R.id.btn_next_month)
        calendarView  = view.findViewById(R.id.calendar_view)
        rvEvents      = view.findViewById(R.id.rv_events)
        progressBar   = view.findViewById(R.id.progress_bar)

        rvEvents.layoutManager = LinearLayoutManager(requireContext())
        eventsAdapter = UserEventsAdapter(emptyList()) { selectedEvent ->
            handleEventClick(selectedEvent)
        }
        rvEvents.adapter = eventsAdapter

        setupCalendar()
        loadUserCalendarData()
    }

    private fun setupCalendar() {
        val startMonth = currentMonth.minusMonths(5)
        val endMonth   = currentMonth.plusMonths(5)

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
                    container.tvDay.setBackgroundResource(0)
                    container.view.setOnClickListener(null)
                    return
                }

                container.tvDay.alpha = 1f
                val events = eventsByDate[data.date]

                if (!events.isNullOrEmpty()) {
                    val backgroundRes = when (events[0].status) {
                        "pending"   -> R.drawable.bg_event_pending
                        "confirmed" -> R.drawable.bg_event_confirmed
                        else        -> R.drawable.bg_event_default
                    }
                    container.view.setBackgroundResource(backgroundRes)
                } else {
                    container.view.setBackgroundResource(0)
                }

                if (data.date == selectedDate) {
                    container.tvDay.setBackgroundResource(R.drawable.bg_btn)
                    container.tvDay.setTextColor(context.getColor(R.color.black))
                } else {
                    container.tvDay.setBackgroundResource(0)
                    if (!events.isNullOrEmpty()) {
                        container.tvDay.setTextColor(context.getColor(R.color.white))
                    } else {
                        container.tvDay.setTextColor(Color.parseColor("#212121"))
                    }
                }

                if (!events.isNullOrEmpty()) {
                    val dotRes = when (events[0].status) {
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

    private fun loadUserCalendarData() {
        if (currentUid.isEmpty()) return

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                eventsByDate.clear()

                val userSnapshot = db.collection("Users").document(currentUid).get().await()
                currentUserRole = userSnapshot.getString("role") ?: "student"
                val userCalendarIDs = userSnapshot.get("calendar") as? List<*> ?: emptyList<Any>()

                for (anyId in userCalendarIDs) {
                    val eventId = anyId.toString()
                    val eventDoc = db.collection("Events").document(eventId).get().await()

                    if (eventDoc.exists()) {
                        val dateStr     = eventDoc.getString("date") ?: continue
                        val status      = eventDoc.getString("status") ?: "pending"
                        val classroomId = eventDoc.getString("classroom-id") ?: ""

                        var displayUnitName = "Atendimento"
                        if (classroomId.isNotEmpty()) {
                            val classDoc = db.collection("Classrooms").document(classroomId).get().await()
                            if (classDoc.exists()) {
                                displayUnitName = classDoc.getString("curricular-unit") ?: "Unidade Curricular"
                            }
                        }

                        val localDate = try {
                            LocalDate.parse(dateStr, isoFormatter)
                        } catch (e: Exception) {
                            continue
                        }

                        val displayStatus = when (status) {
                            "pending"   -> "Pendente"
                            "confirmed" -> "Confirmado"
                            else        -> "Agendado"
                        }

                        val eventItem = UserEventItem(
                            id          = eventId,
                            title       = "$displayStatus - $displayUnitName",
                            dateStr     = dateStr,
                            status      = status,
                            classroomId = classroomId
                        )

                        eventsByDate.getOrPut(localDate) { mutableListOf() }.add(eventItem)
                    }
                }

                calendarView.notifyCalendarChanged()
                showEventsForDate(selectedDate)

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar calendário do usuário: ${e.message}", e)
                Toast.makeText(requireContext(), "Erro ao sincronizar eventos.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Erro fatal: ${e.message}", e)
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun showEventsForDate(date: LocalDate) {
        val dayEvents = eventsByDate[date] ?: emptyList()

        if (dayEvents.isEmpty()) {
            val formattedDate = formatToPtBr(date.format(isoFormatter))
            val emptyNotice = UserEventItem(
                id          = "empty_notice",
                title       = "Não há agendamentos para o dia $formattedDate.",
                dateStr     = date.format(isoFormatter),
                status      = "empty_state",
                classroomId = ""
            )
            eventsAdapter.updateEvents(listOf(emptyNotice))
        } else {
            eventsAdapter.updateEvents(dayEvents)
        }
    }

    private fun handleEventClick(event: UserEventItem) {
        if (event.status == "empty_state") return

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val classDoc = db.collection("Classrooms").document(event.classroomId).get().await()
                val curricularUnit = classDoc.getString("curricular-unit") ?: "Sem nome"
                val teacherId = classDoc.getString("tenured-teacher") ?: ""

                var teacherName = "Professor"
                if (teacherId.isNotEmpty()) {
                    val teacherDoc = db.collection("Users").document(teacherId).get().await()
                    teacherName = teacherDoc.getString("name") ?: "Professor"
                }

                progressBar.visibility = View.GONE

                when (event.status) {
                    "pending" -> {
                        if (currentUserRole == "teacher") {
                            showModalConfirmScheduling(event.id, event.dateStr, curricularUnit)
                        } else {
                            // Aluno pode cancelar seu próprio agendamento pendente
                            showModalConfirmCancelEvent("student", event.id, event.dateStr, curricularUnit, teacherName)
                        }
                    }
                    "confirmed" -> {
                        showModalConfirmCancelEvent(currentUserRole, event.id, event.dateStr, curricularUnit, teacherName)
                    }
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Erro ao processar clique.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showModalConfirmScheduling(eventId: String, dateStr: String, curricularUnit: String) {
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

    private fun showModalConfirmCancelEvent(role: String, eventId: String, dateStr: String, curricularUnit: String, teacherName: String) {
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

    private fun executeConfirmation(eventId: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                db.collection("Events").document(eventId).update("status", "confirmed").await()
                Toast.makeText(requireContext(), "Agendamento confirmado com sucesso.", Toast.LENGTH_SHORT).show()
                loadUserCalendarData()
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

                Toast.makeText(requireContext(), "Agendamento cancelado com sucesso.", Toast.LENGTH_SHORT).show()
                loadUserCalendarData()
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

    data class UserEventItem(
        val id: String,
        val title: String,
        val dateStr: String,
        val status: String,
        val classroomId: String
    )

    inner class UserEventsAdapter(
        private var events: List<UserEventItem>,
        private val onItemClick: (UserEventItem) -> Unit
    ) : RecyclerView.Adapter<UserEventsAdapter.ViewHolder>() {

        fun updateEvents(newEvents: List<UserEventItem>) {
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

            if (event.status == "empty_state") {
                holder.tvDate.text = ""
                holder.itemView.setBackgroundResource(0)
                holder.tvTitle.setTextColor(context.getColor(R.color.white))
                holder.itemView.setOnClickListener(null)
            } else {
                holder.tvDate.text = formatToPtBr(event.dateStr)
                holder.tvTitle.setTextColor(context.getColor(R.color.black))
                holder.tvDate.setTextColor(context.getColor(R.color.color_default_dark))

                when (event.status) {
                    "confirmed" -> holder.itemView.setBackgroundResource(R.drawable.bg_event_confirmed)
                    "pending"   -> holder.itemView.setBackgroundResource(R.drawable.bg_event_pending)
                    else        -> holder.itemView.setBackgroundResource(R.drawable.bg_event_default)
                }
                holder.itemView.setOnClickListener { onItemClick(event) }
            }
        }

        override fun getItemCount() = events.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.event_title)
            val tvDate: TextView  = itemView.findViewById(R.id.event_date)
        }
    }
}