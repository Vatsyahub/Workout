package com.vatsyahub.workout

import android.app.Application
import com.vatsyahub.workout.data.Repository
import com.vatsyahub.workout.data.WorkoutDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WorkoutApp : Application() {
    lateinit var repository: Repository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val database = WorkoutDatabase.create(this)
        repository = Repository(database)
        appScope.launch { repository.seedIfEmpty() }
    }
}
