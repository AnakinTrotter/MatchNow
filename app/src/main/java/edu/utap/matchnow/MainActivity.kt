package edu.utap.matchnow

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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Clear the display name edit text if first time
        if (savedInstanceState == null) {
            binding.displayNameET.text.clear()
        }

        // Logout button
        binding.logoutBut.setOnClickListener {
            // XXX Write me
            authUser.logout()
        }

        // Login button
        // If the user spam-clicks, we only want to initiate one login
        binding.loginBut.setOnClickListener {
            // XXX Write me
            authUser.login()
        }

        // Set display name button
        binding.setDisplayName.setOnClickListener {
            // XXX Write me
            val displayName = binding.displayNameET.text.toString()
            if (displayName.isNotEmpty()) {
                authUser.setDisplayName(displayName)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Initialize AuthUser, observe data to display in UI
        // https://developer.android.com/reference/androidx/lifecycle/Lifecycle#addObserver(androidx.lifecycle.LifecycleObserver)
        // XXX Write me
        authUser = AuthUser(activityResultRegistry)
        lifecycle.addObserver(authUser)

        // Observe the live user data and update UI accordingly
        authUser.observeUser().observe(this) { user ->
            Log.d(TAG, "Observed user: $user")
            binding.displayName.text = user.name
            binding.userEmail.text = user.email
            binding.userUid.text = user.uid
        }
    }
}