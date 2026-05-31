package com.enabwf.periodic

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class HistoryActivity : AppCompatActivity() {

    private val viewModel: HistoryViewModel by viewModels {
        val database = TaskDatabase.getDatabase(application)
        val repository = TaskRepository(database.taskDao())
        HistoryViewModelFactory(repository)
    }
    private val taskViewModel: TaskViewModel by viewModels {
        TaskViewModelFactory(application)
    }

    private lateinit var adapter: HistoryAdapter
    private lateinit var spinner: Spinner
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val recyclerView = findViewById<RecyclerView>(R.id.history_list)
        spinner = findViewById(R.id.history_filter_spinner)
        progressBar = findViewById(R.id.history_progress_bar)

        adapter = HistoryAdapter(onItemClick = { historyItem ->
            showCompletionActionsDialog(historyItem)
        })
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                    && firstVisibleItemPosition >= 0
                ) {
                    viewModel.loadHistory()
                }
            }
        })

        viewModel.historyItems.observe(this) { items ->
            adapter.updateList(items)
            progressBar.visibility = View.GONE
        }

        viewModel.tags.observe(this) { tags ->
            val filterOptions = mutableListOf("All")
            filterOptions.addAll(tags)
            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = spinnerAdapter
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedTag = parent.getItemAtPosition(position) as String
                viewModel.setFilter(if (selectedTag == "All") null else selectedTag)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun showCompletionActionsDialog(historyItem: CompletionHistoryItem) {
        val options = arrayOf("Edit completion time", "Delete completion record")
        MaterialAlertDialogBuilder(this)
            .setTitle(historyItem.taskName)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showEditCompletionTimePicker(historyItem)
                    1 -> showDeleteConfirmationDialog(historyItem)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditCompletionTimePicker(historyItem: CompletionHistoryItem) {
        val selectedDateTime = Calendar.getInstance().apply {
            time = historyItem.completionTime
        }

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Edit completion date")
            .setSelection(selectedDateTime.timeInMillis)
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setEnd(System.currentTimeMillis())
                    .build()
            )
            .build()

        datePicker.addOnPositiveButtonClickListener { selectedDateMillis ->
            selectedDateTime.timeInMillis = selectedDateMillis

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(
                    if (android.text.format.DateFormat.is24HourFormat(this)) {
                        TimeFormat.CLOCK_24H
                    } else {
                        TimeFormat.CLOCK_12H
                    }
                )
                .setHour(selectedDateTime.get(Calendar.HOUR_OF_DAY))
                .setMinute(selectedDateTime.get(Calendar.MINUTE))
                .setTitleText("Edit completion time")
                .build()

            timePicker.addOnPositiveButtonClickListener {
                selectedDateTime.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                selectedDateTime.set(Calendar.MINUTE, timePicker.minute)
                selectedDateTime.set(Calendar.SECOND, 0)
                selectedDateTime.set(Calendar.MILLISECOND, 0)

                // MaterialDatePicker constraints only limit date, so validate date+time as well.
                if (selectedDateTime.timeInMillis > System.currentTimeMillis()) {
                    Toast.makeText(this, "Completion time cannot be in the future.", Toast.LENGTH_SHORT).show()
                    return@addOnPositiveButtonClickListener
                }

                updateCompletionRecord(historyItem, selectedDateTime.timeInMillis)
            }

            timePicker.show(supportFragmentManager, "EDIT_COMPLETION_TIME_PICKER")
        }

        datePicker.show(supportFragmentManager, "EDIT_COMPLETION_DATE_PICKER")
    }

    private fun updateCompletionRecord(historyItem: CompletionHistoryItem, newCompletionTimeMillis: Long) {
        lifecycleScope.launch {
            try {
                val task = taskViewModel.getTaskById(historyItem.taskId)
                if (task == null) {
                    Toast.makeText(
                        this@HistoryActivity,
                        "Unable to update completion: task not found.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                taskViewModel.updateCompletionRecord(
                    CompletionRecord(
                        id = historyItem.completionId,
                        taskId = historyItem.taskId,
                        completionTime = Date(newCompletionTimeMillis)
                    )
                )

                refreshTaskTimingAndMedians(task)

                viewModel.loadHistory(reset = true)
                Toast.makeText(this@HistoryActivity, "Completion updated.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@HistoryActivity, "Failed to update completion.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirmationDialog(historyItem: CompletionHistoryItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete completion record?")
            .setMessage("This removes the selected completion from history.")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteCompletionRecord(historyItem)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCompletionRecord(historyItem: CompletionHistoryItem) {
        lifecycleScope.launch {
            try {
                val task = taskViewModel.getTaskById(historyItem.taskId)
                if (task == null) {
                    Toast.makeText(
                        this@HistoryActivity,
                        "Unable to delete completion: task not found.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                taskViewModel.deleteCompletionRecordById(historyItem.completionId)
                refreshTaskTimingAndMedians(task)

                viewModel.loadHistory(reset = true)
                Toast.makeText(this@HistoryActivity, "Completion deleted.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@HistoryActivity, "Failed to delete completion.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun refreshTaskTimingAndMedians(task: Task) {
        val completionRecords = taskViewModel.getCompletionRecordsForTaskList(task.id)
        val latestCompletion = completionRecords.maxByOrNull { it.completionTime.time }?.completionTime
        val medianHistory = PeriodCalculator.calculateMedian(completionRecords)
        val medianRecent = PeriodCalculator.calculateRecentMedian(completionRecords)

        // For history edits/deletes, task schedule should reflect the actual latest completion.
        val recalculatedDueDate = latestCompletion?.let { Date(it.time + task.periodInMillis) } ?: task.dueDate

        taskViewModel.updateTask(
            task.copy(
                lastDone = latestCompletion,
                dueDate = recalculatedDueDate,
                medianHistoryPeriod = medianHistory,
                medianRecentPeriod = medianRecent
            )
        )
    }
}
