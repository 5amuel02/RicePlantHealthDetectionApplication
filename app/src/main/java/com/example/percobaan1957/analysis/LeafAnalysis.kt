package com.example.percobaan1957.analysis

/**
 * Hasil analisis kesehatan daun secara lokal (on-device).
 * Berisi kesimpulan status, keyakinan, dan komposisi warna daun.
 */
data class LeafAnalysis(
    val status: LeafStatus,
    val label: String,
    val confidence: Float,      // 0..1
    val greenPercent: Int,      // % daun hijau sehat
    val yellowPercent: Int,     // % daun menguning (klorosis)
    val brownPercent: Int,      // % bercak coklat / nekrosis
    val leafCoverage: Int,      // % piksel yang dianggap daun (bukan latar)
    val advice: String,
    val diseaseName: String = "",   // dugaan jenis penyakit spesifik (diisi analyzer V3)
    val severity: String = "",      // Ringan / Sedang / Berat (diisi analyzer V3)
    val lesionCount: Int = 0        // jumlah bercak/lesi pada daun (diisi analyzer V3)
) {
    val confidencePercent: Int get() = (confidence * 100).toInt()

    companion object {
        /** Dipakai saat daun tidak cukup terlihat untuk disimpulkan. */
        fun unknown() = LeafAnalysis(
            status = LeafStatus.TIDAK_YAKIN,
            label = "Daun Tidak Terbaca",
            confidence = 0f,
            greenPercent = 0,
            yellowPercent = 0,
            brownPercent = 0,
            leafCoverage = 0,
            advice = "Daun kurang terlihat jelas. Arahkan kamera lebih dekat ke daun dengan " +
                "pencahayaan cukup, lalu ulangi."
        )
    }
}

enum class LeafStatus(val displayName: String) {
    SEHAT("Sehat"),
    WASPADA("Perlu Diperhatikan"),
    TERINFEKSI("Terindikasi Penyakit"),
    TIDAK_YAKIN("Tidak Yakin")
}
