package edu.utap.matchnow

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import edu.utap.matchnow.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Get Firestore and current user
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load profile data from Firestore
        currentUser?.let { user ->
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("name") ?: "Anonymous"
                        val age = document.getLong("age")?.toInt() ?: 0
                        val loveLanguage = document.getString("loveLanguage") ?: "Unknown"
                        val bio = document.getString("bio") ?: "No bio"
                        val location = document.getString("location") ?: "Unknown"
                        val profilePictureUrl = document.getString("profilePictureUrl")
                        val photos = document.get("photos") as? List<String>

                        // Update UI components
                        binding.nameAge.text = "$name, $age"
                        binding.loveLanguage.text = "Love Language: $loveLanguage"
                        binding.bio.text = "\"$bio\""
                        binding.locationText.text = "Location: $location"
                        binding.profileTitle.text = "Your Profile"

                        // Load the profile picture if URL exists
                        if (!profilePictureUrl.isNullOrEmpty()) {
                            Glide.with(requireContext())
                                .load(profilePictureUrl)
                                .into(binding.profilePicture)
                        }

                        // Load photos if available (using the first three URLs)
                        photos?.let { photoList ->
                            if (photoList.isNotEmpty()) {
                                if (photoList.size > 0 && photoList[0].isNotEmpty()) {
                                    Glide.with(requireContext())
                                        .load(photoList[0])
                                        .into(binding.photo1)
                                }
                                if (photoList.size > 1 && photoList[1].isNotEmpty()) {
                                    Glide.with(requireContext())
                                        .load(photoList[1])
                                        .into(binding.photo2)
                                }
                                if (photoList.size > 2 && photoList[2].isNotEmpty()) {
                                    Glide.with(requireContext())
                                        .load(photoList[2])
                                        .into(binding.photo3)
                                }
                            }
                        }
                    } else {
                        Log.d("ProfileFragment", "No such document")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("ProfileFragment", "Error getting document: ", exception)
                }
        }

        // Logout button: sign out and return to the login screen (MainActivity)
        binding.logoutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        // Stub for Edit Profile button (implement navigation to an edit screen if needed)
        binding.editProfileButton.setOnClickListener {
            // TODO: Navigate to EditProfileActivity (not implemented yet)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
