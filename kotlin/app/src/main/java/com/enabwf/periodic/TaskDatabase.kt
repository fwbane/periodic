package com.enabwf.periodic
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.enabwf.periodic.TaskDao

@Database(entities = [Task::class, CompletionRecord::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)  //  use converters Date<>Long in Converters.kt
abstract class TaskDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

         private val MIGRATION_1_2 = object : Migration(1, 2) {
             override fun migrate(db: SupportSQLiteDatabase) {
                 db.execSQL("ALTER TABLE task_table ADD COLUMN comments TEXT")
                 db.execSQL("ALTER TABLE task_table ADD COLUMN tags TEXT NOT NULL DEFAULT ''") // Default empty string for tags
             }
         }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the new 'isActive' column to task_table, defaulting to 1 (true) for existing tasks
                db.execSQL("ALTER TABLE task_table ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_table ADD COLUMN medianHistoryPeriod INTEGER")
                db.execSQL("ALTER TABLE task_table ADD COLUMN medianRecentPeriod INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rename duplicates to ensure uniqueness before creating index
                // Appending random suffix to duplicates
                db.execSQL("UPDATE task_table SET name = name || '_' || hex(randomblob(4)) WHERE name IN (SELECT name FROM task_table GROUP BY name HAVING COUNT(*) > 1)")
                
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_task_table_name ON task_table(name)")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_completion_table_taskId_completionTime " +
                        "ON completion_table(taskId, completionTime)"
                )
            }
        }

        fun getDatabase(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_database"
                )
                .fallbackToDestructiveMigration()
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
