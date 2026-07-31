# Periodic

Licensed under PolyForm Noncommercial License 
https://polyformproject.org/licenses/noncommercial/1.0.0

An Android app for tracking recurring tasks — things you need to do on a
repeating schedule (e.g. "change furnace filter every 3 months") without a
fixed calendar date. Periodic tracks completion history per task and uses it
to compute how well you're keeping to your intended schedule.

## Features

- **Task list** — add, edit, and archive recurring tasks, each with a name,
  a recurrence period (in hours/days/weeks/months/years), optional comments,
  and free-form tags.
- **Mark done** — complete a task now or backdate/backtime a completion via
  date and time pickers. Completing a task logs a history record and rolls
  the due date forward.
- **History** — browse all logged completions across every task.
- **Analytics** — a tabbed dashboard (Overview, Timeline, Adherence,
  Patterns) built on Vico charts:
  - Overview: completion counts, active task count, top completed tasks.
  - Timeline: completions over time, binned by hour/day/week/month, with a
    per-tag breakdown.
  - Adherence: box plots of how each task's actual completion intervals
    compare to its set period (early/on-schedule/late).
  - Patterns: completion activity by hour of day, day of week, and a
    day/hour heatmap.
  - All views support filtering by date range, tag, task, and archived
    status.
- **Backup/restore** — export the full task list and completion history to
  a CSV file, or import one back in (replacing the current database).

## Tech stack

- Kotlin, single-module Android app (`app`)
- Views + ViewBinding for most screens, with Jetpack Compose set up for
  newer UI (`androidx.compose`, Material 3)
- **Room** for persistence (`TaskDatabase`, currently schema version 6, with
  migrations from v1)
- **Coroutines** for async DB/IO work, **LiveData** + `ViewModel` for UI state
- **Vico** (`com.patrykandpatrick.vico`) for analytics charts
- Min SDK 24, target SDK 34, compile SDK 36

## Project layout

```
app/src/main/java/com/enabwf/periodic/
├── MainActivity.kt           # task list, add/edit/mark-done, settings, CSV export/import
├── HistoryActivity.kt        # completion history list
├── AnalyticsActivity.kt      # analytics screen (tabs + filters)
├── Analytics*.kt             # analytics calculator, models, chart config/adapters per tab
├── Task.kt, CompletionRecord.kt   # Room entities
├── TaskDao.kt, TaskDatabase.kt    # Room DAO + database/migrations
├── TaskRepository.kt              # data access layer
├── TaskViewModel*.kt, HistoryViewModel.kt  # ViewModels
├── PeriodCalculator.kt        # median completion-interval math
├── CsvHelper.kt               # CSV export/import (backup/restore)
└── ui/theme/                  # Compose theme (color, typography)
```

## Building

This is a standard Gradle-based Android project.

```bash
./gradlew assembleDebug     # build a debug APK
./gradlew installDebug      # build and install on a connected device/emulator
./gradlew test              # unit tests
./gradlew connectedAndroidTest  # instrumented tests
```

Or open the `kotlin/` directory in Android Studio and run the `app`
configuration.
