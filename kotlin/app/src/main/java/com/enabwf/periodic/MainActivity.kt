package com.enabwf.periodic

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.os.Bundle
import android.widget.ListView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import java.util.Date
import java.util.Calendar
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import android.view.LayoutInflater
import android.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.Toast


class MainActivity : AppCompatActivity() {

    private val taskViewModel: TaskViewModel by viewModels {
        TaskViewModelFactory(application)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val taskRecyclerView: RecyclerView = findViewById(R.id.task_list)

        // Initialize RecyclerView and Adapter
        val adapter = TaskListAdapter(emptyList())
        taskRecyclerView.adapter = adapter
        taskRecyclerView.layoutManager = LinearLayoutManager(this)  // Set a LayoutManager

        // Observe LiveData and submit list to the adapter
        taskViewModel.allTasks.observe(this, Observer { tasks ->
            tasks?.let { adapter.submitList(it) }
        })

        val addTaskButton: FloatingActionButton = findViewById(R.id.add_task_button)
        addTaskButton.setOnClickListener {
            showAddTaskDialog()
        }

        // Add click listener for RecyclerView items
        taskRecyclerView.addOnItemTouchListener(
            RecyclerItemClickListener(this, taskRecyclerView, object : RecyclerItemClickListener.OnItemClickListener {
                override fun onItemClick(view: View, position: Int) {
                    val task = adapter.getItem(position)
                    markTaskDone(task)
                }

                override fun onLongItemClick(view: View, position: Int) {
                    // Optional long click action
                }
            })
        )
    }
//     private fun markTaskDone(task: Task) {
//         val currentTime = System.currentTimeMillis()
//         val completionTimeDate = Date(currentTime)
//
//         // 1. Log completion
//         val completionRecord = CompletionRecord(taskId = task.id, completionTime = completionTimeDate)
//         taskViewModel.insertCompletionRecord(completionRecord)
//
//         // 2. Calculate next due date
//         val nextDueDateMillis = currentTime + task.periodInMillis
//         val nextDueDate = Date(nextDueDateMillis)
//
//         // 3. Update the task
//         val updatedTask = task.copy(lastDone = completionTimeDate, dueDate = nextDueDate)
//         taskViewModel.update(updatedTask)
//
//         Toast.makeText(this, "${task.name} marked done. Next due: ${android.text.format.DateFormat.getDateFormat(this).format(nextDueDate)}", Toast.LENGTH_SHORT).show()
//     }


    private fun showAddTaskDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null)
        val taskNameEditText = dialogView.findViewById<EditText>(R.id.edit_task_name)
        val periodValueEditText = dialogView.findViewById<EditText>(R.id.edit_period_value)
        val periodUnitSpinner = dialogView.findViewById<Spinner>(R.id.spinner_task_period)
        val commentsEditText = dialogView.findViewById<EditText>(R.id.edit_task_comments)
        val tagsEditText = dialogView.findViewById<TextInputEditText>(R.id.edit_task_tags) // Changed to TextInputEditText

        // Setup Period Unit Spinner
        val periodUnits = listOf("Hours", "Days", "Weeks", "Months", "Years")
        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, periodUnits)
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        periodUnitSpinner.adapter = unitAdapter
        periodUnitSpinner.setSelection(1) // Default to "Days"

        AlertDialog.Builder(this)
            .setTitle("Add New Task")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val taskName = taskNameEditText.text.toString()
                val periodValueStr = periodValueEditText.text.toString()
                val comments = commentsEditText.text.toString().trim()
                val tagsString = tagsEditText.text.toString().trim()

                if (taskName.isNotBlank() && periodValueStr.isNotBlank()) {
                    val periodValue = periodValueStr.toDoubleOrNull()
                    if (periodValue == null || periodValue <= 0) {
                        Toast.makeText(this, "Please enter a valid period value.", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val selectedUnit = periodUnitSpinner.selectedItem.toString()
                    val periodInMillis = getCustomPeriodInMillis(periodValue, selectedUnit)

                    if (periodInMillis <= 0) {
                        Toast.makeText(this, "Invalid period calculation.", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val tagsList = tagsString.split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    val currentTime = System.currentTimeMillis()
                    val newTask = Task(
                        name = taskName,
                        periodInMillis = periodInMillis,
                        lastDone = null,
                        dueDate = Date(currentTime), // Due immediately by default
                        comments = comments.ifEmpty { null },
                        tags = tagsList
                    )
                    taskViewModel.insert(newTask)
                } else {
                    Toast.makeText(this, "Task name and period value cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Removed old getPeriodInMillis function

    private fun getCustomPeriodInMillis(value: Double, unit: String): Long {
        val calendar = Calendar.getInstance()
        val startTime = calendar.timeInMillis // Current time to calculate against for precision

        when (unit) {
            "Hours" -> calendar.add(Calendar.HOUR_OF_DAY, (value).toInt()) // Or use fractional hours if needed more precisely
            "Days" -> calendar.add(Calendar.DAY_OF_YEAR, (value).toInt())
            "Weeks" -> calendar.add(Calendar.WEEK_OF_YEAR, (value).toInt())
            "Months" -> calendar.add(Calendar.MONTH, (value).toInt())
            "Years" -> calendar.add(Calendar.YEAR, (value).toInt())
            else -> return 0L // Should not happen
        }
        // For fractional parts, this simple Calendar.add might truncate.
        // A more precise calculation for fractional periods:
        val unitMillis: Long = when (unit) {
            "Hours" -> (value * 60 * 60 * 1000L).toLong()
            "Days" -> (value * 24 * 60 * 60 * 1000L).toLong()
            "Weeks" -> (value * 7 * 24 * 60 * 60 * 1000L).toLong()
            // For Months and Years, using average or Calendar is a choice.
            // Calendar.add is more accurate for date boundaries but doesn't directly give "periodInMillis" for storage if fractions are used.
            // Let's use average for storage if fractional, but Calendar.add for whole numbers is also fine for due date calculation.
            // The current 'Task' model stores 'periodInMillis'. For consistency with "3.5 weeks":
            "Months" -> (value * 30.4375 * 24 * 60 * 60 * 1000L).toLong() // Average days in month
            "Years" -> (value * 365.25 * 24 * 60 * 60 * 1000L).toLong()   // Average days in year
            else -> 0L
        }
        return unitMillis
    }

    private fun markTaskDone(task: Task) {
        val currentTime = System.currentTimeMillis()
        val completionTimeDate = Date(currentTime)

        val completionRecord = CompletionRecord(taskId = task.id, completionTime = completionTimeDate)
        taskViewModel.insertCompletionRecord(completionRecord)

        // Calculate next due date using the task's stored periodInMillis
        val nextDueDateMillis = currentTime + task.periodInMillis
        val nextDueDate = Date(nextDueDateMillis)

        val updatedTask = task.copy(lastDone = completionTimeDate, dueDate = nextDueDate)
        taskViewModel.update(updatedTask)

        val dateFormat = android.text.format.DateFormat.getDateFormat(this) // Or getMediumDateFormat
        Toast.makeText(this, "${task.name} done. Next due: ${dateFormat.format(nextDueDate)}", Toast.LENGTH_SHORT).show()
    }
}

