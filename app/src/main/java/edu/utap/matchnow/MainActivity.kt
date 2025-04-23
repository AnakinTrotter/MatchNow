package edu.utap.matchnow

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import edu.utap.matchnow.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var authUser: AuthUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide the app bar for a full-screen experience.
        supportActionBar?.hide()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Handle login button click
        binding.loginButton.setOnClickListener {
            Log.d(TAG, "Login button clicked")
            authUser.login()
        }

        // Handle sign up button click (for simplicity, call login)
        binding.signUpButton.setOnClickListener {
            Log.d(TAG, "Sign Up button clicked")
            authUser.login()
        }
    }

    override fun onStart() {
        super.onStart()
        authUser = AuthUser(activityResultRegistry)
        lifecycle.addObserver(authUser)

        // Observe the user; if authenticated, navigate to DatingActivity.
        authUser.observeUser().observe(this) { user ->
            Log.d(TAG, "Observed user: $user")
            if (!user.isInvalid()) {
                val intent = Intent(this, DatingActivity::class.java)
                startActivity(intent)
                finish() // Prevent navigating back to login
            }
        }
    }
}
