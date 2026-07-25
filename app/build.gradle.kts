import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasSigningConfig = keystorePropertiesFile.exists()
if (hasSigningConfig) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.example.percobaan1957"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.percobaan1957"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // TFLite needs to mmap the model file directly out of the APK, which fails if
    // AAPT compresses it — keep .tflite assets stored, not compressed.
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    // 🔹 AndroidX dasar
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // 🔹 Kamera (CameraX) untuk halaman ambil foto padi
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // 🔹 Networking (ambil data dari Flask)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 🔹 JSON parsing
    implementation("org.json:json:20240303")
    implementation("com.google.code.gson:gson:2.10.1")

    // 🔹 Image loader pakai Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // Library grafik
    implementation("com.github.PhilJay:MPAndroidChart:3.1.0")

    // 🔹 On-device rice-leaf disease classifier (see analysis/LeafHealthAnalyzerTFLite.kt).
    // Falls back to the heuristic V3 analyzer if no model.tflite is bundled in assets/.
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    // 🔹 Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
