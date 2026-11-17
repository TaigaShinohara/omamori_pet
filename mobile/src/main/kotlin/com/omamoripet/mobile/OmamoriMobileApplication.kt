package com.omamoripet.mobile

import android.app.Application
import com.omamoripet.mobile.data.MobileRepository

class OmamoriMobileApplication : Application() {
    lateinit var repository: MobileRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = MobileRepository(applicationContext)
    }
}
