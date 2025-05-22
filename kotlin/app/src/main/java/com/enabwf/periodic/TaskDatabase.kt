package com.enabwf.periodic
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.enabwf.periodic.TaskDao

@Database(entities = [Task::class, CompletionRecord::class], version = 3, exportSchema = false)
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
        fun getDatabase(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_database"
                )
                .fallbackToDestructiveMigration()
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
