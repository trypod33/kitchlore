package com.trypod33.kitchlore

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.room.Room
import com.trypod33.kitchlore.data.FirstRunImport
import com.trypod33.kitchlore.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class KitchloreApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "kitchlore.db").build()
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            FirstRunImport.run(this@KitchloreApp, database)
        }
    }
}
