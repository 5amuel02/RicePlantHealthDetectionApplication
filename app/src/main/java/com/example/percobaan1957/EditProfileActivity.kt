package com.example.percobaan1957

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class EditProfileActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etUsername: EditText
    private lateinit var etCreatedDate: EditText
    private lateinit var btnSaveProfile: Button
    private lateinit var btnChangePhoto: Button
    private lateinit var imgProfilePreview: ImageView

    private var selectedImageUri: Uri? = null // Menyimpan URI foto yang dipilih

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etUsername = findViewById(R.id.etUsername)
        etCreatedDate = findViewById(R.id.etCreatedDate)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        btnChangePhoto = findViewById(R.id.btnChangePhoto)
        imgProfilePreview = findViewById(R.id.imgProfilePreview)

        val prefs = getSharedPreferences("user_profile", Context.MODE_PRIVATE)

        // 🔹 Ambil data lama
        etName.setText(prefs.getString("name", ""))
        etEmail.setText(prefs.getString("email", ""))
        etUsername.setText(prefs.getString("username", ""))
        etCreatedDate.setText(prefs.getString("created_date", ""))

        // 🔹 Ambil foto lama (jika ada)
        val savedPhotoUri = prefs.getString("photo_uri", null)
        if (savedPhotoUri != null) {
            Glide.with(this).load(Uri.parse(savedPhotoUri)).circleCrop().into(imgProfilePreview)
        }

        // 🔹 Activity Result untuk memilih gambar dari galeri
        val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                selectedImageUri = uri
                Glide.with(this).load(uri).circleCrop().into(imgProfilePreview)
            }
        }

        // 🔹 Klik tombol “Ubah Foto”
        btnChangePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // 🔹 Tombol Simpan
        btnSaveProfile.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val username = etUsername.text.toString().trim()
            val createdDate = etCreatedDate.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || username.isEmpty() || createdDate.isEmpty()) {
                Toast.makeText(this, "Semua field harus diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🔹 Simpan ke SharedPreferences
            prefs.edit()
                .putString("name", name)
                .putString("email", email)
                .putString("username", username)
                .putString("created_date", createdDate)
                .apply()

            // 🔹 Simpan foto (jika dipilih)
            selectedImageUri?.let {
                prefs.edit().putString("photo_uri", it.toString()).apply()
            }

            Toast.makeText(this, "Profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
