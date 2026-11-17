package app.forjadecolor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version history: 1 = first schema; 2 = advice_entries.doneZonesJson ([MIGRATION_1_2]); 3 = projects.style and baseSetting ([MIGRATION_2_3]). Every upgrade must keep existing rows (with a migration test);
 * never add a destructive fallback.
 */
@Database(
    entities = [
        PaintEntity::class, BrushEntity::class, ProjectEntity::class, ProgressPhotoEntity::class,
        AdviceEntity::class, BlobEntity::class, StateEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ForjaDatabase : RoomDatabase() {
    abstract fun dao(): ForjaDao

    companion object {
        const val NAME = "forja.db"

        /** Stored in the default databases directory, which Android backup includes (`database` domain). */
        fun create(context: Context, name: String = NAME): ForjaDatabase =
            Room.databaseBuilder(context.applicationContext, ForjaDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        /** v2: the zones ticked off on each advice entry; existing rows start with none done. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `advice_entries` ADD COLUMN `doneZonesJson` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** v3: each project's painting style and base setting; existing projects start with none (my method). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `style` TEXT")
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `baseSetting` TEXT")
            }
        }
    }
}
