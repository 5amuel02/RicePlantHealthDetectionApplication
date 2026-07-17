package com.example.percobaan1957

import android.app.Application

/**
 * Application class — menerapkan preferensi tema (mode gelap) sedini mungkin
 * agar konsisten di seluruh activity sejak aplikasi dibuka.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPrefs.applyTheme(this)
    }
}
