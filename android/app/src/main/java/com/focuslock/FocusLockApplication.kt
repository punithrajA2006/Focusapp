package com.focuslock

import android.app.Application
import com.focuslock.data.repository.FocusLockRepository

class FocusLockApplication : Application() {

    lateinit var repository: FocusLockRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = FocusLockRepository(this)
    }
}
