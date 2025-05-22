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
import android.widget.ArrayAdapter
import android.widget.Toast
import android.widget.TextView // Import TextView
import androidx.appcompat.app.AlertDialog // Ensure this is androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat // For date formatting
import java.util.Locale
import java.util.concurrent.TimeUnit // For actual period calculation
import android.app.DatePickerDialog // For Date Picker
import android.app.TimePickerDialog // For Time Picker

class MainActivity : AppCompatActivity() {

    private val taskViewModel: TaskViewModel by viewModels {
        TaskViewModelFactory(application)
    }

    private lateinit var taskListAdapter: TaskListAdapter // Make adapter a class member


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val taskRecyclerView: RecyclerView = findViewById(R.id.task_list)

        // Initialize RecyclerView and Adapter
        taskListAdapter = TaskListAdapter(emptyList())
        taskRecyclerView.adapter = taskListAdapter
        taskRecyclerView.layoutManager = LinearLayoutManager(this)  // Set a LayoutManager

        // Observe LiveData and submit list to the adapter
        taskViewModel.allTasks.observe(this, Observer { tasks ->
            tasks?.let { taskListAdapter.submitList(it) }
        })

        val addTaskButton: FloatingActionButton = findViewById(R.id.add_task_button)
        addTaskButton.setOnClickListener {
            showAddTaskDialog()
        }

