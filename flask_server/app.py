"""
RA-ATAP Flask server
=====================
Endpoint:
  GET  /              -> status server (buka di browser untuk cek server hidup)
  POST /predict       -> terima foto padi (multipart field "image"), simpan, balas hasil
  GET  /documents     -> daftar hasil deteksi { "data": [...] }  (dipakai halaman Result)
  GET  /get_results   -> daftar hasil deteksi (bentuk list, kompatibilitas lama)

Catatan: model deteksi penyakit BELUM terpasang. /predict memakai heuristik
warna daun sederhana (rasio hijau) sebagai placeholder sementara — bukan
diagnosis penyakit. Kalau OpenCV/numpy tidak terpasang, analisis dilewati dan
hasil ditandai "menunggu_analisis".
"""

import json
import os
from datetime import datetime

from flask import Flask, jsonify, request, url_for

# OpenCV & numpy opsional — server tetap jalan walau belum di-install.
try:
    import cv2
    import numpy as np
    CV_AVAILABLE = True
except Exception:
    CV_AVAILABLE = False

app = Flask(__name__)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
UPLOAD_DIR = os.path.join(app.static_folder, "uploads")
PRED_FILE = os.path.join(BASE_DIR, "predictions.json")

os.makedirs(UPLOAD_DIR, exist_ok=True)


# ---------------------------------------------------------------------------
# Penyimpanan hasil (file JSON sederhana)
# ---------------------------------------------------------------------------
def load_predictions():
    if not os.path.exists(PRED_FILE):
        return []
    try:
        with open(PRED_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except (ValueError, OSError):
        return []


def save_predictions(items):
    with open(PRED_FILE, "w", encoding="utf-8") as f:
        json.dump(items, f, indent=2, ensure_ascii=False)


# ---------------------------------------------------------------------------
# Analisis warna daun (placeholder, bukan diagnosis penyakit)
# ---------------------------------------------------------------------------
def analyze_leaf(image_path):
    """Kembalikan (status, label, confidence) dari rasio piksel hijau."""
    if not CV_AVAILABLE:
        return "menunggu_analisis", "Belum dianalisis", 0.0

    img = cv2.imread(image_path)
    if img is None:
        return "menunggu_analisis", "Gambar tidak terbaca", 0.0

    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    # Rentang hijau daun pada ruang warna HSV
    lower = np.array([25, 40, 40])
    upper = np.array([95, 255, 255])
    mask = cv2.inRange(hsv, lower, upper)
    green_ratio = float((mask > 0).mean())  # 0.0 - 1.0

    if green_ratio >= 0.45:
        status = "sehat"
        label = "Daun hijau segar"
    else:
        status = "perlu_pemeriksaan"
        label = "Warna daun kurang hijau"
    return status, label, round(green_ratio, 3)


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------
@app.route("/", methods=["GET"])
def index():
    return jsonify({
        "app": "RA-ATAP Flask server",
        "status": "online",
        "opencv": CV_AVAILABLE,
        "endpoints": ["/predict (POST)", "/documents (GET)", "/get_results (GET)"],
        "total_hasil": len(load_predictions()),
    })


@app.route("/predict", methods=["POST"])
def predict():
    if "image" not in request.files:
        return jsonify({"status": "error", "message": "Tidak ada file 'image' pada request"}), 400

    file = request.files["image"]
    if file.filename == "":
        return jsonify({"status": "error", "message": "Nama file kosong"}), 400

    # Simpan foto
    timestamp = datetime.now()
    fname = f"padi_{timestamp.strftime('%Y%m%d_%H%M%S_%f')}.jpg"
    fpath = os.path.join(UPLOAD_DIR, fname)
    file.save(fpath)

    # Analisis placeholder
    status, label, confidence = analyze_leaf(fpath)

    image_url = request.host_url.rstrip("/") + url_for("static", filename=f"uploads/{fname}")

    record = {
        "timestamp": timestamp.isoformat(timespec="seconds"),
        "label": label,
        "confidence": confidence,
        "status": status,
        "source": "kamera_android",
        "image_url": image_url,
        "note": "Placeholder heuristik warna daun. Model deteksi penyakit belum terpasang.",
    }

    items = load_predictions()
    items.insert(0, record)          # terbaru di depan
    save_predictions(items[:100])    # simpan maksimal 100 terakhir

    return jsonify({"status": "success", "data": record})


@app.route("/documents", methods=["GET"])
def documents():
    return jsonify({"data": load_predictions()})


@app.route("/get_results", methods=["GET"])
def get_results():
    # Bentuk list agar tetap kompatibel dengan versi lama.
    return jsonify(load_predictions())


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
