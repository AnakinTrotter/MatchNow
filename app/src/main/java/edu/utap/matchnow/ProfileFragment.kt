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
    // Use a safe getter that assumes _binding is non-null only between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    // Get Firestore and current user
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load profile data from Firestore
        currentUser?.let { user ->
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    // Make sure the binding is still valid (i.e. view is not destroyed)
                    val safeBinding = _binding ?: return@addOnSuccessListener

                    if (document != null && document.exists()) {
                        val name = document.getString("name") ?: "Anonymous"
                        val age = document.getLong("age")?.toInt() ?: 0
                        val loveLanguage = document.getString("loveLanguage") ?: "Unknown"
                        val bio = document.getString("bio") ?: "No bio"
                        val location = document.getString("location") ?: "Unknown"
                        // Retrieve search radius if available
                        val searchRadius = document.getString("searchRadius") ?: "25"
                        val profilePictureUrl = document.getString("profilePictureUrl")
                        val photos = document.get("photos") as? List<String>

                        // Update UI components using safeBinding
                        safeBinding.nameAge.text = "$name, $age"
                        safeBinding.loveLanguage.text = "Love Language: $loveLanguage"
                        safeBinding.bio.text = "\"$bio\""
                        safeBinding.locationText.text = "Location: $location"
                        safeBinding.searchRadiusText.text = "Searching Within: $searchRadius miles"
                        safeBinding.profileTitle.text = "Your Profile"

                        // Load the profile picture if URL exists
                        if (!profilePictureUrl.isNullOrEmpty()) {
                            Glide.with(requireContext())
                                .load(profilePictureUrl)
                                .into(safeBinding.profilePicture)
                        }

                        // Load photos if available (using the first three URLs)
                        photos?.let { photoList ->
                            if (photoList.isNotEmpty() && photoList[0].isNotEmpty()) {
                                Glide.with(requireContext())
                                    .load(photoList[0])
                                    .into(safeBinding.photo1)
                            }
                            if (photoList.size > 1 && photoList[1].isNotEmpty()) {
                                Glide.with(requireContext())
                                    .load(photoList[1])
                                    .into(safeBinding.photo2)
                            }
                            if (photoList.size > 2 && photoList[2].isNotEmpty()) {
                                Glide.with(requireContext())
                                    .load(photoList[2])
                                    .into(safeBinding.photo3)
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

        // Edit Profile button: launch the edit profile activity
        binding.editProfileButton.setOnClickListener {
            val intent = Intent(requireContext(), EditProfileActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
