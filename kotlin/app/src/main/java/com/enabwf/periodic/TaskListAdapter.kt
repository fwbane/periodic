package com.enabwf.periodic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.chip.Chip

class TaskListAdapter(private var tasks: List<Task>) : RecyclerView.Adapter<TaskListAdapter.TaskViewHolder>() {

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val taskName: TextView = view.findViewById(R.id.task_name)
        val taskDueDate: TextView = view.findViewById(R.id.task_due_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.task_list_item, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.taskName.text = task.name
        if (task.dueDate != null) {
            // Use a more user-friendly date format
            val dateFormat = android.text.format.DateFormat.getMediumDateFormat(holder.itemView.context)
            holder.taskDueDate.text = "Due: ${dateFormat.format(task.dueDate!!)}"
        } else {
            holder.taskDueDate.text = "Due date not set"
        }
        val commentsPreview: TextView = holder.itemView.findViewById(R.id.task_comments_preview)
        val tagsChipGroup: ChipGroup = holder.itemView.findViewById(R.id.task_tags_group) // Make sure ChipGroup is imported

        if (!task.comments.isNullOrEmpty()) {
            commentsPreview.text = task.comments.take(50) + if (task.comments.length > 50) "..." else "" // Show a snippet
            commentsPreview.visibility = View.VISIBLE
        } else {
            commentsPreview.visibility = View.GONE
        }

        tagsChipGroup.removeAllViews() // Clear old chips
        if (task.tags.isNotEmpty()) {
            task.tags.forEach { tagName ->
                val chip = Chip(holder.itemView.context) // Import com.google.android.material.chip.Chip
                chip.text = tagName
                // chip.isClickable = false // Or true if you want to filter by tags later
                // chip.isCheckable = false
                tagsChipGroup.addView(chip)
            }
            tagsChipGroup.visibility = View.VISIBLE
        } else {
            tagsChipGroup.visibility = View.GONE
        }
    }

    override fun getItemCount() = tasks.size

    // Add a submitList method to update the tasks list dynamically
    fun submitList(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged() // Notify that data has changed so the list updates
    }

    // Add a helper function to get an item at a specific position
    fun getItem(position: Int): Task {
        return tasks[position]
    }
}
