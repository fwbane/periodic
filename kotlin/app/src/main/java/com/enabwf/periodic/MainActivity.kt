package com.enabwf.periodic

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.os.Bundle
import android.content.Intent
import android.widget.ListView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.bottomnavigation.BottomNavigationView
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter


class MainActivity : AppCompatActivity() {

    private val taskViewModel: TaskViewModel by viewModels {
        TaskViewModelFactory(application)
    }

    private lateinit var taskListAdapter: TaskListAdapter // Make adapter a class member

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { exportDatabaseToUri(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { showImportConfirmationDialog(it) }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val taskRecyclerView: RecyclerView = findViewById(R.id.task_list)

        // Initialize RecyclerView and Adapter
        taskListAdapter = TaskListAdapter(emptyList())
        taskRecyclerView.adapter = taskListAdapter
        taskRecyclerView.layoutManager = LinearLayoutManager(this)  // Set a LayoutManager

        // Trigger backfill of median calculations if needed
        taskViewModel.checkAndBackfillMedians()

        // Observe LiveData and submit list to the adapter
        taskViewModel.allTasks.observe(this, Observer { tasks ->
            tasks?.let { taskListAdapter.submitList(it) }
        })

        val addTaskButton: FloatingActionButton = findViewById(R.id.add_task_button)
        addTaskButton.setOnClickListener {
            showAddTaskDialog()
        }

        val bottomNavigation: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                }
                R.id.navigation_analytics -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java))
                }
                R.id.navigation_settings -> {
                    showSettingsDialog()
                }
            }
            false
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

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Add New Task")
            .setView(dialogView)
            .setPositiveButton("Add", null) // Set to null, we'll handle click manually
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val taskName = taskNameEditText.text.toString().trim() // Ensure it's trimmed
                val periodValueStr = periodValueEditText.text.toString()
                val comments = commentsEditText.text.toString().trim()
                val tagsString = tagsEditText.text.toString().trim()

                if (taskName.isBlank()) { // Basic check for blank name
                    Toast.makeText(this, "Task name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener // Keep dialog open
                }
                if (periodValueStr.isBlank()) {
                    Toast.makeText(this, "Period value cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }


                // Launch a coroutine to check for name existence (suspend function)
                lifecycleScope.launch {
                    if (taskViewModel.doesTaskNameExist(taskName)) {
                        Toast.makeText(this@MainActivity, "A task with this name already exists. Please use a different name.", Toast.LENGTH_LONG).show()
                        // Do not dismiss the dialog, let the user correct the name
                    } else {
                        // Name does not exist, proceed to create and insert the task
                        val periodValue = periodValueStr.toDoubleOrNull()
                        if (periodValue == null || periodValue <= 0) {
                            Toast.makeText(this@MainActivity, "Please enter a valid period value.", Toast.LENGTH_SHORT).show()
                            return@launch // Exit coroutine, keep dialog open
                        }

                        val selectedUnit = periodUnitSpinner.selectedItem.toString()
                        val periodInMillis = getCustomPeriodInMillis(periodValue, selectedUnit)

                        if (periodInMillis <= 0) {
                            Toast.makeText(this@MainActivity, "Invalid period calculation.", Toast.LENGTH_SHORT).show()
                            return@launch // Exit coroutine, keep dialog open
                        }

                        val tagsList = tagsString.split(',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }

                        val currentTime = System.currentTimeMillis()
                        val newTask = Task(
                            name = taskName,
                            periodInMillis = periodInMillis,
                            lastDone = null,
                            dueDate = Date(currentTime),
                            comments = comments.ifEmpty { null },
                            tags = tagsList,
                            isActive = true // Explicitly set, though it's the default
                        )
                        taskViewModel.insert(newTask)
                        dialog.dismiss() // Dismiss dialog only on successful addition
                    }
                }
            }
        }
        dialog.show()
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
        lifecycleScope.launch {
            val completionTimeDate = Date(completionTimeMillis)

            val completionRecord = CompletionRecord(taskId = task.id, completionTime = completionTimeDate)
            taskViewModel.addCompletionRecord(completionRecord) // suspend

            // Calculate medians
            val updatedRecords = taskViewModel.getCompletionRecordsForTaskList(task.id)
            val medianHistory = PeriodCalculator.calculateMedian(updatedRecords)
            val medianRecent = PeriodCalculator.calculateRecentMedian(updatedRecords)

            // Calculate next due date using the task's stored periodInMillis
            val nextDueDateMillis = completionTimeMillis  + task.periodInMillis
            val nextDueDate = Date(nextDueDateMillis)
            val currentDueDate = task.dueDate
            // check if new due date is before existing one
            val actualNextDueDate: Date = if (currentDueDate != null && nextDueDate.before(currentDueDate)) {
                // If the calculated date is BEFORE the existing due date, KEEP the existing due date.
                currentDueDate
            } else {
                // Otherwise, use the calculated date (e.g., if it's past the original due date, or if task.dueDate is null)
                nextDueDate
            }

            val updatedTask = task.copy(
                lastDone = completionTimeDate,
                dueDate = actualNextDueDate,
                medianHistoryPeriod = medianHistory,
                medianRecentPeriod = medianRecent
            )
            taskViewModel.updateTask(updatedTask) // suspend

            val dateFormat = android.text.format.DateFormat.getDateFormat(this@MainActivity) // Or getMediumDateFormat
            Toast.makeText(this@MainActivity, "${task.name} done. Next due: ${dateFormat.format(actualNextDueDate)}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMarkDoneOptionsDialog(task: Task) {
        val options = arrayOf("Mark Done Now", "Mark with Custom Time")
        MaterialAlertDialogBuilder(this)
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

        val datePickerBuilder = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select completion date")
            .setSelection(calendar.timeInMillis) // Set initial selection to current date
            // Optional: Prevent selecting future dates for completion time
//            .setTheme(com.google.android.material.R.style.ThemeOverlay_MaterialComponents_DatePicker) // Use Material 3 DatePicker theme

        // For limiting max date, you need to use a DateValidator
        // If you always want maxDate as now:
        datePickerBuilder.setCalendarConstraints(
            com.google.android.material.datepicker.CalendarConstraints.Builder()
                .setEnd(System.currentTimeMillis()) // Set max selectable date to now
                .build()
        )


        val datePicker = datePickerBuilder.build()

        datePicker.addOnPositiveButtonClickListener { selectedDateMillis ->
            // Date selected, now show Time Picker
            calendar.timeInMillis = selectedDateMillis
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)

            // --- Material Time Picker Dialog ---
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(if (android.text.format.DateFormat.is24HourFormat(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(currentHour)
                .setMinute(currentMinute)
                .setTitleText("Select completion time")
                .build()

            timePicker.addOnPositiveButtonClickListener {
                calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                calendar.set(Calendar.MINUTE, timePicker.minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                // Date and Time selected, mark task done
                markTaskDone(task, calendar.timeInMillis)
            }

            // Show the Time Picker Dialog
            // You need to pass the fragment manager of your Activity/Fragment
            timePicker.show(supportFragmentManager, "MATERIAL_TIME_PICKER_TAG")
        }

        // Show the Date Picker Dialog
        datePicker.show(supportFragmentManager, "MATERIAL_DATE_PICKER_TAG")
    }
//        val datePickerDialog = DatePickerDialog(
//            this,
//            { _, year, monthOfYear, dayOfMonth ->
//                // Date selected, now show Time Picker
//                calendar.set(Calendar.YEAR, year)
//                calendar.set(Calendar.MONTH, monthOfYear)
//                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
//
//                // Time Picker Dialog
//                val timePickerDialog = TimePickerDialog(
//                    this,
//                    { _, hourOfDay, minute ->
//                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
//                        calendar.set(Calendar.MINUTE, minute)
//                        calendar.set(Calendar.SECOND, 0) // Optional: zero out seconds
//                        calendar.set(Calendar.MILLISECOND, 0) // Optional: zero out milliseconds
//
//                        // Date and Time selected, mark task done
//                        markTaskDone(task, calendar.timeInMillis)
//                    },
//                    calendar.get(Calendar.HOUR_OF_DAY),
//                    calendar.get(Calendar.MINUTE),
//                    android.text.format.DateFormat.is24HourFormat(this) // Use device's 24-hour setting
//                )
//                timePickerDialog.show()
//            },
//            calendar.get(Calendar.YEAR),
//            calendar.get(Calendar.MONTH),
//            calendar.get(Calendar.DAY_OF_MONTH)
//        )
//        // Optional: Prevent selecting future dates for completion time
//        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
//        datePickerDialog.show()
//    }


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
                // Show last 5 or make it configurable.
                historyAdapter.submitList(it.take(5)) // Or it for all records
            }
        })

        // Display Median Periods
        val medianHistoryStr = formatDuration(task.medianHistoryPeriod ?: 0)
        val medianRecentStr = formatDuration(task.medianRecentPeriod ?: 0)
        actualPeriodTextView.text = "$medianHistoryStr / $medianRecentStr"

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Save", null) // We'll handle click manually for validation
            .setNegativeButton("Cancel") { dialogInterface, _ -> // Use the standard lambda
                dialogInterface.dismiss() // Dismiss the dialog on Cancel
            }
            .setNeutralButton("Archive", null)
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

                lifecycleScope.launch {
                    // Check for duplicate name only if the name has changed
                    if (newName.equals(task.name, ignoreCase = true) || !taskViewModel.doesOtherTaskNameExist(newName, task.id)) {
                        // Name is unique or hasn't changed, proceed with saving
                        val newSelectedUnit = periodUnitSpinner.selectedItem.toString()
                        val newPeriodInMillis = getCustomPeriodInMillis(newPeriodValue, newSelectedUnit)

                        val newTagsList = newTagsStr.split(',').map { tg -> tg.trim() }.filter { tg -> tg.isNotEmpty() }

                        // Due Date Recalculation Logic:
                        // If period changed, new due date is anchored to (lastDone or (currentDueDate - oldPeriod)) + newPeriod
                        val newDueDate: Date = if (newPeriodInMillis != task.periodInMillis) {
                            val baseTimeForDueDateCalc = task.lastDone?.time ?: (task.dueDate!!.time - task.periodInMillis) // Original start of cycle
                            Date(baseTimeForDueDateCalc + newPeriodInMillis)
                        } else {
                            task.dueDate!! // Keep original if period didn't change
                        }

                        val updatedTask = task.copy(
                            name = newName,
                            periodInMillis = newPeriodInMillis,
                            tags = newTagsList,
                            comments = newComments.ifEmpty { null },
                            dueDate = newDueDate,
                            isActive = task.isActive // Preserve current active status
                        )
                        taskViewModel.update(updatedTask)
                        dialog.dismiss()
                    } else {
                        // Name conflicts with another existing task
                        Toast.makeText(this@MainActivity, "Another task with this name already exists. Please use a different name.", Toast.LENGTH_LONG).show()
                    }
                }
            }

            // DELETE Button (Now "Archive")
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Archive")
                    .setMessage("Are you sure you want to archive '${task.name}'?")
                    .setPositiveButton("Archive"){ dialog, _ -> // Lambda for dialog.dismiss() is 'dialog', not '_'
                        taskViewModel.markTaskAsInactive(task)
                        dialog.dismiss() // Dismiss the dialog here
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> // You can also dismiss or do other things here if needed
                        dialog.dismiss() // Explicitly dismiss if cancel is a no-op
                    }
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

    private fun showSettingsDialog() {
        val options = arrayOf(getString(R.string.export_database), getString(R.string.import_database))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> exportLauncher.launch("periodic_backup.csv")
                    1 -> importLauncher.launch(arrayOf("text/comma-separated-values", "text/csv"))
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportDatabaseToUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val tasks = taskViewModel.getAllTasks()
                val records = taskViewModel.getAllCompletionRecords()
                val csvContent = CsvHelper.toCsv(tasks, records)
                
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(csvContent)
                    }
                }
                Toast.makeText(this@MainActivity, R.string.export_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, R.string.error_exporting, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showImportConfirmationDialog(uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_confirmation_title)
            .setMessage(R.string.import_confirmation_message)
            .setPositiveButton(R.string.import_btn) { dialog, _ ->
                importDatabaseFromUri(uri)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun importDatabaseFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val csvContent = reader.readText()
                        val (tasks, records) = CsvHelper.fromCsv(csvContent)
                        taskViewModel.replaceDatabase(tasks, records)
                    }
                }
                Toast.makeText(this@MainActivity, R.string.import_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, R.string.error_importing, Toast.LENGTH_LONG).show()
            }
        }
    }
}


