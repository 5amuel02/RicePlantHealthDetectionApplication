package com.example.percobaan1957

import android.content.Intent
import android.os.Bundle
import android.text.util.Linkify
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.bumptech.glide.Glide
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class ResultActivity : AppCompatActivity() {

    private val client = OkHttpClient()

    private lateinit var tvJsonRaw: TextView
    private lateinit var resultContainer: LinearLayout
    private lateinit var cleanContainer: LinearLayout
    private lateinit var tvResultServerInfo: TextView
    private lateinit var btnAmbilData: Button
    private lateinit var btnResultSettings: Button
    private lateinit var messageText: TextView

    // 🆕 Tambahan variabel untuk hitung response time
    private var startTime: Long = 0 // 🆕

    // 🔹 Navbar
    private lateinit var menuHome: ImageButton
    private lateinit var menuResult: ImageButton
    private lateinit var menuProfile: ImageButton
    private lateinit var menuAdvance: ImageButton
    private lateinit var navCamera: ImageButton

    // Alamat server yang terakhir diambil datanya (untuk refetch saat config berubah)
    private var lastFetchedAddr = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        tvJsonRaw = findViewById(R.id.tvJsonRaw)
        resultContainer = findViewById(R.id.resultContainer)
        cleanContainer = findViewById(R.id.cleanContainer)
        tvResultServerInfo = findViewById(R.id.tvResultServerInfo)
        btnAmbilData = findViewById(R.id.btnAmbilData)
        btnResultSettings = findViewById(R.id.btnResultSettings)
        messageText = TextView(this)

        val titleText = findViewById<LinearLayout>(R.id.resultContainer).parent as LinearLayout
        val hasilIndex = titleText.indexOfChild(findViewById(R.id.titleText))
        messageText.apply {
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@ResultActivity, android.R.color.holo_red_dark))
            visibility = View.GONE
            typeface = ResourcesCompat.getFont(this@ResultActivity, R.font.montserrat_medium)
            setPadding(0, 4, 0, 12)
        }
        titleText.addView(messageText, hasilIndex + 1)

        menuHome = findViewById(R.id.menuHome)
        menuResult = findViewById(R.id.menuResult)
        menuProfile = findViewById(R.id.menuProfile)
        menuAdvance = findViewById(R.id.menuAdvance)
        navCamera = findViewById(R.id.nav_camera)

        setupNavbar()

        btnAmbilData.setOnClickListener {
            if (ServerConfig.isConfigured(this)) {
                ambilDataDariFlask()
            } else {
                Toast.makeText(this, "Atur alamat server di Pengaturan dulu", Toast.LENGTH_SHORT).show()
                openActivity(SettingsActivity::class.java)
            }
        }

        btnResultSettings.setOnClickListener { openActivity(SettingsActivity::class.java) }
    }

    override fun onResume() {
        super.onResume()
        updateServerInfo()
    }

    // Perbarui label server & ambil data otomatis saat pertama kali / konfigurasi berubah
    private fun updateServerInfo() {
        if (!ServerConfig.isConfigured(this)) {
            tvResultServerInfo.text = "Server belum diatur"
            tvJsonRaw.text = "Atur IP Flask di Pengaturan untuk mengambil hasil deteksi"
            lastFetchedAddr = ""
            return
        }

        val addr = ServerConfig.address(this)
        tvResultServerInfo.text = "Server: $addr"
        if (addr != lastFetchedAddr) {
            ambilDataDariFlask()
        }
    }

    private fun setupNavbar() {
        menuHome.setOnClickListener { openActivity(MainActivity::class.java) }
        navCamera.setOnClickListener { openActivity(CameraActivity::class.java) }
        menuResult.setOnClickListener {
            Toast.makeText(this, "Kamu sudah di halaman Result", Toast.LENGTH_SHORT).show()
        }
        menuAdvance.setOnClickListener { openActivity(SettingsActivity::class.java) }
        menuProfile.setOnClickListener { openActivity(ProfileActivity::class.java) }
    }

    // ====================================================
    // 🆕 Hitung Response Time & Simpan CSV
    // ====================================================
    private fun simpanKeCSV(responseTime: Long) { // 🆕
        val timestamp = System.currentTimeMillis()
        val line = "$timestamp,$responseTime\n"
        openFileOutput("response_time_log.csv", MODE_APPEND)
            .write(line.toByteArray())
    }

    // ====================================================
    // 🔹 Ambil Data dari Flask
    // ====================================================
    private fun ambilDataDariFlask() {
        if (!ServerConfig.isConfigured(this)) {
            tvJsonRaw.text = "Atur IP Flask di Pengaturan untuk mengambil hasil deteksi"
            return
        }

        val url = ServerConfig.documentsUrl(this)
        lastFetchedAddr = ServerConfig.address(this)
        tvJsonRaw.text = "Mengambil data dari: $url"
        messageText.visibility = View.GONE

        // 🆕 Mulai hitung waktu
        startTime = System.currentTimeMillis()

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvJsonRaw.text = "Gagal mengambil data: ${e.message}"
                    tampilkanPesanBawahJudul(
                        "Tidak dapat terhubung ke server Flask. Periksa koneksi atau IP.",
                        R.drawable.ic_warning
                    )
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val endTime = System.currentTimeMillis() // 🆕
                val responseTime = endTime - startTime // 🆕

                runOnUiThread {
                    Toast.makeText(
                        this@ResultActivity,
                        "Response time: ${responseTime} ms",
                        Toast.LENGTH_LONG
                    ).show()
                }
                simpanKeCSV(responseTime) // 🆕

                val body = response.body?.string()
                runOnUiThread {
                    if (body.isNullOrEmpty()) {
                        tvJsonRaw.text = "Data kosong dari server"
                        tampilkanPesanBawahJudul(
                            "Data kosong. Server Flask belum mengirim hasil deteksi.",
                            R.drawable.ic_inbox,
                            R.color.text_muted
                        )
                        return@runOnUiThread
                    }

                    tvJsonRaw.text = body
                    try {
                        val jsonObject = JSONObject(body)
                        val jsonArray = jsonObject.getJSONArray("data")

                        resultContainer.removeAllViews()
                        if (jsonArray.length() == 0) {
                            tampilkanPesanBawahJudul(
                                "Tidak ditemukan padi yang sakit.",
                                R.drawable.ic_check_circle,
                                R.color.status_success
                            )
                            cleanContainer.visibility = View.GONE
                            resultContainer.visibility = View.GONE
                            return@runOnUiThread
                        }

                        val dataTerinfeksi = mutableListOf<JSONObject>()

                        for (i in 0 until jsonArray.length()) {
                            val item = jsonArray.getJSONObject(i)
                            val mainData =
                                if (item.has("data") && item.opt("data") is JSONObject)
                                    item.getJSONObject("data")
                                else item

                            val status = mainData.optString("status", "").lowercase()
                            if (status.contains("infeksi") || status.contains("terinfeksi")) {
                                dataTerinfeksi.add(mainData)
                            }
                        }

                        if (dataTerinfeksi.isNotEmpty()) {
                            cleanContainer.visibility = View.GONE
                            resultContainer.visibility = View.VISIBLE
                            messageText.visibility = View.GONE

                            for (data in dataTerinfeksi) {
                                tampilkanKartuTerinfeksi(
                                    data.optString("label", "Tidak diketahui"),
                                    data.optDouble("confidence", 0.0),
                                    data.optString("timestamp", "-"),
                                    data.optString("image_url", ""),
                                    data.optString("source", "-")
                                )
                            }

                            tampilkanPesanBawahJudul(
                                "Ditemukan tanaman terinfeksi!",
                                R.drawable.ic_alert,
                                R.color.status_danger
                            )

                        } else {
                            resultContainer.visibility = View.GONE
                            cleanContainer.visibility = View.GONE
                            tampilkanPesanBawahJudul(
                                "Semua tanaman tampak sehat.",
                                R.drawable.ic_check_circle,
                                R.color.status_success
                            )
                        }

                    } catch (e: Exception) {
                        tvJsonRaw.text = "Gagal parsing JSON: ${e.message}"
                        tampilkanPesanBawahJudul(
                            "Format data tidak sesuai.",
                            R.drawable.ic_warning,
                            R.color.status_warning
                        )
                    }
                }
            }
        })
    }

    private fun tampilkanPesanBawahJudul(
        pesan: String,
        iconRes: Int = 0,
        colorRes: Int = R.color.text_secondary
    ) {
        messageText.text = pesan
        messageText.setTextColor(ContextCompat.getColor(this, colorRes))
        if (iconRes != 0) {
            messageText.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
            messageText.compoundDrawablePadding = 20
        } else {
            messageText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }
        messageText.visibility = View.VISIBLE
    }

    private fun tampilkanKartuTerinfeksi(
        label: String,
        confidence: Double,
        timestamp: String,
        imageUrl: String,
        source: String
    ) {
        val context = this
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            background = ContextCompat.getDrawable(context, R.drawable.bg_rounded_box)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 32, 0, 32) }
            elevation = 8f
        }

        val title = TextView(context).apply {
            text = "Ditemukan Tanaman Terinfeksi"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.status_danger))
            typeface = ResourcesCompat.getFont(context, R.font.montserrat_semibold)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_alert, 0, 0, 0)
            compoundDrawablePadding = 16
            setPadding(0, 0, 0, 12)
        }

        val info = TextView(context).apply {
            text = """
                Waktu Deteksi: $timestamp
                Label: $label
                Akurasi: ${(confidence * 100).toInt()}%
                Sumber: $source
            """.trimIndent()
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            typeface = ResourcesCompat.getFont(context, R.font.montserrat_regular)
            setLineSpacing(8f, 1f)
            letterSpacing = 0.02f
            autoLinkMask = Linkify.WEB_URLS
        }

        if (imageUrl.isNotEmpty()) {
            val imgView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    500
                ).apply { setMargins(0, 24, 0, 24) }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            Glide.with(context)
                .load(imageUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(imgView)

            cardLayout.addView(imgView)
        }

        cardLayout.addView(title)
        cardLayout.addView(info)
        resultContainer.addView(cardLayout)
    }

    private fun tampilkanKartuSehat() {
        resultContainer.visibility = View.GONE
        cleanContainer.visibility = View.VISIBLE
    }

    private fun <T> openActivity(activityClass: Class<T>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
