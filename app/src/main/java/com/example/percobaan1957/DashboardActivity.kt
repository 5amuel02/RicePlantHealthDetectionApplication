package com.example.percobaan1957

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Halaman Dashboard statistik. Mengambil hasil deteksi dari endpoint yang sama
 * dengan Result (`/documents`) lalu meringkasnya: total pemindaian, sehat vs
 * terinfeksi, distribusi penyakit, dan ringkasan waktu respons dari log lokal.
 */
class DashboardActivity : AppCompatActivity() {

    private val client = OkHttpClient()

    private lateinit var tvDashServerInfo: TextView
    private lateinit var btnDashRefresh: Button
    private lateinit var tvDashMessage: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvHealthy: TextView
    private lateinit var tvInfected: TextView
    private lateinit var tvHealthPct: TextView
    private lateinit var healthBarTrack: LinearLayout
    private lateinit var barHealthy: View
    private lateinit var barInfected: View
    private lateinit var diseaseContainer: LinearLayout
    private lateinit var tvDiseaseEmpty: TextView
    private lateinit var tvRespSummary: TextView

    private val logFileName = "response_time_log.csv"
    private var lastFetchedAddr = ""

    // Bar penyakit + bobot targetnya, dianimasikan mengisi dari 0.
    private val diseaseBars = mutableListOf<Pair<View, Float>>()
    private var statsAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        tvDashServerInfo = findViewById(R.id.tvDashServerInfo)
        btnDashRefresh = findViewById(R.id.btnDashRefresh)
        tvDashMessage = findViewById(R.id.tvDashMessage)
        tvTotal = findViewById(R.id.tvTotal)
        tvHealthy = findViewById(R.id.tvHealthy)
        tvInfected = findViewById(R.id.tvInfected)
        tvHealthPct = findViewById(R.id.tvHealthPct)
        healthBarTrack = findViewById(R.id.healthBarTrack)
        barHealthy = findViewById(R.id.barHealthy)
        barInfected = findViewById(R.id.barInfected)
        diseaseContainer = findViewById(R.id.diseaseContainer)
        tvDiseaseEmpty = findViewById(R.id.tvDiseaseEmpty)
        tvRespSummary = findViewById(R.id.tvRespSummary)

        // Ujung bar membulat: klip anak-anaknya ke outline track.
        healthBarTrack.clipToOutline = true

        btnDashRefresh.setOnClickListener {
            if (ServerConfig.isConfigured(this)) {
                fetchStats()
            } else {
                openActivity(SettingsActivity::class.java)
            }
        }

