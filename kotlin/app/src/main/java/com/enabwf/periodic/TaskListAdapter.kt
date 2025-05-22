package com.enabwf.periodic

import android.util.TypedValue
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.chip.Chip
import java.util.Date

class TaskListAdapter(private var tasks: List<Task>) : RecyclerView.Adapter<TaskListAdapter.TaskViewHolder>() {

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val taskName: TextView = view.findViewById(R.id.task_name)
        val taskDueDate: TextView = view.findViewById(R.id.task_due_date)
        val commentsPreview: TextView? = view.findViewById(R.id.task_comments_preview) // Make nullable if optional
        val tagsChipGroup: ChipGroup? = view.findViewById(R.id.task_tags_group) // Make nullable if optional

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.task_list_item, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.taskName.text = task.name

        val currentTime = Date() // Get current time to check for overdue

        // Standard way to get a theme-based text color (e.g., textColorSecondary)
        val defaultTextColor: Int
        val typedValue = TypedValue()
        holder.itemView.context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        defaultTextColor = ContextCompat.getColor(holder.itemView.context, typedValue.resourceId)

        if (task.dueDate != null) {
            val dateFormat = android.text.format.DateFormat.getMediumDateFormat(holder.itemView.context)
            holder.taskDueDate.text = "Due: ${dateFormat.format(task.dueDate!!)}"

            if (task.dueDate!!.before(currentTime) && task.lastDone?.before(task.dueDate!!) != false) { // Task is overdue
                holder.taskDueDate.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.red_overdue))
            } else { // Task is not overdue or due date is in the future
                holder.taskDueDate.setTextColor(defaultTextColor)
            }
        } else {
            holder.taskDueDate.text = "Due date not set"
            holder.taskDueDate.setTextColor(defaultTextColor) // Set to default if no due date
        }
        val commentsPreview: TextView = holder.itemView.findViewById(R.id.task_comments_preview)
        val tagsChipGroup: ChipGroup = holder.itemView.findViewById(R.id.task_tags_group) // Make sure ChipGroup is imported

        holder.commentsPreview?.let { commentsTextView ->
            if (!task.comments.isNullOrEmpty()) {
                commentsTextView.text = task.comments.take(50) + if (task.comments.length > 50) "..." else ""
                commentsTextView.visibility = View.VISIBLE
            } else {
                commentsTextView.visibility = View.GONE
            }
        }

        // Handle tags (if you implemented it)
        holder.tagsChipGroup?.let { chipGroup ->
            chipGroup.removeAllViews() // Clear old chips
            if (task.tags.isNotEmpty()) {
                task.tags.forEach { tagName ->
                    val chip = Chip(holder.itemView.context)
                    chip.text = tagName
                    chipGroup.addView(chip)
                }
                chipGroup.visibility = View.VISIBLE
            } else {
                chipGroup.visibility = View.GONE
            }
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
        // Add bounds check for safety, though itemCount should be correct
        if (position >= 0 && position < tasks.size) {
            return tasks[position]
        }
        // Fallback or throw exception - this ideally shouldn't be reached if position is always valid
        // For now, let's assume valid position based on prior checks in MainActivity
        return tasks.first() // Or handle error appropriately
    }
}
