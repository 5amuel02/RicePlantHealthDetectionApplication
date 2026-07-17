package com.example.percobaan1957

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var refreshButton: Button
    private lateinit var btnViewResult: Button
    private lateinit var tvServerInfo: TextView
    private lateinit var btnConnectIp: Button
    private lateinit var btnOpenSettings: Button
    private lateinit var btnDashboard: Button
    private lateinit var imgMascot: ImageView
    private var bobAnimator: ObjectAnimator? = null

    // Terminal sapaan interaktif
    private lateinit var tvTerminal: TextView
    private lateinit var terminalCard: View
    private val terminalMessages = listOf(
        "> Halo, selamat datang!",
        "> Sistem RA-ATAP siap memantau.",
        "> Drone standby, menunggu perintah...",
        "> Tip: atur server dulu di Pengaturan.",
        "> Semoga panen sehat hari ini!"
    )
    private var termIndex = 0
    private var termTarget = ""
    private var termPos = 0
    private var cursorVisible = true

    // 🔹 Navigasi bawah (ImageButton)
    private lateinit var navHome: ImageButton
    private lateinit var navCamera: ImageButton
    private lateinit var navResult: ImageButton
    private lateinit var navSettings: ImageButton
    private lateinit var navProfile: ImageButton

    private val handler = Handler(Looper.getMainLooper())
    private var isConnected = false
    private var isConnecting = false
    // Alamat server yang sedang/terakhir dimuat, dipakai untuk mendeteksi
    // perubahan konfigurasi saat kembali dari halaman Pengaturan.
    private var loadedAddr = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🔹 Inisialisasi View
        webView = findViewById(R.id.webView)
        statusText = findViewById(R.id.connectionStatus)
        refreshButton = findViewById(R.id.btnRefresh)
        btnViewResult = findViewById(R.id.btnViewResult)
        tvServerInfo = findViewById(R.id.tvServerInfo)
        btnConnectIp = findViewById(R.id.btnConnectIp)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        btnDashboard = findViewById(R.id.btnDashboard)
        imgMascot = findViewById(R.id.imgMascot)
        tvTerminal = findViewById(R.id.tvTerminal)
        terminalCard = findViewById(R.id.terminalCard)
        startMascotGreeting()

        // Ketuk terminal / maskot untuk sapaan berikutnya
        terminalCard.setOnClickListener { nextTerminalMessage() }
        imgMascot.setOnClickListener { nextTerminalMessage() }
        // Ketik sapaan pertama setelah animasi entrance selesai
        handler.postDelayed({ typeTerminal(terminalMessages[termIndex]) }, 700)

        // 🔹 Inisialisasi navigasi bawah
        navHome = findViewById(R.id.nav_home)
        navCamera = findViewById(R.id.nav_camera)
        navResult = findViewById(R.id.nav_result)
        navSettings = findViewById(R.id.nav_settings)
        navProfile = findViewById(R.id.nav_profile)

        // 🌐 Setup WebView
        // Klip konten (termasuk gambar stream) ke sudut membulat bg_stream_inner
        // supaya sudut hitam tidak menyembul keluar frame.
        webView.clipToOutline = true
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (isConnecting) {
                    isConnecting = false
                    isConnected = true
                    statusText.text = "Status: Connected"
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                isConnecting = false
                isConnected = false
                statusText.text = "Status: Tidak dapat tersambung ke server"
                showPlaceholder()
            }
        }

        // 🔘 Tombol Hubungkan (pakai server dari Pengaturan)
        btnConnectIp.setOnClickListener {
            if (ServerConfig.isConfigured(this)) {
                loadStream()
            } else {
                Toast.makeText(this, "Atur alamat server di Pengaturan dulu", Toast.LENGTH_SHORT).show()
                openActivity(SettingsActivity::class.java)
            }
        }

        // ⚙️ Tombol Atur Server -> Pengaturan
        btnOpenSettings.setOnClickListener { openActivity(SettingsActivity::class.java) }

        // 🔁 Tombol Refresh Stream
        refreshButton.setOnClickListener {
            if (ServerConfig.isConfigured(this)) {
                statusText.text = "Status: Refreshing..."
                isConnected = false
                isConnecting = true
                loadStream()
            } else {
                Toast.makeText(this, "Atur alamat server di Pengaturan dulu", Toast.LENGTH_SHORT).show()
            }
        }

        // 📊 Tombol lihat hasil deteksi
        btnViewResult.setOnClickListener { openActivity(ResultActivity::class.java) }

        // 📈 Tombol Dashboard statistik
        btnDashboard.setOnClickListener { openActivity(DashboardActivity::class.java) }

        // 📱 Navigasi bawah
        navHome.setOnClickListener {
            Toast.makeText(this, "Kamu sudah di halaman Home", Toast.LENGTH_SHORT).show()
        }
        navCamera.setOnClickListener { openActivity(CameraActivity::class.java) }
        navResult.setOnClickListener { openActivity(ResultActivity::class.java) }
        navSettings.setOnClickListener { openActivity(SettingsActivity::class.java) }
        navProfile.setOnClickListener { openActivity(ProfileActivity::class.java) }
    }

    override fun onResume() {
        super.onResume()
        refreshServerInfo()
    }

    // 🔹 Perbarui info server & muat stream bila perlu (dipanggil tiap kembali ke Home)
    private fun refreshServerInfo() {
        if (!ServerConfig.isConfigured(this)) {
            tvServerInfo.text = "Server belum diatur"
            statusText.text = "Status: Atur IP di Pengaturan"
            loadedAddr = ""
            showPlaceholder()
            return
        }

        val addr = ServerConfig.address(this)
        tvServerInfo.text = "Server: $addr"

        val addrChanged = addr != loadedAddr
        if (ServerConfig.isAutoConnect(this) && (addrChanged || (!isConnected && !isConnecting))) {
            loadStream()
        } else if (!ServerConfig.isAutoConnect(this) && loadedAddr.isEmpty()) {
            statusText.text = "Status: Siap — tekan Hubungkan"
            showPlaceholder()
        }
    }

    // 🔹 Fungsi untuk memuat stream dari server
    private fun loadStream() {
        if (!ServerConfig.isConfigured(this)) {
            showPlaceholder()
            return
        }

        val addr = ServerConfig.address(this)
        loadedAddr = addr
        tvServerInfo.text = "Server: $addr"

        statusText.text = "Status: Connecting..."
        isConnected = false
        isConnecting = true

        val streamWithTimestamp = "${ServerConfig.videoUrl(this)}?cachebust=${System.currentTimeMillis()}"

        val html = """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        margin: 0;
                        padding: 0;
                        background-color: black;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                    }
                    img {
                        border: none;
                        width: 100%;
                        height: auto;
                    }
                </style>
            </head>
            <body>
                <img src="$streamWithTimestamp" alt="Live Stream" />
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)

        // ⏱ Timeout 5 detik
        handler.postDelayed({
            if (!isConnected && isConnecting) {
                isConnecting = false
                statusText.text = "Status: Server tidak merespons"
                showPlaceholder()
            }
        }, 5000)
    }

    // 🔹 Placeholder tampilan hitam jika belum ada video
    private fun showPlaceholder() {
        val html = """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        margin: 0;
                        padding: 0;
                        background-color: black;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        color: white;
                        font-family: Arial, sans-serif;
                        font-size: 18px;
                    }
                    @keyframes pulse {
                        0%, 100% { opacity: 0.45; }
                        50% { opacity: 1; }
                    }
                    div { animation: pulse 1.4s ease-in-out infinite; }
                </style>
            </head>
            <body>
                <div>Menunggu stream...</div>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    // 🤖🌿 Maskot robot-daun yang menyapa: mengambang lembut + melambai berkala.
    private val waveRunnable = object : Runnable {
        override fun run() {
            playWave()
            handler.postDelayed(this, 3600)
        }
    }

    private fun startMascotGreeting() {
        // Mengambang naik-turun pelan.
        bobAnimator = ObjectAnimator.ofFloat(imgMascot, View.TRANSLATION_Y, 0f, -dp(5f)).apply {
            duration = 1300
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 600
            start()
        }
        // Titik putar di bawah-tengah supaya rotasinya terasa seperti melambai.
        imgMascot.post {
            imgMascot.pivotX = imgMascot.width / 2f
            imgMascot.pivotY = imgMascot.height.toFloat()
        }
        handler.postDelayed(waveRunnable, 1200)
    }

    private fun playWave() {
        ObjectAnimator.ofFloat(imgMascot, View.ROTATION, 0f, 14f, -10f, 10f, -6f, 0f).apply {
            duration = 780
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    // ⌨️ Efek ketik terminal + kursor berkedip
    private val typeRunnable = object : Runnable {
        override fun run() {
            if (termPos <= termTarget.length) {
                tvTerminal.text = termTarget.substring(0, termPos) + "▌"
                termPos++
                handler.postDelayed(this, 34)
            } else {
                // Selesai mengetik → mulai kedip kursor
                handler.postDelayed(blinkRunnable, 500)
            }
        }
    }

    private val blinkRunnable = object : Runnable {
        override fun run() {
            cursorVisible = !cursorVisible
            tvTerminal.text = termTarget + if (cursorVisible) "▌" else " "
            handler.postDelayed(this, 520)
        }
    }

    private fun typeTerminal(message: String) {
        handler.removeCallbacks(typeRunnable)
        handler.removeCallbacks(blinkRunnable)
        termTarget = message
        termPos = 0
        cursorVisible = true
        handler.post(typeRunnable)
    }

    private fun nextTerminalMessage() {
        termIndex = (termIndex + 1) % terminalMessages.size
        typeTerminal(terminalMessages[termIndex])
        playWave()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        bobAnimator?.cancel()
        super.onDestroy()
    }

    // 🔹 Fungsi buka activity lain
    private fun <T> openActivity(activityClass: Class<T>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
