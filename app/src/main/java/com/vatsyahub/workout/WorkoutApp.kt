package com.vatsyahub.workout

import android.app.Application
import com.vatsyahub.workout.data.Repository
import com.vatsyahub.workout.data.WorkoutDatabase

class WorkoutApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = WorkoutDatabase.create(this)
        repository = Repository(database)
    }
}
