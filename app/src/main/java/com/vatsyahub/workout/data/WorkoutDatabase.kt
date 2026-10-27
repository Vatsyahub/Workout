package com.vatsyahub.workout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vatsyahub.workout.domain.musclesWithoutMovingHandleArms

@Database(
    entities = [
        Profile::class,
        Exercise::class,
        WorkoutType::class,
        ExerciseType::class,
        ActivityKind::class,
        ActivityField::class,
        HiitPattern::class,
        HiitPass::class,
        Workout::class,
        Block::class,
        SetRow::class,
        HiitSetRow::class,
        Split::class,
        FieldValue::class,
        ActiveSession::class,
        SavedPlan::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun dao(): WorkoutDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN favourite INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN warmupSec INTEGER")
                db.execSQL("ALTER TABLE workouts ADD COLUMN cooldownSec INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE profile ADD COLUMN appearance TEXT NOT NULL DEFAULT 'LIGHT'",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE active_session ADD COLUMN payload TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS plans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        kind TEXT NOT NULL,
                        title TEXT NOT NULL,
                        savedAt INTEGER NOT NULL,
                        body TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query(
                    """
                    SELECT e.id AS id, e.name AS name, e.bodyPart AS bodyPart, e.usage AS usage,
                        IFNULL((
                            SELECT group_concat(t.name, ', ')
                            FROM exercise_types l
                            JOIN workout_types t ON t.id = l.typeId
                            WHERE l.exerciseId = e.id
                        ), '') AS types
                    FROM exercises e
                    """.trimIndent(),
                )
                val updates = ArrayList<Pair<Long, String>>()
                cursor.use { rows ->
                    val idCol = rows.getColumnIndexOrThrow("id")
                    val nameCol = rows.getColumnIndexOrThrow("name")
                    val partCol = rows.getColumnIndexOrThrow("bodyPart")
                    val useCol = rows.getColumnIndexOrThrow("usage")
                    val typeCol = rows.getColumnIndexOrThrow("types")
                    while (rows.moveToNext()) {
                        val body = rows.getString(partCol).orEmpty()
                        val next = musclesWithoutMovingHandleArms(
                            body,
                            rows.getString(nameCol).orEmpty(),
                            rows.getString(typeCol).orEmpty(),
                            rows.getString(useCol).orEmpty(),
                        )
                        if (next != body) updates += rows.getLong(idCol) to next
                    }
                }
                for ((id, body) in updates) {
                    db.execSQL(
                        "UPDATE exercises SET bodyPart = ? WHERE id = ?",
                        arrayOf<Any>(body, id),
                    )
                }
            }
        }

        fun create(context: Context): WorkoutDatabase =
            Room.databaseBuilder(context, WorkoutDatabase::class.java, "workout.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "INSERT INTO profile (id, name, age, weightKg, heightCm, avatar, appearance) " +
                                "VALUES (1, '', NULL, NULL, NULL, NULL, 'LIGHT')",
                        )
                    }
                })
                .build()
    }
}