        setupNavbar()
    }

    override fun onResume() {
        super.onResume()
        refreshResponseSummary()

        if (!ServerConfig.isConfigured(this)) {
            tvDashServerInfo.text = "Server belum diatur"
            showMessage("Atur alamat server di Pengaturan untuk melihat statistik.", R.color.status_warning)
            resetStats()
            lastFetchedAddr = ""
            return
        }

        val addr = ServerConfig.address(this)
        tvDashServerInfo.text = "Server: $addr"
        // Ambil ulang hanya saat pertama atau saat konfigurasi berubah.
        if (addr != lastFetchedAddr) fetchStats()
    }

    private fun fetchStats() {
        if (!ServerConfig.isConfigured(this)) return
        val addr = ServerConfig.address(this)
        lastFetchedAddr = addr
        tvDashServerInfo.text = "Server: $addr"
        showMessage("Memuat statistik…", R.color.text_secondary)

        val request = Request.Builder().url(ServerConfig.documentsUrl(this)).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showMessage("Gagal terhubung ke server. Periksa koneksi/IP.", R.color.status_danger)
                    resetStats()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                response.close()
                runOnUiThread {
                    if (body.isNullOrBlank()) {
                        showMessage("Server belum mengirim data deteksi.", R.color.text_muted)
                        resetStats()
                        return@runOnUiThread
                    }
                    try {
                        renderStats(JSONObject(body))
                    } catch (e: Exception) {
                        showMessage("Format data tidak sesuai: ${e.message}", R.color.status_warning)
                        resetStats()
                    }
                }
            }
        })
    }

    private fun renderStats(json: JSONObject) {
        val arr = json.getJSONArray("data")
        val total = arr.length()
        var infected = 0
        val diseaseCounts = LinkedHashMap<String, Int>()

        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val main = if (item.has("data") && item.opt("data") is JSONObject)
                item.getJSONObject("data") else item

            val status = main.optString("status", "").lowercase()
            val isInfected = status.contains("infeksi") || status.contains("terinfeksi")
            if (isInfected) {
                infected++
                val label = main.optString("label", "Tidak diketahui").ifBlank { "Tidak diketahui" }
                diseaseCounts[label] = (diseaseCounts[label] ?: 0) + 1
            }
        }
        val healthy = (total - infected).coerceAtLeast(0)

        // Persentase kesehatan (teks langsung, warna sesuai ambang)
        healthBarTrack.weightSum = if (total > 0) total.toFloat() else 1f
        if (total > 0) {
            val pct = healthy * 100 / total
            tvHealthPct.text = "$pct%"
            val color = when {
                pct >= 70 -> R.color.status_success
                pct >= 40 -> R.color.status_warning
                else -> R.color.status_danger
            }
            tvHealthPct.setTextColor(ContextCompat.getColor(this, color))
        } else {
            tvHealthPct.text = "–"
            tvHealthPct.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        }

        // Bangun bar penyakit (bobot 0) lalu animasikan angka & bar mengisi dari 0.
        renderDiseaseBars(diseaseCounts)
        animateStats(total, healthy, infected)

        when {
            total == 0 -> showMessage("Belum ada pemindaian yang tercatat di server.", R.color.text_muted)
            infected == 0 -> showMessage("Semua tanaman tampak sehat. Bagus!", R.color.status_success)
            else -> hideMessage()
        }
    }

    private fun renderDiseaseBars(counts: Map<String, Int>) {
        diseaseContainer.removeAllViews()
        diseaseBars.clear()
        if (counts.isEmpty()) {
            tvDiseaseEmpty.visibility = View.VISIBLE
            diseaseContainer.visibility = View.GONE
            return
        }
        tvDiseaseEmpty.visibility = View.GONE
        diseaseContainer.visibility = View.VISIBLE

        val sorted = counts.entries.sortedByDescending { it.value }
        val max = sorted.first().value

        for ((index, entry) in sorted.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (index > 0) topMargin = dp(14) }
            }

            val labelRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val labelView = TextView(this).apply {
                text = entry.key
                typeface = ResourcesCompat.getFont(this@DashboardActivity, R.font.montserrat_semibold)
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.text_primary))
                textSize = 14f
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val countView = TextView(this).apply {
                text = entry.value.toString()
                typeface = ResourcesCompat.getFont(this@DashboardActivity, R.font.montserrat_bold)
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.status_danger))
                textSize = 14f
                includeFontPadding = false
            }
            labelRow.addView(labelView)
            labelRow.addView(countView)

            val track = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8)
                ).apply { topMargin = dp(6) }
                background = ContextCompat.getDrawable(this@DashboardActivity, R.drawable.bg_bar_rounded)
                backgroundTintList = ContextCompat.getColorStateList(this@DashboardActivity, R.color.stroke_light)
                weightSum = max.toFloat()
                clipToOutline = true
            }
            val bar = View(this).apply {
                // Mulai dari 0, diisi oleh animateStats().
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0f)
                setBackgroundColor(ContextCompat.getColor(this@DashboardActivity, R.color.status_danger))
            }
            track.addView(bar)
            diseaseBars.add(bar to entry.value.toFloat())

            row.addView(labelRow)
            row.addView(track)
            diseaseContainer.addView(row)
        }
    }

    // Angka count-up + bar (kesehatan & penyakit) mengisi dari 0 secara bersamaan.
    private fun animateStats(total: Int, healthy: Int, infected: Int) {
        statsAnimator?.cancel()
        val bars = diseaseBars.toList()
        statsAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val f = va.animatedValue as Float
                tvTotal.text = (total * f).toInt().toString()
                tvHealthy.text = (healthy * f).toInt().toString()
                tvInfected.text = (infected * f).toInt().toString()
                setBarWeight(barHealthy, healthy * f)
                setBarWeight(barInfected, infected * f)
                for ((bar, target) in bars) setBarWeight(bar, target * f)
            }
            doOnEndSnapValues(total, healthy, infected, bars)
            start()
        }
    }

    // Pastikan nilai akhir tepat (hindari pembulatan animasi di frame terakhir).
    private fun ValueAnimator.doOnEndSnapValues(
        total: Int, healthy: Int, infected: Int, bars: List<Pair<View, Float>>
    ) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                tvTotal.text = total.toString()
                tvHealthy.text = healthy.toString()
                tvInfected.text = infected.toString()
                setBarWeight(barHealthy, healthy.toFloat())
                setBarWeight(barInfected, infected.toFloat())
                for ((bar, target) in bars) setBarWeight(bar, target)
            }
        })
    }

    private fun setBarWeight(view: View, weight: Float) {
        val lp = view.layoutParams as LinearLayout.LayoutParams
        lp.weight = weight
        view.layoutParams = lp
    }

    // Ringkasan waktu respons dari response_time_log.csv ("timestamp,ms")
    private fun refreshResponseSummary() {
        val file = File(filesDir, logFileName)
        val times = if (file.exists()) {
            file.readLines().mapNotNull { it.split(",").getOrNull(1)?.trim()?.toLongOrNull() }
        } else emptyList()

        tvRespSummary.text = if (times.isEmpty()) {
            "Belum ada data waktu respons"
        } else {
            val avg = times.average().toInt()
            val min = times.minOrNull() ?: 0L
            val max = times.maxOrNull() ?: 0L
            "${times.size} pengukuran • rata-rata $avg ms\nTercepat $min ms • Terlama $max ms"
        }
    }

    private fun resetStats() {
        statsAnimator?.cancel()
        tvTotal.text = "0"
        tvHealthy.text = "0"
        tvInfected.text = "0"
        tvHealthPct.text = "–"
        tvHealthPct.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        setBarWeight(barHealthy, 0f)
        setBarWeight(barInfected, 0f)
        healthBarTrack.weightSum = 1f
        renderDiseaseBars(emptyMap())
    }

    private fun showMessage(text: String, colorRes: Int) {
        tvDashMessage.text = text
        tvDashMessage.setTextColor(ContextCompat.getColor(this, colorRes))
        tvDashMessage.visibility = View.VISIBLE
    }

    private fun hideMessage() {
        tvDashMessage.visibility = View.GONE
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun setupNavbar() {
        findViewById<ImageButton>(R.id.nav_home).setOnClickListener { openActivity(MainActivity::class.java) }
        findViewById<ImageButton>(R.id.nav_camera).setOnClickListener { openActivity(CameraActivity::class.java) }
        findViewById<ImageButton>(R.id.nav_result).setOnClickListener { openActivity(ResultActivity::class.java) }
        findViewById<ImageButton>(R.id.nav_settings).setOnClickListener { openActivity(SettingsActivity::class.java) }
        findViewById<ImageButton>(R.id.nav_profile).setOnClickListener { openActivity(ProfileActivity::class.java) }
    }

    private fun <T> openActivity(activityClass: Class<T>) {
        startActivity(Intent(this, activityClass))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