        // Add click listener for RecyclerView items
        taskRecyclerView.addOnItemTouchListener(
            RecyclerItemClickListener(this, taskRecyclerView, object : RecyclerItemClickListener.OnItemClickListener {
                override fun onItemClick(view: View, position: Int) {
                    if (position >= 0 && position < taskListAdapter.itemCount) { // Check bounds
                        val task = taskListAdapter.getItem(position)
                        showMarkDoneOptionsDialog(task)
                    }
                }

                override fun onLongItemClick(view: View, position: Int) {
                    if (position >= 0 && position < taskListAdapter.itemCount) { // Check bounds
                        val task = taskListAdapter.getItem(position)
                        showEditTaskDialog(task) // Call edit dialog on long press
                    }
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

    private fun markTaskDone(task: Task, completionTimeMillis: Long) {
//        val currentTime = System.currentTimeMillis()
        val completionTimeDate = Date(completionTimeMillis)

        val completionRecord = CompletionRecord(taskId = task.id, completionTime = completionTimeDate)
        taskViewModel.insertCompletionRecord(completionRecord)

        // Calculate next due date using the task's stored periodInMillis
        val nextDueDateMillis = completionTimeMillis  + task.periodInMillis
        val nextDueDate = Date(nextDueDateMillis)

        val updatedTask = task.copy(lastDone = completionTimeDate, dueDate = nextDueDate)
        taskViewModel.update(updatedTask)

        val dateFormat = android.text.format.DateFormat.getDateFormat(this) // Or getMediumDateFormat
        Toast.makeText(this, "${task.name} done. Next due: ${dateFormat.format(nextDueDate)}", Toast.LENGTH_SHORT).show()
    }

    private fun showMarkDoneOptionsDialog(task: Task) {
        val options = arrayOf("Mark Done Now", "Mark with Custom Time")
        AlertDialog.Builder(this)
            .setTitle("Complete Task: ${task.name}")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> { // Mark Done Now
                        markTaskDone(task, System.currentTimeMillis())
                    }
                    1 -> { // Mark with Custom Time
                        showCustomTimePickerForCompletion(task)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomTimePickerForCompletion(task: Task) {
        val calendar = Calendar.getInstance() // Used to get current date/time and to set selected values

        // Date Picker Dialog
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, monthOfYear, dayOfMonth ->
                // Date selected, now show Time Picker
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, monthOfYear)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                // Time Picker Dialog
                val timePickerDialog = TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0) // Optional: zero out seconds
                        calendar.set(Calendar.MILLISECOND, 0) // Optional: zero out milliseconds

                        // Date and Time selected, mark task done
                        markTaskDone(task, calendar.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(this) // Use device's 24-hour setting
                )
                timePickerDialog.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        // Optional: Prevent selecting future dates for completion time
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun showEditTaskDialog(task: Task) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_task, null)

        val taskNameEditText = dialogView.findViewById<TextInputEditText>(R.id.edit_task_name_edittext)
        val periodValueEditText = dialogView.findViewById<EditText>(R.id.edit_period_value_edittext)
        val periodUnitSpinner = dialogView.findViewById<Spinner>(R.id.edit_period_unit_spinner)
        val nextDueDateTextView = dialogView.findViewById<TextView>(R.id.text_next_due_date)
        val tagsEditText = dialogView.findViewById<TextInputEditText>(R.id.edit_task_tags_edittext)
        val commentsEditText = dialogView.findViewById<TextInputEditText>(R.id.edit_task_comments_edittext)
        val historyRecyclerView = dialogView.findViewById<RecyclerView>(R.id.recycler_completion_history)
        val actualPeriodTextView = dialogView.findViewById<TextView>(R.id.text_actual_period)

        // Populate existing data
        taskNameEditText.setText(task.name)
        tagsEditText.setText(task.tags.joinToString(", "))
        commentsEditText.setText(task.comments ?: "")

        // Populate period
        val (value, unit) = convertMillisToPeriodComponents(task.periodInMillis)
        periodValueEditText.setText(value.toString())
        val periodUnits = listOf("Hours", "Days", "Weeks", "Months", "Years") // Must match getCustomPeriodInMillis
        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, periodUnits)
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        periodUnitSpinner.adapter = unitAdapter
        val unitPosition = periodUnits.indexOf(unit)
        periodUnitSpinner.setSelection(if (unitPosition != -1) unitPosition else 1) // Default to Days if not found

        // Display Next Due Date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        nextDueDateTextView.text = task.dueDate?.let { dateFormat.format(it) } ?: "N/A"

        // Setup Completion History RecyclerView
        val historyAdapter = CompletionHistoryAdapter(emptyList())
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyRecyclerView.adapter = historyAdapter
        historyRecyclerView.isNestedScrollingEnabled = false // Important for RecyclerView in ScrollView

        taskViewModel.getCompletionRecordsForTask(task.id).observe(this, Observer { records ->
            records?.let {
                // Show last 10 or make it configurable. For now, all.
                historyAdapter.submitList(it.take(10)) // Or it for all records

                // Calculate and Display Actual Period
                if (it.size >= 2) {
                    val actualPeriodMillis = calculateActualPeriod(it)
                    actualPeriodTextView.text = formatDuration(actualPeriodMillis)
                } else {
                    actualPeriodTextView.text = "N/A (needs at least 2 completions)"
                }
            }
        })

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save", null) // We'll handle click manually for validation
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Archive", null) // For Archive action
            .create()

        dialog.setOnShowListener {
            // SAVE Button
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = taskNameEditText.text.toString().trim()
                val newPeriodValueStr = periodValueEditText.text.toString()
                val newTagsStr = tagsEditText.text.toString().trim()
                val newComments = commentsEditText.text.toString().trim()

                if (newName.isBlank() || newPeriodValueStr.isBlank()) {
                    Toast.makeText(this, "Name and Period Value cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val newPeriodValue = newPeriodValueStr.toDoubleOrNull()
                if (newPeriodValue == null || newPeriodValue <= 0) {
                    Toast.makeText(this, "Invalid Period Value.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val newSelectedUnit = periodUnitSpinner.selectedItem.toString()
                val newPeriodInMillis = getCustomPeriodInMillis(newPeriodValue, newSelectedUnit)

                val newTagsList = newTagsStr.split(',').map { tg -> tg.trim() }.filter { tg -> tg.isNotEmpty() }

                // Due Date Recalculation Logic:
                // If period changed, new due date is (lastDone or (currentDueDate - oldPeriod)) + newPeriod
                val newDueDate: Date = if (newPeriodInMillis != task.periodInMillis) {
                    val baseTime = task.lastDone?.time ?: (task.dueDate!!.time - task.periodInMillis)
                    Date(baseTime + newPeriodInMillis)
                } else {
                    task.dueDate!! // Keep original if period didn't change
                }

                val updatedTask = task.copy(
                    name = newName,
                    periodInMillis = newPeriodInMillis,
                    tags = newTagsList,
                    comments = newComments.ifEmpty { null },
                    dueDate = newDueDate // Updated due date
                )
                taskViewModel.update(updatedTask)
                dialog.dismiss()
            }

            // DELETE Button (Now "Deactivate")
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Archive")
                    .setMessage("Are you sure you want to archive '${task.name}'?")
                    .setPositiveButton("Archive") { _, _ ->
                        taskViewModel.markTaskAsInactive(task)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        dialog.show()
    }

    // Helper to convert periodInMillis back to value and unit (approximate for display)
    private fun convertMillisToPeriodComponents(millis: Long): Pair<Double, String> {
        val dayMillis = 24 * 60 * 60 * 1000L
        val hourMillis = 60 * 60 * 1000L

        return when {
            millis % (365.25 * dayMillis).toLong() == 0L && millis / (365.25 * dayMillis).toLong() > 0 -> Pair(millis / (365.25 * dayMillis), "Years")
            millis % (30.4375 * dayMillis).toLong() == 0L && millis / (30.4375 * dayMillis).toLong() > 0 -> Pair(millis / (30.4375 * dayMillis), "Months")
            millis % (7 * dayMillis) == 0L && millis / (7 * dayMillis) > 0 -> Pair((millis / (7 * dayMillis)).toDouble(), "Weeks")
            millis % dayMillis == 0L && millis / dayMillis > 0 -> Pair((millis / dayMillis).toDouble(), "Days")
            millis % hourMillis == 0L && millis / hourMillis > 0 -> Pair((millis / hourMillis).toDouble(), "Hours")
            else -> Pair( (millis.toDouble() / dayMillis.toDouble()), "Days") // Default to days if not perfectly divisible
        }
    }

    private fun calculateActualPeriod(records: List<CompletionRecord>): Long {
        if (records.size < 2) return 0L
        // Records are sorted DESC by DAO, so reverse for chronological calculation
        val sortedRecords = records.sortedBy { it.completionTime.time }
        var totalDifference = 0L
        for (i in 0 until sortedRecords.size - 1) {
            totalDifference += (sortedRecords[i+1].completionTime.time - sortedRecords[i].completionTime.time)
        }
        return totalDifference / (sortedRecords.size - 1)
    }

    // Helper to format duration (Long millis) into a human-readable string
    private fun formatDuration(millis: Long): String {
        if (millis <= 0) return "N/A"

        val days = TimeUnit.MILLISECONDS.toDays(millis)
        val hours = TimeUnit.MILLISECONDS.toHours(millis) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60

        val parts = mutableListOf<String>()
        if (days > 0) parts.add("$days day${if (days > 1) "s" else ""}")
        if (hours > 0) parts.add("$hours hour${if (hours > 1) "s" else ""}")
        if (minutes > 0 && days == 0L) parts.add("$minutes minute${if (minutes > 1) "s" else ""}") // Show minutes if duration is less than a day

        return if (parts.isEmpty()) "Less than a minute" else parts.joinToString(", ")
    }
}


