package com.vatsyahub.workout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = false,
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun dao(): WorkoutDao

    companion object {
        fun create(context: Context): WorkoutDatabase =
            Room.databaseBuilder(context, WorkoutDatabase::class.java, "workout.db")
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
