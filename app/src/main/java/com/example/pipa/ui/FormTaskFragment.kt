package com.example.pipa.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.pipa.R
import com.example.pipa.data.model.Status
import com.example.pipa.data.model.Task
import com.example.pipa.databinding.FragmentFormTaskBinding
import com.example.pipa.util.FirebaseHelper
import com.example.pipa.util.initToolbar
import com.example.pipa.util.showBottomSheet
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.database
import kotlin.getValue

class FormTaskFragment : BaseFragment() {
    private var _binding:  FragmentFormTaskBinding? = null
    private val binding get() = _binding!!

    //alocação de memória para variável e instanciar mais tarde

    private lateinit var task: Task

    private var newTask: Boolean = true

    private var status: Status = Status.TODO

    private lateinit var reference: DatabaseReference

    private lateinit var auth: FirebaseAuth

    private val args: FormTaskFragmentArgs by navArgs()

    private val viewModel: TaskViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFormTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar(binding.toolbar)

        reference = Firebase.database.reference
        auth = Firebase.auth

        getArgs()
        initListener()
    }

    private fun getArgs() {
        val let = args.task.let {
            if (it != null) {
                this.task = it
                configTask()
            }
        }
    }

    private fun configTask() {
        newTask = false
        status = task.status
        binding.textToolbar.setText(R.string.text_toolbar_update_form_task_fragment)

        binding.editTextDescricao.setText(task.description)
        setStatus()
    }

    private fun setStatus() {
        val id = when (task.status) {
            Status.TODO -> R.id.rbTodo
            Status.DOING -> R.id.rbDoing
            Status.DONE -> R.id.rbDone
        }
        binding.radioGroup.check(id)
    }

    private fun initListener() {
        binding.buttonSave.setOnClickListener {
            validateData()
        }

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            status = when (checkedId) {
                R.id.rbTodo -> Status.TODO
                R.id.rbDoing -> Status.DOING
                R.id.rbDone -> Status.DONE
                else -> Status.TODO
            }
        }
    }

    private fun validateData() {
        val description = binding.editTextDescricao.text.toString().trim()

        if (description.isNotBlank()) {
            hideKeyboard()
            binding.progressBar.isVisible = true


            if(newTask) task = Task()
            task.description = description
            task.status = status

            saveTask()
        } else {
            showBottomSheet(message = getString(R.string.description_empty_form_task_fragment))
        }
    }

    private fun saveTask() {
        val userId = FirebaseHelper.getIdUser()
        if (userId == null) {
            showBottomSheet(message= getString(R.string.error_generic))
        }
        binding.progressBar.isVisible = true
        FirebaseHelper.getDatabase()
            .child("task")
            .child(userId)
            .child(task.id)
            .setValue(task)
            .addOnCompleteListener{ result ->
                binding.progressBar.isVisible = false

                if (result.isSuccessful) {
                    Toast.makeText(
                        requireContext(),
                        R.string.text_save_success_form_task_fragment,
                        Toast.LENGTH_SHORT
                    ).show()

                    if (newTask) {
                        //Criando nova tarefa
                        findNavController().popBackStack()
                    } else {
                        //Editando tarefa
                        Toast.makeText(
                            requireContext(),
                            R.string.text_update_sucess_form_task_fragment,
                            Toast.LENGTH_SHORT
                        ).show()

                        viewModel.setUpdateTask(task)
                        binding.progressBar.isVisible=false
                    }
                } else {
                    // Mostra erro específico se possível
                    binding.progressBar.isVisible = false
                    val errorMessage = result.exception?.message
                        ?: getString(R.string.error_generic)
                    showBottomSheet(message = getString(R.string.error_generic))
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}