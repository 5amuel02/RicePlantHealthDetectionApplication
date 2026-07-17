package com.example.percobaan1957

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sembunyikan action bar (biar tampilan lebih bersih)
        supportActionBar?.hide()

        // Keyboard resize diatur lewat android:windowSoftInputMode di Manifest.
        setContentView(R.layout.activity_login)

        val usernameField = findViewById<EditText>(R.id.inputUsername)
        val passwordField = findViewById<EditText>(R.id.inputPassword)
        val btnSignIn = findViewById<Button>(R.id.btnSignIn)

        btnSignIn.setOnClickListener {
            val username = usernameField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Isi username dan password dulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (username == "admin" && password == "12345") {
                Toast.makeText(this, "Login berhasil", Toast.LENGTH_SHORT).show()

                // Pindah ke halaman monitoring
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish() // Supaya tidak bisa balik ke login
            } else {
                Toast.makeText(this, "Username atau password salah", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
