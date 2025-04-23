package edu.utap.matchnow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import edu.utap.matchnow.databinding.FragmentProfileBinding

class MatchProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private var targetUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetUserId = arguments?.getString("userId")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.logoutButton.visibility = View.GONE
        binding.editProfileButton.visibility = View.GONE

        val profileContainer = binding.root.findViewById<ViewGroup>(R.id.profileContainer)

        val userId = targetUserId ?: return
        val currentUid = currentUserId ?: return

        // Fetch current user's matches
        firestore.collection("users").document(currentUid).get().addOnSuccessListener { currentUserDoc ->
            val matches = currentUserDoc.get("matches") as? List<*> ?: emptyList<String>()

            // Inflate and setup the action button
            val buttonLayout = View.inflate(context, R.layout.match_action_button, null)
            val actionButton = buttonLayout.findViewById<Button>(R.id.matchActionButton)

            if (matches.contains(userId)) {
                actionButton.text = "Message"
                actionButton.setOnClickListener {
                    Toast.makeText(requireContext(), "Chat coming soon!", Toast.LENGTH_SHORT).show()
                }
            } else {
                actionButton.text = "Match"
                actionButton.setOnClickListener {
                    performMatch()
                }
            }

            // Replace editProfileButton with this new action button
            val editIndex = profileContainer.indexOfChild(binding.editProfileButton)
            profileContainer.removeView(binding.editProfileButton)
            profileContainer.addView(buttonLayout, editIndex)
        }

        firestore.collection("users").document(userId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                binding.nameAge.text = "${doc.getString("name")}, ${doc.getLong("age")?.toInt() ?: 0}"
                binding.loveLanguage.text = "Love Language: ${doc.getString("loveLanguage")}"
                binding.bio.text = "\"${doc.getString("bio")}\""
                binding.locationText.text = "Location: ${doc.getString("location")}"
                binding.searchRadiusText.text = "Searching Within: ${doc.getLong("searchRadius") ?: 0} miles"

                val profilePic = doc.getString("profilePictureUrl")
                if (!profilePic.isNullOrEmpty()) {
                    Glide.with(requireContext()).load(profilePic).into(binding.profilePicture)
                }

                val photos = doc.get("photos") as? List<*> ?: emptyList<String>()
                val urls = photos.filterIsInstance<String>()
                if (urls.isNotEmpty()) Glide.with(requireContext()).load(urls[0]).into(binding.photo1)
                if (urls.size > 1) Glide.with(requireContext()).load(urls[1]).into(binding.photo2)
                if (urls.size > 2) Glide.with(requireContext()).load(urls[2]).into(binding.photo3)
            }
        }
    }

    private fun performMatch() {
        val otherUserId = targetUserId ?: return
        val currentUid = currentUserId ?: return
        val batch = firestore.batch()

        val currentRef = firestore.collection("users").document(currentUid)
        val otherRef = firestore.collection("users").document(otherUserId)

        batch.update(currentRef, "matches", FieldValue.arrayUnion(otherUserId))
        batch.update(otherRef, "matches", FieldValue.arrayUnion(currentUid))

        batch.commit().addOnSuccessListener {
            Toast.makeText(requireContext(), "It's a match!", Toast.LENGTH_SHORT).show()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to match.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
