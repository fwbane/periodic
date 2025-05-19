package com.enabwf.periodic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class CompletionHistoryAdapter(private var records: List<CompletionRecord>) :
    RecyclerView.Adapter<CompletionHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val completionTimeText: TextView = view.findViewById(android.R.id.text1) // Using simple text1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false) // Simple layout
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        holder.completionTimeText.text = dateFormat.format(record.completionTime)
    }

    override fun getItemCount() = records.size

    fun submitList(newRecords: List<CompletionRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }
}