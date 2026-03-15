package com.enabwf.periodic

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HistoryActivity : AppCompatActivity() {

    private val viewModel: HistoryViewModel by viewModels {
        val database = TaskDatabase.getDatabase(application)
        val repository = TaskRepository(database.taskDao())
        HistoryViewModelFactory(repository)
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

        adapter = HistoryAdapter()
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
}
