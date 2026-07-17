package com.example.percobaan1957

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

class ProfileActivity : AppCompatActivity() {

    private lateinit var profileImage: ImageView
    private lateinit var nameText: TextView
    private lateinit var emailText: TextView
    private lateinit var usernameText: TextView
    private lateinit var createdDateText: TextView
    private lateinit var logoutButton: Button
    private lateinit var editButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Inisialisasi View
        profileImage = findViewById(R.id.profile_image)
        nameText = findViewById(R.id.profile_name)
        emailText = findViewById(R.id.profile_email)
        usernameText = findViewById(R.id.profile_username)
        createdDateText = findViewById(R.id.profile_created_date)
        logoutButton = findViewById(R.id.btn_logout)
        editButton = findViewById(R.id.btnEditProfile)

        // 🔹 Ambil data dari SharedPreferences
        val prefs = getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        val userName = prefs.getString("name", "Samuel Manik")
        val userEmail = prefs.getString("email", "samuel@example.com")
        val userUsername = prefs.getString("username", "samuelmanik")
        val createdDate = prefs.getString("created_date", "2025-01-01")
        val profilePicUrl = "https://i.imgur.com/1bX5QH6.jpg"

        // 🔹 Tampilkan ke UI
        nameText.text = userName
        emailText.text = userEmail
        usernameText.text = "Username: $userUsername"
        createdDateText.text = "Created: $createdDate"

        Glide.with(this)
            .load(profilePicUrl)
            .apply(RequestOptions.circleCropTransform())
            .into(profileImage)

        // Tombol Edit Account
        editButton.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)
        }

        // Tombol Logout
        logoutButton.setOnClickListener {
            val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            sharedPreferences.edit().clear().apply()

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    // Supaya profil otomatis update setelah kembali dari EditProfileActivity
    override fun onResume() {
        super.onResume()

        val prefs = getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        nameText.text = prefs.getString("name", "Samuel Manik")
        emailText.text = prefs.getString("email", "samuel@example.com")
        usernameText.text = "Username: ${prefs.getString("username", "samuelmanik")}"
        createdDateText.text = "Created: ${prefs.getString("created_date", "2025-01-01")}"

        val photoUri = prefs.getString("photo_uri", null)
        val profilePicUrl = photoUri ?: "https://i.imgur.com/1bX5QH6.jpg" // default

        Glide.with(this)
            .load(profilePicUrl)
            .apply(RequestOptions.circleCropTransform())
            .into(profileImage)
    }
}
