package com.example.percobaan1957

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.percobaan1957.analysis.LeafAnalysis
import com.example.percobaan1957.analysis.LeafHealthAnalyzerTFLite
import com.example.percobaan1957.analysis.LeafHealthAnalyzerV3
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var capturedImage: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var captureGuide: View
    private lateinit var captureControls: View
    private lateinit var reviewControls: View
    private lateinit var loadingOverlay: View
    private lateinit var tvLoadingText: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var btnSwitch: ImageButton
    private lateinit var btnShutter: ImageButton

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var useBackCamera = true
    private var flashOn = false
    private var photoFile: File? = null

    private val httpClient = OkHttpClient()

    // Loads model.tflite from assets/ once, lazily, off the main thread's critical path.
    // isAvailable is false (no crash) until that file is actually bundled — see the
    // analyzer's kdoc and app/src/main/assets/README.md.
    private val tfliteAnalyzer by lazy { LeafHealthAnalyzerTFLite(this) }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "Izin kamera diperlukan untuk fitur ini", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.previewView)
        capturedImage = findViewById(R.id.capturedImage)
        tvTitle = findViewById(R.id.tvTitle)
        captureGuide = findViewById(R.id.captureGuide)
        captureControls = findViewById(R.id.captureControls)
        reviewControls = findViewById(R.id.reviewControls)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        tvLoadingText = findViewById(R.id.tvLoadingText)
        btnClose = findViewById(R.id.btnClose)
        btnFlash = findViewById(R.id.btnFlash)
        btnSwitch = findViewById(R.id.btnSwitch)
        btnShutter = findViewById(R.id.btnShutter)

        btnClose.setOnClickListener { finish() }
        btnShutter.setOnClickListener { takePhoto() }
        btnSwitch.setOnClickListener {
            useBackCamera = !useBackCamera
            startCamera()
        }
        btnFlash.setOnClickListener { toggleFlash() }
        findViewById<View>(R.id.btnRetake).setOnClickListener { retake() }
        findViewById<View>(R.id.btnDetect).setOnClickListener { analyzeLocally() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    // ============ CameraX ============
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val selector = if (useBackCamera)
                    CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleFlash() {
        flashOn = !flashOn
        imageCapture?.flashMode =
            if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        btnFlash.setImageResource(if (flashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
        val file = File(outputDir(), "RAATAP_$name.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, "Gagal mengambil foto: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    showReview(file)
                }
            }
        )
    }

    // ============ Review mode ============
    private fun showReview(file: File) {
        photoFile = file
        Glide.with(this).load(file).into(capturedImage)

        capturedImage.visibility = View.VISIBLE
        previewView.visibility = View.INVISIBLE
        captureGuide.visibility = View.GONE
        captureControls.visibility = View.GONE
        btnFlash.visibility = View.GONE
        reviewControls.visibility = View.VISIBLE
        tvTitle.text = "Pratinjau Foto"
    }

    private fun retake() {
        photoFile?.delete()
        photoFile = null

        capturedImage.setImageDrawable(null)
        capturedImage.visibility = View.GONE
        previewView.visibility = View.VISIBLE
        captureGuide.visibility = View.VISIBLE
        captureControls.visibility = View.VISIBLE
        btnFlash.visibility = View.VISIBLE
        reviewControls.visibility = View.GONE
        tvTitle.text = "Kamera Deteksi"
    }

    // ============ Analisis lokal (on-device, tanpa server) ============
    private fun analyzeLocally() {
        val file = photoFile ?: return
        tvLoadingText.text = "Menganalisis foto…"
        loadingOverlay.visibility = View.VISIBLE

        // Decode + analisis di thread terpisah agar UI tidak macet.
        Thread {
            val bitmap = try {
                decodeSampled(file, 640)
            } catch (e: Exception) {
                null
            }
            val usedTFLite = bitmap != null && tfliteAnalyzer.isAvailable
            val result = when {
                bitmap == null -> null
                usedTFLite -> tfliteAnalyzer.analyze(bitmap)
                else -> LeafHealthAnalyzerV3.analyze(bitmap)
            }
            runOnUiThread {
                loadingOverlay.visibility = View.GONE
                if (result == null) {
                    Toast.makeText(this, "Gagal membaca foto untuk dianalisis", Toast.LENGTH_SHORT).show()
                } else {
                    showLocalResult(result, usedTFLite)
                }
            }
        }.start()
    }

    // Decode foto dengan penurunan resolusi supaya hemat memori & cepat.
    private fun decodeSampled(file: File, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        val larger = maxOf(bounds.outWidth, bounds.outHeight)
        while (larger / sample > maxSize) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun showLocalResult(a: LeafAnalysis, usedTFLite: Boolean) {
        val msg = buildString {
            append("Status: ${a.status.displayName}\n")
            append("Keyakinan: ${a.confidencePercent}%\n")
            if (a.severity.isNotEmpty()) append("Tingkat keparahan: ${a.severity}\n")
            if (a.lesionCount > 0) append("Bercak/lesi terdeteksi: ${a.lesionCount}\n")
            if (!usedTFLite) {
                append("\nKomposisi warna daun:\n")
                append("• Hijau sehat: ${a.greenPercent}%\n")
                append("• Menguning: ${a.yellowPercent}%\n")
                append("• Bercak coklat: ${a.brownPercent}%\n")
                append("• Cakupan daun: ${a.leafCoverage}%\n")
            }
            append("\n")
            append(a.advice)
            append(
                if (usedTFLite) {
                    "\n\n— Model AI on-device (MobileNetV2, dilatih dari dataset publik CC BY 4.0)"
                } else {
                    "\n\n— Analisis lokal V3: mesin pakar warna & bentuk (bukan model AI terlatih)"
                }
            )
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(a.label)
            .setMessage(msg)
            .setPositiveButton("Selesai") { _, _ -> retake() }
            .setNegativeButton("Tutup", null)
        // Opsional: masih bisa kirim ke server bila IP sudah diatur.
        if (ServerConfig.isConfigured(this)) {
            builder.setNeutralButton("Kirim ke server") { _, _ -> uploadForDetection() }
        }
        builder.show()
    }

    // ============ Kirim ke Flask ============
    private fun uploadForDetection() {
        val file = photoFile ?: return

        if (!ServerConfig.isConfigured(this)) {
            AlertDialog.Builder(this)
                .setTitle("IP server belum diatur")
                .setMessage("Atur IP drone/Flask terlebih dahulu di halaman Pengaturan, lalu coba lagi. Foto tetap tersimpan di perangkat.")
                .setPositiveButton("Buka Pengaturan") { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .setNegativeButton("Nanti", null)
                .show()
            return
        }

        tvLoadingText.text = "Mengirim untuk deteksi…"
        loadingOverlay.visibility = View.VISIBLE

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                file.name,
                file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            .build()

        val url = ServerConfig.predictUrl(this)
        val request = Request.Builder().url(url).post(body).build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loadingOverlay.visibility = View.GONE
                    showServerPending(file, "Tidak dapat terhubung ke server deteksi ($url).\n\n${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                val bodyStr = response.body?.string().orEmpty()
                runOnUiThread {
                    loadingOverlay.visibility = View.GONE
                    if (code == 200) {
                        showDetectionResult(bodyStr)
                    } else {
                        showServerPending(file, "Endpoint /predict belum tersedia di server (HTTP $code).")
                    }
                }
            }
        })
    }

    private fun showDetectionResult(bodyStr: String) {
        AlertDialog.Builder(this)
            .setTitle("Hasil Deteksi")
            .setMessage(if (bodyStr.isBlank()) "Server tidak mengirim data." else bodyStr)
            .setPositiveButton("Lihat di Hasil") { _, _ ->
                startActivity(Intent(this, ResultActivity::class.java))
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun showServerPending(file: File, reason: String) {
        AlertDialog.Builder(this)
            .setTitle("Server deteksi belum siap")
            .setMessage("$reason\n\nFoto berhasil disimpan di perangkat:\n${file.absolutePath}")
            .setPositiveButton("Selesai") { _, _ -> retake() }
            .setNegativeButton("Ulangi", null)
            .show()
    }

    private fun outputDir(): File {
        val dir = externalCacheDir ?: cacheDir
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        tfliteAnalyzer.close()
    }
}
