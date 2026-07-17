package com.example.percobaan1957

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Halaman pusat pengaturan koneksi server. IP + port disimpan lewat [ServerConfig]
 * sehingga Home, Result, dan Kamera cukup membaca dari satu tempat.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var inputServerIp: EditText
    private lateinit var inputServerPort: EditText
    private lateinit var switchAutoConnect: SwitchCompat
    private lateinit var switchDarkMode: SwitchCompat
    private lateinit var btnTestConnection: Button
    private lateinit var btnSaveSettings: Button
    private lateinit var tvConnStatus: TextView
    private lateinit var tvLogSummary: TextView
    private lateinit var btnClearLog: Button
    private lateinit var tvVersion: TextView

    private val logFileName = "response_time_log.csv"

    // Timeout pendek supaya tes koneksi tidak menggantung lama.
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        inputServerIp = findViewById(R.id.inputServerIp)
        inputServerPort = findViewById(R.id.inputServerPort)
        switchAutoConnect = findViewById(R.id.switchAutoConnect)
        switchDarkMode = findViewById(R.id.switchDarkMode)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        tvConnStatus = findViewById(R.id.tvConnStatus)
        tvLogSummary = findViewById(R.id.tvLogSummary)
        btnClearLog = findViewById(R.id.btnClearLog)
        tvVersion = findViewById(R.id.tvVersion)

        // Isi nilai tersimpan
        inputServerIp.setText(ServerConfig.getIp(this))
        inputServerPort.setText(ServerConfig.getPort(this).toString())
        switchAutoConnect.isChecked = ServerConfig.isAutoConnect(this)

        // Mode gelap — set state dulu, baru pasang listener agar init tak memicu recreate.
        switchDarkMode.isChecked = AppPrefs.isDarkMode(this)
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            AppPrefs.setDarkMode(this, isChecked)
            AppPrefs.applyTheme(this) // AppCompat me-recreate activity untuk menerapkan tema
        }

        btnTestConnection.setOnClickListener { testConnection() }
        btnSaveSettings.setOnClickListener { saveSettings() }
        btnClearLog.setOnClickListener { confirmClearLog() }

        showVersion()
        refreshLogSummary()

        setupNavbar()
    }

    private fun showVersion() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
        tvVersion.text = "Versi ${version ?: "-"}"
    }

    // Ringkasan dari response_time_log.csv (baris: "timestamp,responseTimeMs")
    private fun refreshLogSummary() {
        val file = File(filesDir, logFileName)
        val times = if (file.exists()) {
            file.readLines().mapNotNull { it.split(",").getOrNull(1)?.trim()?.toLongOrNull() }
        } else {
            emptyList()
        }

        if (times.isEmpty()) {
            tvLogSummary.text = "Belum ada data waktu respons"
            btnClearLog.isEnabled = false
            return
        }

        val avg = times.average().toInt()
        val min = times.minOrNull() ?: 0L
        val max = times.maxOrNull() ?: 0L
        tvLogSummary.text = "${times.size} pengukuran • rata-rata $avg ms\nTercepat $min ms • Terlama $max ms"
        btnClearLog.isEnabled = true
    }

    private fun confirmClearLog() {
        AlertDialog.Builder(this)
            .setTitle("Hapus log?")
            .setMessage("Semua catatan waktu respons akan dihapus dan tidak bisa dikembalikan.")
            .setPositiveButton("Hapus") { _, _ ->
                deleteFile(logFileName)
                refreshLogSummary()
                Toast.makeText(this, "Log dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun currentIp(): String = inputServerIp.text.toString().trim()

    /** Port dari input; kosong/tidak valid dianggap default. */
    private fun currentPort(): Int {
        val raw = inputServerPort.text.toString().trim().toIntOrNull()
        return if (raw != null && raw in 1..65535) raw else ServerConfig.DEFAULT_PORT
    }

    private fun saveSettings() {
        val ip = currentIp()
        if (ip.isEmpty()) {
            Toast.makeText(this, "Alamat IP tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }
        val port = currentPort()
        // Normalkan tampilan port kalau tadinya kosong/tidak valid.
        inputServerPort.setText(port.toString())

        ServerConfig.save(this, ip, port, switchAutoConnect.isChecked)
        Toast.makeText(this, "Pengaturan disimpan ($ip:$port)", Toast.LENGTH_SHORT).show()
    }

    private fun testConnection() {
        val ip = currentIp()
        if (ip.isEmpty()) {
            Toast.makeText(this, "Isi alamat IP dulu", Toast.LENGTH_SHORT).show()
            return
        }
        val port = currentPort()
        setStatus("Menguji koneksi…", R.color.text_secondary)
        btnTestConnection.isEnabled = false

        val request = Request.Builder().url(ServerConfig.statusUrl(ip, port)).build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    btnTestConnection.isEnabled = true
                    setStatus("Gagal terhubung ke $ip:$port", R.color.status_danger)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val ok = response.isSuccessful
                response.close()
                runOnUiThread {
                    btnTestConnection.isEnabled = true
                    if (ok) {
                        setStatus("Server aktif di $ip:$port", R.color.status_success)
                    } else {
                        setStatus("Server membalas HTTP ${response.code}", R.color.status_warning)
                    }
                }
            }
        })
    }

    private fun setStatus(text: String, colorRes: Int) {
        tvConnStatus.text = text
        tvConnStatus.setTextColor(ContextCompat.getColor(this, colorRes))
        tvConnStatus.visibility = View.VISIBLE
    }

    private fun setupNavbar() {
        findViewById<ImageButton>(R.id.nav_home).setOnClickListener { openActivity(MainActivity::class.java) }
        findViewById<ImageButton>(R.id.nav_camera).setOnClickListener { openActivity(CameraActivity::class.java) }
        findViewById<ImageButton>(R.id.nav_result).setOnClickListener { openActivity(ResultActivity::class.java) }
        findViewById<ImageButton>(R.id.nav_settings).setOnClickListener {
            Toast.makeText(this, "Kamu sudah di halaman Pengaturan", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageButton>(R.id.nav_profile).setOnClickListener { openActivity(ProfileActivity::class.java) }
    }

    private fun <T> openActivity(activityClass: Class<T>) {
        startActivity(Intent(this, activityClass))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
