package com.example.percobaan1957.analysis

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * V3 — "mesin pakar" analisis daun padi paling mumpuni yang bisa berjalan
 * on-device TANPA model terlatih. Tidak hanya menghitung warna (V1/V2), tapi
 * membaca BENTUK dan POLA bercak seperti cara ahli menilai daun:
 *
 *  1. Koreksi cahaya gray-world + ukur eksposur & ketajaman (blur menurunkan keyakinan).
 *  2. Klasifikasi per-piksel (bukan grid kasar): hijau / kuning / coklat / pucat / latar.
 *  3. Segmentasi daun: komponen hijau utama (luas × kedekatan pusat) di-region-grow
 *     ke bercak kuning/coklat yang menempel (maks 12 px), plus hole-fill untuk
 *     pusat lesi pucat yang terkurung daun. Latar coklat/kuning yang menempel di
 *     tepi frame dibuang sebagai "blob curiga latar".
 *  4. Ekstraksi lesi individual (connected components) + fitur bentuk lewat momen:
 *     elongasi, ketebalan, komposisi warna, posisi tepi.
 *  5. Skor per penyakit dari pola khasnya:
 *       • Blas          → lesi memanjang, pusat pucat keabu-abuan
 *       • Bercak coklat → bintik oval coklat kecil-sedang menyebar
 *       • Hawar bakteri → menguning dari tepi/ujung daun (margin)
 *       • Tungro/hara   → kuning-oranye merata di seluruh daun
 *       • Bercak garis  → garis coklat sangat tipis memanjang
 *  6. Keyakinan dikalibrasi: skor teratas × selisih ke skor kedua × kualitas foto.
 *
 * Tetap bukan model AI terlatih — batas kejujurannya diberi label di UI. Upgrade
 * berikutnya (model TFLite) cukup mengganti isi [analyze]; UI tidak berubah.
 */
object LeafHealthAnalyzerV3 {

    private const val MAX_DIM = 320
    private const val GROW_LIMIT = 12   // px maks pertumbuhan lesi dari piksel hijau

    private const val BG: Byte = 0
    private const val GREEN: Byte = 1
    private const val YELLOW: Byte = 2
    private const val BROWN: Byte = 3
    private const val PALE: Byte = 4

    private class Blob {
        var area = 0
        var sx = 0.0; var sy = 0.0
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        var yl = 0; var br = 0; var pl = 0
        var touchBorder = false
    }

    fun analyze(src: Bitmap): LeafAnalysis {
        if (src.width < 16 || src.height < 16) return LeafAnalysis.unknown()

        // ---- 0) Kecilkan ke resolusi kerja agar cepat & konsisten ----
        val ratio = MAX_DIM.toFloat() / max(src.width, src.height)
        val bmp = if (ratio < 1f) Bitmap.createScaledBitmap(
            src, max(16, (src.width * ratio).toInt()), max(16, (src.height * ratio).toInt()), true
        ) else src

        val w = bmp.width
        val h = bmp.height
        val n = w * h
        val px = IntArray(n)
        bmp.getPixels(px, 0, w, 0, 0, w, h)

        // ---- 1) Statistik global: white-balance, eksposur, ketajaman ----
        var sr = 0.0; var sg = 0.0; var sb = 0.0; var sl = 0.0
        val lum = FloatArray(n)
        for (i in 0 until n) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            sr += r; sg += g; sb += b
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            lum[i] = l; sl += l
        }
        var grad = 0.0
        for (y in 0 until h - 1) {
            val row = y * w
            for (x in 0 until w - 1) {
                val i = row + x
                grad += abs(lum[i] - lum[i + 1]) + abs(lum[i] - lum[i + w])
            }
        }
        val meanL = sl / n
        val meanGray = (sr + sg + sb) / (3.0 * n)
        val gainR = (meanGray / (sr / n).coerceAtLeast(1.0)).coerceIn(0.6, 1.6)
        val gainG = (meanGray / (sg / n).coerceAtLeast(1.0)).coerceIn(0.6, 1.6)
        val gainB = (meanGray / (sb / n).coerceAtLeast(1.0)).coerceIn(0.6, 1.6)
        val sharpness = grad / ((w - 1.0) * (h - 1.0) * 2.0)
        val blurry = sharpness < 2.6
        val badExposure = meanL < 40 || meanL > 215

