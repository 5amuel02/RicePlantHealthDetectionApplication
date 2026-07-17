package com.example.percobaan1957

import android.content.Context

/**
 * Sumber tunggal konfigurasi server Flask (IP, port, preferensi).
 *
 * Sebelumnya IP disimpan terpisah di dua key ("flask_ip" untuk Home/Kamera dan
 * "flask_ip_result" untuk Result) dan port 5000 ditulis hardcode di banyak tempat.
 * Semua halaman sekarang membaca/menulis lewat objek ini supaya konsisten dan
 * cukup diatur satu kali di halaman Pengaturan.
 */
object ServerConfig {

    private const val PREFS = "settings"
    private const val KEY_IP = "flask_ip"
    private const val KEY_PORT = "flask_port"
    private const val KEY_AUTO_CONNECT = "auto_connect_stream"

    const val DEFAULT_PORT = 5000

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getIp(context: Context): String =
        prefs(context).getString(KEY_IP, "")?.trim().orEmpty()

    fun getPort(context: Context): Int =
        prefs(context).getInt(KEY_PORT, DEFAULT_PORT)

    fun isAutoConnect(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CONNECT, true)

    /** True kalau IP sudah diisi (port selalu punya default). */
    fun isConfigured(context: Context): Boolean = getIp(context).isNotEmpty()

    fun save(context: Context, ip: String, port: Int, autoConnect: Boolean) {
        prefs(context).edit()
            .putString(KEY_IP, ip.trim())
            .putInt(KEY_PORT, port)
            .putBoolean(KEY_AUTO_CONNECT, autoConnect)
            .apply()
    }

    /** "host:port", dipakai untuk ditampilkan ke pengguna. */
    fun address(context: Context): String = "${getIp(context)}:${getPort(context)}"

    fun baseUrl(context: Context): String = "http://${getIp(context)}:${getPort(context)}"
    fun videoUrl(context: Context): String = "${baseUrl(context)}/video"
    fun predictUrl(context: Context): String = "${baseUrl(context)}/predict"
    fun documentsUrl(context: Context): String = "${baseUrl(context)}/documents"

    /** URL status server (endpoint "/") untuk uji koneksi. */
    fun statusUrl(ip: String, port: Int): String = "http://${ip.trim()}:$port/"
}
