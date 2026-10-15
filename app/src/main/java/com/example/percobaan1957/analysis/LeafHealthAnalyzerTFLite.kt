package com.example.percobaan1957.analysis

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * On-device rice-leaf disease classifier — a real trained model (MobileNetV2,
 * transfer-learned), not the hand-engineered color/shape heuristics in [LeafHealthAnalyzerV3].
 *
 * `model.tflite` and `labels.txt` are NOT bundled in this repo (binary model artifacts
 * don't belong in source control by default). Train them yourself with
 * `training/train_rice_disease_tflite.ipynb` (Google Colab, ~15 minutes, free GPU) and
 * drop the two output files into `app/src/main/assets/` — see that folder's README.
 *
 * Until then, [isAvailable] is false and callers should fall back to [LeafHealthAnalyzerV3],
 * which needs no model file and always works.
 *
 * The training set (CC BY 4.0, Mendeley Data DOI 10.17632/fwcj7stb8r.1 — Sethy & Barpanda,
 * "Rice Leaf Disease Image Samples") has exactly four classes: Bacterial blight, Blast,
 * Brown Spot, and Tungro. There is no "healthy" class, so this analyzer never claims a
 * leaf is healthy — a low top-class confidence is reported as [LeafStatus.WASPADA]
 * ("needs a closer look") instead, the same bucket V3 uses for the same situation.
 */
class LeafHealthAnalyzerTFLite(context: Context) {

    private val interpreter: Interpreter? = loadInterpreter(context.assets)
    private val labels: List<String> = loadLabels(context.assets)

    /** True once both the model and its label file loaded successfully from assets. */
    val isAvailable: Boolean get() = interpreter != null && labels.isNotEmpty()

    fun analyze(bitmap: Bitmap): LeafAnalysis? {
        val model = interpreter ?: return null
        if (labels.isEmpty()) return null

        val input = preprocess(bitmap)
        val output = Array(1) { FloatArray(labels.size) }
        model.run(input, output)
        val probabilities = output[0]

        var topIndex = 0
        for (i in probabilities.indices) if (probabilities[i] > probabilities[topIndex]) topIndex = i
        val topLabel = labels[topIndex]
        val confidence = probabilities[topIndex]

        if (confidence < CONFIDENCE_THRESHOLD) {
            return LeafAnalysis(
                status = LeafStatus.WASPADA,
                label = "Perlu Pemeriksaan Lanjut",
                confidence = confidence,
                greenPercent = 0,
                yellowPercent = 0,
                brownPercent = 0,
                leafCoverage = 0,
                advice = "Model belum cukup yakin dengan pola pada foto ini (keyakinan " +
                    "${(confidence * 100).toInt()}%). Foto ulang dengan pencahayaan lebih baik " +
                    "dan daun mengisi sebagian besar bingkai, atau coba beberapa daun lain.",
            )
        }

        val disease = DISEASE_INFO.entries.firstOrNull { (key, _) -> topLabel.normalizedContains(key) }?.value
            ?: DiseaseInfo(topLabel, "Tidak ada saran spesifik tersedia untuk kelas ini.")

        return LeafAnalysis(
            status = LeafStatus.TERINFEKSI,
            label = disease.displayName,
            confidence = confidence,
            greenPercent = 0,
            yellowPercent = 0,
            brownPercent = 0,
            leafCoverage = 0,
            advice = disease.advice,
            diseaseName = disease.displayName,
        )
    }

    fun close() {
        interpreter?.close()
    }

    private fun preprocess(src: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(src, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())
        val px = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(px, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in px) {
            // MobileNetV2 preprocessing: scale [0, 255] to [-1, 1] per channel.
            buffer.putFloat((((pixel shr 16) and 0xFF) / 127.5f) - 1f)
            buffer.putFloat((((pixel shr 8) and 0xFF) / 127.5f) - 1f)
            buffer.putFloat(((pixel and 0xFF) / 127.5f) - 1f)
        }
        return buffer
    }

    private fun String.normalizedContains(key: String): Boolean =
        this.lowercase().replace(Regex("[^a-z]"), "").contains(key)

    private data class DiseaseInfo(val displayName: String, val advice: String)

    companion object {
        private const val TAG = "LeafHealthAnalyzerTFLite"
        private const val MODEL_FILE = "model.tflite"
        private const val LABELS_FILE = "labels.txt"
        private const val INPUT_SIZE = 224
        private const val CONFIDENCE_THRESHOLD = 0.55f

        // Matched against labels.txt entries via normalizedContains — tolerant of the exact
        // casing/spacing/underscore style the training notebook happened to write.
        private val DISEASE_INFO = linkedMapOf(
            "blast" to DiseaseInfo(
                "Blas Daun (Blast)",
                "Model mendeteksi pola khas penyakit blas. Hindari pemupukan nitrogen " +
                    "berlebih, jaga jarak tanam agar tidak lembap, dan pertimbangkan fungisida " +
                    "berbahan aktif trisiklazol sesuai dosis. Konsultasikan dengan penyuluh.",
            ),
            "brown" to DiseaseInfo(
                "Bercak Coklat (Brown Spot)",
                "Model mendeteksi bintik oval kecoklatan khas brown spot, sering dipicu " +
                    "kekurangan kalium dan tanah kurang subur. Perbaiki pemupukan berimbang " +
                    "(K & Si), gunakan benih sehat, dan fungisida mankozeb bila meluas.",
            ),
            "bacterial" to DiseaseInfo(
                "Hawar Daun Bakteri (Kresek)",
                "Model mendeteksi pola khas hawar daun bakteri. Hindari genangan dan " +
                    "nitrogen berlebih, jangan alirkan air dari petak sakit, dan gunakan " +
                    "varietas tahan pada musim berikutnya.",
            ),
            "blight" to DiseaseInfo(
                "Hawar Daun Bakteri (Kresek)",
                "Model mendeteksi pola khas hawar daun bakteri. Hindari genangan dan " +
                    "nitrogen berlebih, jangan alirkan air dari petak sakit, dan gunakan " +
                    "varietas tahan pada musim berikutnya.",
            ),
            "tungro" to DiseaseInfo(
                "Tungro",
                "Model mendeteksi pola khas tungro, ditularkan oleh wereng hijau. Periksa " +
                    "keberadaan wereng di sekitar tanaman, cabut dan musnahkan rumpun yang " +
                    "sakit, dan kendalikan vektornya agar tidak menyebar ke petak lain.",
            ),
        )

        private fun loadInterpreter(assets: AssetManager): Interpreter? {
            val buffer = try {
                loadModelFile(assets)
            } catch (e: IOException) {
                Log.i(TAG, "model.tflite not bundled yet, falling back to the heuristic analyzer")
                return null
            }
            return try {
                Interpreter(buffer)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize the TFLite interpreter", e)
                null
            }
        }

        private fun loadModelFile(assets: AssetManager): MappedByteBuffer {
            val descriptor = assets.openFd(MODEL_FILE)
            FileInputStream(descriptor.fileDescriptor).use { input ->
                return input.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength,
                )
            }
        }

        private fun loadLabels(assets: AssetManager): List<String> = try {
            assets.open(LABELS_FILE).bufferedReader().use { it.readLines() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: IOException) {
            emptyList()
        }
    }
}
