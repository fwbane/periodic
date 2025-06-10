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
import com.google.android.material.color.MaterialColors

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

//    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.taskName.text = task.name

        val currentTime = Date() // Get current time to check for overdue

        val dueDateDefaultColor = MaterialColors.getColor(
            holder.itemView,
            com.google.android.material.R.attr.colorOnSurfaceVariant // Use colorOnSurfaceVariant for secondary text
        )

        if (task.dueDate != null) {
            val dateFormat = android.text.format.DateFormat.getMediumDateFormat(holder.itemView.context)
            holder.taskDueDate.text =
                holder.itemView.context.getString(
                    R.string.due_date_display,
                    dateFormat.format(task.dueDate!!)
                )

//            val currentTime = System.currentTimeMillis() // Get current time once for comparison

            // Check if task is overdue: due date is in the past AND (lastDone is null OR lastDone was before due date)
            // The previous logic `task.lastDone?.before(task.dueDate!!) != false` is a bit complex.
            // It means `lastDone is null` OR `lastDone is NOT after dueDate`.
            // Let's simplify to: If lastDone exists, it must be after dueDate to count as 'done'.
            // Otherwise, if due date is in the past, it's overdue.

            val isOverdue = task.dueDate!!.before(currentTime) &&
                    (task.lastDone == null || task.lastDone!!.before(task.dueDate!!))

            if (isOverdue) { // Task is overdue
                // Directly use your predefined red_overdue color from colors.xml
                holder.taskDueDate.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.red_overdue))
            } else { // Task is not overdue or due date is in the future
                holder.taskDueDate.setTextColor(dueDateDefaultColor)
            }
        } else {
            holder.taskDueDate.text = holder.itemView.context.getString(R.string.due_date_not_set)
            holder.taskDueDate.setTextColor(dueDateDefaultColor) // Set to themed default if no due date
        }
        val commentsPreview: TextView = holder.itemView.findViewById(R.id.task_comments_preview)
        val tagsChipGroup: ChipGroup = holder.itemView.findViewById(R.id.task_tags_group) // Make sure ChipGroup is imported

        holder.commentsPreview?.let { commentsTextView ->
            if (!task.comments.isNullOrEmpty()) {
                commentsTextView.text = buildString {
                    append(task.comments.take(50))
                    append(if (task.comments.length > 50) "..." else "")
                }
                commentsTextView.visibility = View.VISIBLE
            } else {
                commentsTextView.visibility = View.GONE
            }
        }

        // Handle tags
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
