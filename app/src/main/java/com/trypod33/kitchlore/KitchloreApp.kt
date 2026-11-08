package com.trypod33.kitchlore

import android.app.Application
import androidx.room.Room
import com.trypod33.kitchlore.data.DocTextExtractor
import com.trypod33.kitchlore.data.FirstRunImport
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.sync.FolderSyncManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class KitchloreApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "kitchlore.db")
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            DocTextExtractor.init(this@KitchloreApp)
            FirstRunImport.run(this@KitchloreApp, database)
            // When a folder is already picked, pull the other device's
            // library quietly so the first launch of the session stays live.
            if (FolderSyncManager.hasFolder(this@KitchloreApp)) {
                runCatching { FolderSyncManager.sync(this@KitchloreApp, database) }
            }
        }
    }
}
