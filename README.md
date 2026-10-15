# Rice Plant Health Detection Application

An Android app + Flask backend for rice-leaf photo capture and analysis — built during an internship project. The app captures a leaf photo via CameraX, runs it through an on-device analyzer, and displays a health reading with a running history of past scans (backend optional).

> 📱 Android (Kotlin) + 🐍 Python/Flask backend

<p align="center">
  <a href="https://github.com/5amuel02/RicePlantHealthDetectionApplication/releases/latest">
    <img src="https://img.shields.io/github/v/release/5amuel02/RicePlantHealthDetectionApplication?label=Download%20APK&style=for-the-badge&color=16A34A" alt="Download latest APK" />
  </a>
</p>

## Install

1. Download `RiceHealth-v1.0.0.apk` from **[the latest release](https://github.com/5amuel02/RicePlantHealthDetectionApplication/releases/latest)**
2. Open it on your Android device (7.0+) and allow installation from this source when prompted
3. Camera capture and local pre-analysis work immediately; to save results to history, point the app at a running Flask backend from the **Settings** screen (see *Running it yourself* below for the backend)

The APK is signed with a dedicated release key; verify it with `apksigner verify --print-certs RiceHealth-v1.0.0.apk`.

## Features

- **In-app camera capture** (CameraX) with immediate preview before analysis
- **On-device disease classifier** (`LeafHealthAnalyzerTFLite`) — a MobileNetV2 model, transfer-learned on a public rice-leaf-disease dataset and exported to TFLite; runs fully offline, no server needed. Falls back automatically to `LeafHealthAnalyzerV3` — a hand-engineered color/shape heuristic analyzer that needs no trained model at all — if `model.tflite` isn't bundled (see *On-device model* below)
- **Result history** — past scans listed with timestamp, color reading, and description, backed by a small Flask + JSON store
- **Configurable server endpoint** — Flask server IP/port set from the in-app Settings screen (`ServerConfig`), not hardcoded; the server upload is optional, only used to persist history
- **Profile & auth screens** for a per-user experience

## How it works

1. `CameraActivity` captures a leaf photo and analyzes it **on-device** — the trained TFLite classifier if a model is bundled, otherwise the V3 heuristic analyzer
2. The result displays immediately in an in-app dialog; optionally, the photo also uploads to the Flask server's `/predict` endpoint to persist it to history
3. The server analyzes leaf color ratio (OpenCV/numpy if available, degrades gracefully if not) and stores the result — this server-side path is a placeholder heuristic, not a trained model; the real classifier lives on-device (see above)
4. `ResultActivity` polls `/documents` / `/get_results` to show scan history

## On-device model

`model.tflite` isn't committed to this repo (binary model weights don't belong in
source control by default) — train it yourself with
[`training/train_rice_disease_tflite.ipynb`](training/train_rice_disease_tflite.ipynb)
in Google Colab (free GPU, ~15 minutes: `Runtime → Run all`), then drop the two
output files into `app/src/main/assets/` (see that folder's
[README](app/src/main/assets/README.md)) and rebuild.

Trained on ["Rice Leaf Disease Image Samples"](https://data.mendeley.com/datasets/fwcj7stb8r/1)
(Sethy & Barpanda, Mendeley Data, DOI [10.17632/fwcj7stb8r.1](https://doi.org/10.17632/fwcj7stb8r.1),
**CC BY 4.0**) — four classes: Bacterial blight, Blast, Brown Spot, Tungro. The
dataset has no "healthy" class, so a low-confidence prediction is reported as
"needs a closer look" rather than a false "healthy" claim.

Until you run the notebook, the app works exactly as before — it just uses the V3
heuristic analyzer instead.

## Tech stack

**Android:** Kotlin, CameraX, TensorFlow Lite, Glide, OkHttp, Material Components
**Model training:** TensorFlow/Keras (MobileNetV2 transfer learning), Google Colab
**Backend:** Flask, OpenCV (optional), NumPy (optional)

## Project structure

```
app/src/main/java/com/example/percobaan1957/
├── CameraActivity.kt          # capture + analyze + optional upload flow
├── analysis/
│   ├── LeafHealthAnalyzerTFLite.kt  # trained MobileNetV2 classifier (needs model.tflite)
│   └── LeafHealthAnalyzerV3.kt      # hand-engineered color/shape heuristic, no model needed
├── ResultActivity.kt          # scan history
├── ServerConfig.kt            # single source of truth for server IP/port
└── Login/Register/Profile*.kt

app/src/main/assets/           # model.tflite + labels.txt go here (not committed)

training/
└── train_rice_disease_tflite.ipynb  # Colab notebook that produces the model files

flask_server/
├── app.py                     # Flask API (/predict, /documents, /get_results)
└── requirements.txt
```

## Running it yourself

Just want to try the app? Grab the [prebuilt APK](https://github.com/5amuel02/RicePlantHealthDetectionApplication/releases/latest) instead — camera capture and pre-analysis work without the backend below (see *Install*).

**Backend:**
```bash
cd flask_server
pip install -r requirements.txt
python app.py
```

**App:**
```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # minified release APK — unsigned unless you provide your own keystore.properties
```
Then set the Flask server's IP and port from the app's Settings screen (blank by default — no server is pre-configured).

## Status

Built as part of an internship (magang) project. A known simplification worth noting if you read the code: `LoginActivity` currently checks against a single hardcoded demo credential rather than a real auth backend — fine for a prototype, but the first thing to replace before any real deployment.
