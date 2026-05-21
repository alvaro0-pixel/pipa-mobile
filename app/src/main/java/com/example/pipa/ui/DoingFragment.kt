package com.example.pipa.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pipa.R
import com.example.pipa.data.model.Status
import com.example.pipa.data.model.Task
import com.example.pipa.databinding.FragmentDoingBinding
import com.example.pipa.ui.adapter.TaskAdapter

class DoingFragment : Fragment() {
    private var _binding: FragmentDoingBinding? = null
    private val binding get() = _binding!!
    private lateinit var taskAdapter: TaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDoingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListeners()

        initRecyclerViewTask()
        getTask()
    }

    private fun initListeners() {
        binding.floatingActionButton2.setOnClickListener {
            findNavController().navigate((R.id.action_homeFragment_to_formTaskFragment))
        }
    }

    private fun initRecyclerViewTask() {
        taskAdapter = TaskAdapter(requireContext()) { task, option -> optionSelected(task, option) }

        with(binding.recyclerViewTask){
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            adapter = taskAdapter
        }
    }

    private fun optionSelected(task: Task, option: Int){
        when (option) {
            TaskAdapter.SELECT_REMOVER -> {
                Toast.makeText(requireContext(), "Removendo ${task.description}", Toast.LENGTH_SHORT).show()
            }
            TaskAdapter.SELECT_EDIT -> {
                Toast.makeText(requireContext(), "Editando ${task.description}", Toast.LENGTH_SHORT).show()
            }
            TaskAdapter.SELECT_DETAILS -> {
                Toast.makeText(requireContext(), "Detalhes ${task.description}", Toast.LENGTH_SHORT).show()
            }
            TaskAdapter.SELECT_NEXT -> {
                Toast.makeText(requireContext(), "Próximo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getTask() {
        val taskList = listOf(
            Task("0", "Criar nova tela do app", Status.DOING),
            Task("1", "Validar informações na tela de login", Status.DOING),
            Task("2", "Adicionar nova funcionalidade no app", Status.DOING),
            Task("3", "Salvar token Localmente", Status.DOING),
            Task("4", "Criar funcionalidade de logout no app", Status.DOING)
        )
        taskAdapter.submitList(taskList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}