package com.invincible.jedishare

import timber.log.Timber

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SharingApp: Application() {
    override fun onCreate() {
        Timber.d("SharingApp - onCreate called")
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}