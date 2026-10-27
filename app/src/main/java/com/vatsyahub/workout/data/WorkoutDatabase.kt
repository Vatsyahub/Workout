package com.vatsyahub.workout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    ],
    version = 3,
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

        fun create(context: Context): WorkoutDatabase =
            Room.databaseBuilder(context, WorkoutDatabase::class.java, "workout.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "INSERT INTO profile (id, name, age, weightKg, heightCm, avatar) " +
                                "VALUES (1, '', NULL, NULL, NULL, NULL)",
                        )
                    }
                })
                .build()
    }
}
