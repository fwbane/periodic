package com.enabwf.periodic

import java.util.Date

object CsvHelper {

    private const val TYPE_TASK = "TASK"
    private const val TYPE_COMPLETION = "COMPLETION"

    fun toCsv(tasks: List<Task>, records: List<CompletionRecord>): String {
        val sb = StringBuilder()
        
        // Header
        sb.append("type,id,name_or_taskId,periodInMillis_or_completionTime,lastDone,dueDate,comments,tags,isActive\n")

        // Tasks
        tasks.forEach { task ->
            sb.append(TYPE_TASK).append(",")
            sb.append(task.id).append(",")
            sb.append(escapeCsv(task.name)).append(",")
            sb.append(task.periodInMillis).append(",")
            sb.append(task.lastDone?.time ?: "").append(",")
            sb.append(task.dueDate?.time ?: "").append(",")
            sb.append(escapeCsv(task.comments ?: "")).append(",")
            sb.append(escapeCsv(task.tags.joinToString(","))).append(",")
            sb.append(if (task.isActive) 1 else 0).append("\n")
        }

        // Completion Records
        records.forEach { record ->
            sb.append(TYPE_COMPLETION).append(",")
            sb.append(record.id).append(",")
            sb.append(record.taskId).append(",")
            sb.append(record.completionTime.time).append(",")
            sb.append(",").append(",").append(",").append(",").append("\n")
        }

        return sb.toString()
    }

    fun fromCsv(csvContent: String): Pair<List<Task>, List<CompletionRecord>> {
        val tasks = mutableListOf<Task>()
        val records = mutableListOf<CompletionRecord>()
        
        val lines = csvContent.lines()
        if (lines.isEmpty()) return Pair(emptyList(), emptyList())

        // Skip header
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            val parts = parseCsvLine(line)
            if (parts.isEmpty()) continue

            val type = parts[0]
            when (type) {
                TYPE_TASK -> {
                    if (parts.size >= 9) {
                        val task = Task(
                            id = parts[1].toIntOrNull() ?: 0,
                            name = unescapeCsv(parts[2]),
                            periodInMillis = parts[3].toLongOrNull() ?: 0L,
                            lastDone = parts[4].toLongOrNull()?.let { Date(it) },
                            dueDate = parts[5].toLongOrNull()?.let { Date(it) },
                            comments = unescapeCsv(parts[6]).ifEmpty { null },
                            tags = unescapeCsv(parts[7]).split(",").filter { it.isNotEmpty() },
                            isActive = parts[8] == "1"
                        )
                        tasks.add(task)
                    }
                }
                TYPE_COMPLETION -> {
                    if (parts.size >= 4) {
                        val record = CompletionRecord(
                            id = parts[1].toIntOrNull() ?: 0,
                            taskId = parts[2].toIntOrNull() ?: 0,
                            completionTime = Date(parts[3].toLongOrNull() ?: 0L)
                        )
                        records.add(record)
                    }
                }
            }
        }

        return Pair(tasks, records)
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    private fun unescapeCsv(value: String): String {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length - 1).replace("\"\"", "\"")
        }
        return value
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    current.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString())
                current = StringBuilder()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}

