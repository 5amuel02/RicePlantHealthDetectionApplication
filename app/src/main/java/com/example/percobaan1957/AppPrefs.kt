package com.example.percobaan1957

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Preferensi tampilan aplikasi (terpisah dari [ServerConfig] yang khusus server).
 * Saat ini menyimpan pilihan mode gelap.
 */
object AppPrefs {

    private const val PREFS = "settings"
    private const val KEY_DARK = "dark_mode"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDarkMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DARK, false)

    fun setDarkMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK, enabled).apply()
    }

    /** Terapkan tema sesuai preferensi tersimpan. AppCompat akan me-recreate
     *  activity yang sedang aktif bila nilainya berubah. */
    fun applyTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode(context)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
