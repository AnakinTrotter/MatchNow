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

        currentUser?.let { user ->
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    val safeBinding = _binding ?: return@addOnSuccessListener

                    if (document != null && document.exists()) {
                        val name = document.getString("name") ?: "Anonymous"
                        val age = document.getLong("age")?.toInt() ?: 0
                        val loveLanguage = document.getString("loveLanguage") ?: "Unknown"
                        val bio = document.getString("bio") ?: "No bio"
                        val location = document.getString("location") ?: "Unknown"
                        val searchRadius = document.getLong("searchRadius")?.toInt() ?: 25
                        val profilePictureUrl = document.getString("profilePictureUrl")
                        val photos = document.get("photos") as? List<*>

                        safeBinding.nameAge.text = "$name, $age"
                        safeBinding.loveLanguage.text = "Love Language: $loveLanguage"
                        safeBinding.bio.text = "\"$bio\""
                        safeBinding.locationText.text = "Location: $location"
                        safeBinding.searchRadiusText.text = "Searching Within: $searchRadius miles"
                        safeBinding.profileTitle.text = "Your Profile"

                        profilePictureUrl?.takeIf { it.isNotEmpty() }?.let { url ->
                            Glide.with(requireContext())
                                .load(url)
                                .into(safeBinding.profilePicture)
                        }

                        photos?.filterIsInstance<String>()?.let { photoList ->
                            if (photoList.isNotEmpty()) {
                                if (photoList.size > 0 && photoList[0].isNotEmpty()) {
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
                        }
                    } else {
                        Log.d("ProfileFragment", "No such document")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("ProfileFragment", "Error getting document: ", exception)
                }
        }

        binding.logoutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            requireActivity().finish()
        }

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
