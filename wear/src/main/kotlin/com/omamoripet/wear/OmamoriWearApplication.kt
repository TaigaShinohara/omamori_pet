package com.omamoripet.wear

import android.app.Application
import com.omamoripet.wear.data.WatchStateRepository

class OmamoriWearApplication : Application() {
    lateinit var repository: WatchStateRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = WatchStateRepository(applicationContext)
    }
}
