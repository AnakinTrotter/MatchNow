package edu.utap.matchnow

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import edu.utap.matchnow.databinding.ActivityDatingBinding

class DatingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDatingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide the app bar for a full-screen experience.
        supportActionBar?.hide()
        binding = ActivityDatingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load the default fragment (ChatFragment) into the container.
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, ChatFragment())
        }

        // Switch fragments based on bottom nav selections.
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.chat -> {
                    supportFragmentManager.commit {
                        replace(R.id.fragmentContainer, ChatFragment())
                    }
                    true
                }
                R.id.profile -> {
                    supportFragmentManager.commit {
                        replace(R.id.fragmentContainer, ProfileFragment())
                    }
                    true
                }
                else -> false
            }
        }
    }
}