        // ---- 2) Klasifikasi per-piksel (setelah koreksi cahaya) ----
        val cls = ByteArray(n)
        val hsv = FloatArray(3)
        var yellowHueSum = 0.0
        var yellowHueCnt = 0
        for (i in 0 until n) {
            val c = px[i]
            val r = min(255.0, ((c shr 16) and 0xFF) * gainR).toInt()
            val g = min(255.0, ((c shr 8) and 0xFF) * gainG).toInt()
            val b = min(255.0, (c and 0xFF) * gainB).toInt()
            Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0]; val s = hsv[1]; val v = hsv[2]
            cls[i] = when {
                v < 0.13f -> BG
                s < 0.16f -> if (v > 0.72f) PALE else BG
                hue in 66f..170f -> GREEN
                hue in 40f..66f -> { yellowHueSum += hue; yellowHueCnt++; YELLOW }
                hue in 12f..40f && v < 0.88f -> BROWN
                hue < 12f && s > 0.30f && v < 0.65f -> BROWN
                else -> BG
            }
        }

        val queue = IntArray(n)

        // ---- 3a) Komponen hijau utama (luas × kedekatan ke pusat bingkai) ----
        val comp = IntArray(n) { -1 }
        var compCount = 0
        val areas = ArrayList<Int>()
        val cxs = ArrayList<Double>()
        val cys = ArrayList<Double>()
        for (start in 0 until n) {
            if (cls[start] != GREEN || comp[start] != -1) continue
            var head = 0; var tail = 0
            queue[tail++] = start; comp[start] = compCount
            var area = 0; var sx = 0.0; var sy = 0.0
            while (head < tail) {
                val i = queue[head++]
                area++
                val x = i % w; val y = i / w
                sx += x; sy += y
                if (x > 0 && cls[i - 1] == GREEN && comp[i - 1] == -1) { comp[i - 1] = compCount; queue[tail++] = i - 1 }
                if (x < w - 1 && cls[i + 1] == GREEN && comp[i + 1] == -1) { comp[i + 1] = compCount; queue[tail++] = i + 1 }
                if (y > 0 && cls[i - w] == GREEN && comp[i - w] == -1) { comp[i - w] = compCount; queue[tail++] = i - w }
                if (y < h - 1 && cls[i + w] == GREEN && comp[i + w] == -1) { comp[i + w] = compCount; queue[tail++] = i + w }
            }
            areas.add(area); cxs.add(sx / area); cys.add(sy / area)
            compCount++
        }
        if (compCount == 0) return LeafAnalysis.unknown()

        val cx0 = w / 2.0; val cy0 = h / 2.0
        val maxD = hypot(cx0, cy0)
        var best = 0; var bestScore = -1.0
        for (k in 0 until compCount) {
            val centrality = 1.0 - hypot(cxs[k] - cx0, cys[k] - cy0) / maxD
            val sc = areas[k] * (0.35 + 0.65 * centrality)
            if (sc > bestScore) { bestScore = sc; best = k }
        }

        // ---- 3b) Region-grow daun ke lesi kuning/coklat menempel (maks GROW_LIMIT px) ----
        val leaf = BooleanArray(n)
        val run = IntArray(n) { -1 }
        var head = 0; var tail = 0
        for (i in 0 until n) if (comp[i] == best) { leaf[i] = true; run[i] = 0; queue[tail++] = i }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w; val y = i / w
            val r0 = run[i]
            var d = 0
            while (d < 4) {
                val j = when (d) {
                    0 -> if (x > 0) i - 1 else -1
                    1 -> if (x < w - 1) i + 1 else -1
                    2 -> if (y > 0) i - w else -1
                    else -> if (y < h - 1) i + w else -1
                }
                d++
                if (j < 0 || leaf[j]) continue
                val cj = cls[j]
                if (cj == GREEN) { leaf[j] = true; run[j] = 0; queue[tail++] = j }
                else if ((cj == YELLOW || cj == BROWN) && r0 < GROW_LIMIT) {
                    leaf[j] = true; run[j] = r0 + 1; queue[tail++] = j
                }
            }
        }

        // ---- 3c) Hole-fill: area terkurung daun = bagian daun (pusat lesi pucat) ----
        val outside = BooleanArray(n)
        head = 0; tail = 0
        for (x in 0 until w) {
            val t = x; val bIdx = (h - 1) * w + x
            if (!leaf[t] && !outside[t]) { outside[t] = true; queue[tail++] = t }
            if (!leaf[bIdx] && !outside[bIdx]) { outside[bIdx] = true; queue[tail++] = bIdx }
        }
        for (y in 0 until h) {
            val l = y * w; val rIdx = y * w + w - 1
            if (!leaf[l] && !outside[l]) { outside[l] = true; queue[tail++] = l }
            if (!leaf[rIdx] && !outside[rIdx]) { outside[rIdx] = true; queue[tail++] = rIdx }
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w; val y = i / w
            if (x > 0 && !leaf[i - 1] && !outside[i - 1]) { outside[i - 1] = true; queue[tail++] = i - 1 }
            if (x < w - 1 && !leaf[i + 1] && !outside[i + 1]) { outside[i + 1] = true; queue[tail++] = i + 1 }
            if (y > 0 && !leaf[i - w] && !outside[i - w]) { outside[i - w] = true; queue[tail++] = i - w }
            if (y < h - 1 && !leaf[i + w] && !outside[i + w]) { outside[i + w] = true; queue[tail++] = i + w }
        }
        for (i in 0 until n) if (!leaf[i] && !outside[i]) {
            leaf[i] = true
            if (cls[i] == BG || cls[i] == PALE) cls[i] = PALE
        }

        var preLeafArea = 0
        for (i in 0 until n) if (leaf[i]) preLeafArea++
        if (preLeafArea < n * 0.04) return LeafAnalysis.unknown()

        // ---- 4) Ekstraksi blob lesi (kuning/coklat/pucat di dalam daun) ----
        val blobLbl = IntArray(n) { -1 }
        val blobs = ArrayList<Blob>()
        for (start in 0 until n) {
            if (!leaf[start] || cls[start] == GREEN || blobLbl[start] != -1) continue
            val blb = Blob()
            val id = blobs.size
            blobs.add(blb)
            head = 0; tail = 0
            blobLbl[start] = id; queue[tail++] = start
            while (head < tail) {
                val i = queue[head++]
                val x = i % w; val y = i / w
                blb.area++
                blb.sx += x; blb.sy += y
                blb.sxx += x.toDouble() * x; blb.syy += y.toDouble() * y; blb.sxy += x.toDouble() * y
                when (cls[i]) { YELLOW -> blb.yl++; BROWN -> blb.br++; PALE -> blb.pl++; else -> {} }
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) blb.touchBorder = true
                if (x > 0 && leaf[i - 1] && cls[i - 1] != GREEN && blobLbl[i - 1] == -1) { blobLbl[i - 1] = id; queue[tail++] = i - 1 }
                if (x < w - 1 && leaf[i + 1] && cls[i + 1] != GREEN && blobLbl[i + 1] == -1) { blobLbl[i + 1] = id; queue[tail++] = i + 1 }
                if (y > 0 && leaf[i - w] && cls[i - w] != GREEN && blobLbl[i - w] == -1) { blobLbl[i - w] = id; queue[tail++] = i - w }
                if (y < h - 1 && leaf[i + w] && cls[i + w] != GREEN && blobLbl[i + w] == -1) { blobLbl[i + w] = id; queue[tail++] = i + w }
            }
        }

        // ---- 4b) Buang blob curiga latar: menempel tepi frame & dominan coklat/kuning besar ----
        val removed = BooleanArray(blobs.size)
        var anyRemoved = false
        for ((id, b) in blobs.withIndex()) {
            if (!b.touchBorder || b.area == 0) continue
            val brF = b.br.toDouble() / b.area
            val ylF = b.yl.toDouble() / b.area
            val areaR = b.area.toDouble() / preLeafArea
            if ((brF > 0.5 && areaR > 0.02) || (ylF > 0.6 && areaR > 0.2)) {
                removed[id] = true; anyRemoved = true
            }
        }
        if (anyRemoved) for (i in 0 until n) {
            val id = blobLbl[i]
            if (id >= 0 && removed[id]) leaf[i] = false
        }

        // ---- 5) Statistik daun final ----
        var leafArea = 0; var gCnt = 0; var yCnt = 0; var bCnt = 0; var pCnt = 0
        for (i in 0 until n) if (leaf[i]) {
            leafArea++
            when (cls[i]) { GREEN -> gCnt++; YELLOW -> yCnt++; BROWN -> bCnt++; PALE -> pCnt++; else -> {} }
        }
        val coverage = leafArea * 100 / n
        if (leafArea < n * 0.04) return LeafAnalysis.unknown()

        // Jarak ke tepi daun (untuk pola hawar bakteri di margin)
        val dist = IntArray(n) { 9 }
        head = 0; tail = 0
        for (i in 0 until n) if (leaf[i]) {
            val x = i % w; val y = i / w
            val isEdge = x == 0 || y == 0 || x == w - 1 || y == h - 1 ||
                !leaf[i - 1] || !leaf[i + 1] || !leaf[i - w] || !leaf[i + w]
            if (isEdge) { dist[i] = 0; queue[tail++] = i }
        }
        while (head < tail) {
            val i = queue[head++]
            val nd = dist[i] + 1
            if (nd > 3) continue
            val x = i % w; val y = i / w
            if (x > 0 && leaf[i - 1] && dist[i - 1] > nd) { dist[i - 1] = nd; queue[tail++] = i - 1 }
            if (x < w - 1 && leaf[i + 1] && dist[i + 1] > nd) { dist[i + 1] = nd; queue[tail++] = i + 1 }
            if (y > 0 && leaf[i - w] && dist[i - w] > nd) { dist[i - w] = nd; queue[tail++] = i - w }
            if (y < h - 1 && leaf[i + w] && dist[i + w] > nd) { dist[i + w] = nd; queue[tail++] = i + w }
        }
        var yMargin = 0
        for (i in 0 until n) if (leaf[i] && cls[i] == YELLOW && dist[i] <= 3) yMargin++

        // ---- 6) Fitur bentuk per blob → skor pola penyakit ----
        val leafD = leafArea.toDouble()
        var blast = 0.0; var brownSpot = 0.0; var streak = 0.0
        var lesionCount = 0
        for ((id, b) in blobs.withIndex()) {
            if (removed[id] || b.area < 4) continue
            lesionCount++
            val mx = b.sx / b.area; val my = b.sy / b.area
            val cxx = b.sxx / b.area - mx * mx
            val cyy = b.syy / b.area - my * my
            val cxy = b.sxy / b.area - mx * my
            val t = (cxx + cyy) / 2.0
            val det = cxx * cyy - cxy * cxy
            val s = sqrt(max(0.0, t * t - det))
            val l1 = t + s
            val l2 = max(t - s, 1e-4)
            val elong = sqrt(l1 / l2)      // rasio panjang:lebar
            val minor = 4.0 * sqrt(max(l2, 0.0))  // perkiraan tebal (px)
            val areaR = b.area / leafD
            val brF = b.br.toDouble() / b.area
            val plF = b.pl.toDouble() / b.area
            when {
                elong >= 6.0 && minor <= 3.5 && brF > 0.5 ->
                    streak += min(0.5, areaR * 20) + 0.15
                (elong >= 2.6 && brF > 0.35) || plF > 0.25 ->
                    blast += min(0.6, areaR * 12) + (if (plF > 0.15) 0.2 else 0.08)
                elong < 2.6 && brF > 0.45 && areaR in 0.0004..0.06 ->
                    brownSpot += min(0.5, areaR * 15) + 0.12
                else -> {}
            }
        }
        blast = min(1.0, blast); brownSpot = min(1.0, brownSpot); streak = min(1.0, streak)

        val gRt = gCnt / leafD
        val yR = yCnt / leafD
        val bR = bCnt / leafD
        val pR = pCnt / leafD
        val lesionR = yR + bR + pR
        val marginF = if (yCnt > 0) yMargin.toDouble() / yCnt else 0.0
        val meanYellowHue = if (yellowHueCnt > 0) yellowHueSum / yellowHueCnt else 60.0
        val blight = if (yR > 0.10)
            min(1.0, yR * 2.2) * (0.35 + 0.65 * marginF) * (if (bR + pR > 0.03) 1.1 else 0.9)
        else 0.0
        val tungro = if (yR > 0.22)
            min(1.0, yR * 1.5) * (1.0 - marginF * 0.5) * (if (meanYellowHue < 52) 1.0 else 0.55)
        else 0.0

        // ---- 7) Kualitas foto & catatan ----
        var quality = (coverage / 40.0).coerceIn(0.62, 1.0)
        if (blurry) quality *= 0.82
        if (badExposure) quality *= 0.85
        val notes = StringBuilder()
        if (blurry) notes.append("\n• Foto agak buram — coba foto ulang lebih stabil.")
        if (badExposure) notes.append("\n• Pencahayaan kurang ideal (terlalu gelap/terang).")
        if (coverage < 25) notes.append("\n• Daun kecil di bingkai — dekatkan kamera.")
        val noteStr = if (notes.isEmpty()) "" else "\n\nCatatan kualitas foto:$notes"

        val greenPct = (gRt * 100).toInt()
        val yellowPct = (yR * 100).toInt()
        val brownPct = ((bR + pR) * 100).toInt()
        val severity = when {
            lesionR < 0.05 -> "Ringan"
            lesionR < 0.15 -> "Sedang"
            else -> "Berat"
        }

        // ---- 8) Keputusan ----
        if (lesionR < 0.055 && gRt > 0.72) {
            val conf = ((0.5 + 0.45 * gRt) * quality).coerceIn(0.4, 0.95).toFloat()
            return LeafAnalysis(
                LeafStatus.SEHAT, "Daun Sehat", conf,
                greenPct, yellowPct, brownPct, coverage,
                "Daun tampak hijau segar tanpa pola bercak penyakit. Lanjutkan perawatan rutin " +
                    "dan pantau berkala.$noteStr",
                lesionCount = lesionCount
            )
        }

        val ranked = listOf(
            Triple(
                "Blas Daun (Blast)", blast,
                "Pola lesi memanjang dengan pusat pucat khas penyakit blas. Hindari pemupukan " +
                    "nitrogen berlebih, jaga jarak tanam agar tidak lembap, dan pertimbangkan " +
                    "fungisida berbahan aktif trisiklazol sesuai dosis. Konsultasikan dengan penyuluh."
            ),
            Triple(
                "Bercak Coklat (Brown Spot)", brownSpot,
                "Bintik oval kecoklatan menyebar khas brown spot, sering dipicu kekurangan kalium " +
                    "dan tanah kurang subur. Perbaiki pemupukan berimbang (K & Si), gunakan benih " +
                    "sehat, dan fungisida mankozeb bila meluas."
            ),
            Triple(
                "Hawar Daun Bakteri (Kresek)", blight,
                "Menguning/mengering dari tepi dan ujung daun mengarah ke hawar daun bakteri. " +
                    "Hindari genangan dan nitrogen berlebih, jangan alirkan air dari petak sakit, " +
                    "dan gunakan varietas tahan pada musim berikutnya."
            ),
            Triple(
                "Tungro / Kekurangan Hara", tungro,
                "Daun menguning-oranye merata — bisa tungro (ditularkan wereng hijau) atau " +
                    "kekurangan nitrogen. Periksa keberadaan wereng; bila tungro, cabut rumpun sakit " +
                    "dan kendalikan vektor. Bila hara, perbaiki pemupukan N."
            ),
            Triple(
                "Bercak Garis Sempit", streak,
                "Garis-garis coklat sempit memanjang khas narrow brown spot, umum saat tanaman " +
                    "menua atau kurang kalium. Perbaiki pemupukan K dan pantau perkembangannya."
            )
        ).sortedByDescending { it.second }

        val top = ranked[0]
        val second = ranked[1].second

        if (top.second < 0.22) {
            val conf = (0.55 * quality).coerceIn(0.35, 0.7).toFloat()
            return LeafAnalysis(
                LeafStatus.WASPADA, "Perlu Pemeriksaan Lanjut", conf,
                greenPct, yellowPct, brownPct, coverage,
                "Ada perubahan warna pada daun namun polanya belum khas penyakit tertentu. " +
                    "Pantau 2–3 hari ke depan dan foto ulang dengan pencahayaan baik.$noteStr",
                severity = severity, lesionCount = lesionCount
            )
        }

        val conf = ((0.5 + 0.35 * top.second + 0.1 * (top.second - second)) * quality)
            .coerceIn(0.4, 0.92).toFloat()
        return LeafAnalysis(
            LeafStatus.TERINFEKSI, top.first, conf,
            greenPct, yellowPct, brownPct, coverage,
            top.third + noteStr,
            diseaseName = top.first, severity = severity, lesionCount = lesionCount
        )
    }
}
