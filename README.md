# Rice Plant Health Detection Application

An Android app + Flask backend for rice-leaf photo capture and analysis — built during an internship project. The app captures a leaf photo via CameraX, uploads it to a local Flask server, and displays a color-based health reading with a running history of past scans.

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

- **In-app camera capture** (CameraX) with immediate preview before upload
- **Leaf analysis pipeline** — client-side pre-analysis (`LeafHealthAnalyzerV3`) plus a server-side color-ratio heuristic as a placeholder for a future trained model
- **Result history** — past scans listed with timestamp, color reading, and description, backed by a small Flask + JSON store
- **Configurable server endpoint** — Flask server IP/port set from the in-app Settings screen (`ServerConfig`), not hardcoded
- **Profile & auth screens** for a per-user experience

## How it works

1. `CameraActivity` captures a leaf photo and runs a local pre-analysis pass
2. The photo uploads to the Flask server's `/predict` endpoint (multipart form)
3. The server analyzes leaf color ratio (OpenCV/numpy if available, degrades gracefully if not) and stores the result
4. `ResultActivity` polls `/documents` / `/get_results` to show scan history

> The disease-detection model itself is **not yet integrated** — the current server-side analysis is a color-ratio heuristic placeholder, not a real diagnosis. This is flagged directly in the server code's docstring.

## Tech stack

**Android:** Kotlin, CameraX, Glide, OkHttp, Material Components
**Backend:** Flask, OpenCV (optional), NumPy (optional)

## Project structure

```
app/src/main/java/com/example/percobaan1957/
├── CameraActivity.kt          # capture + upload flow
├── analysis/                  # client-side leaf analysis
├── ResultActivity.kt          # scan history
├── ServerConfig.kt            # single source of truth for server IP/port
└── Login/Register/Profile*.kt

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
