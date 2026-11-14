package app.forjadecolor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Version history: 1 = first schema. Every upgrade must keep existing rows (with a migration test);
 * never add a destructive fallback.
 */
@Database(
    entities = [
        PaintEntity::class, BrushEntity::class, ProjectEntity::class, ProgressPhotoEntity::class,
        AdviceEntity::class, BlobEntity::class, StateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ForjaDatabase : RoomDatabase() {
    abstract fun dao(): ForjaDao

    companion object {
        const val NAME = "forja.db"

        /** Stored in the default databases directory, which Android backup includes (`database` domain). */
        fun create(context: Context): ForjaDatabase =
            Room.databaseBuilder(context.applicationContext, ForjaDatabase::class.java, NAME).build()
    }
}
