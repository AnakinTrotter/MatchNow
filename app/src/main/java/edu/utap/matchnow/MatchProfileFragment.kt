package edu.utap.matchnow

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
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

        // Hide the logout and edit profile buttons since we're in MatchProfileFragment
        binding.logoutButton.visibility = View.GONE
        binding.editProfileButton.visibility = View.GONE

        val userId = targetUserId ?: return
        val currentUid = currentUserId ?: return

        // First, fetch the current user's data...
        firestore.collection("users").document(currentUid).get().addOnSuccessListener { currentUserDoc ->
            val myMatches = currentUserDoc.get("matches") as? List<String> ?: emptyList()

            // ...then fetch the target user's data
            firestore.collection("users").document(userId).get().addOnSuccessListener { targetDoc ->
                val targetMatches = targetDoc.get("matches") as? List<String> ?: emptyList()

                // Inflate our custom action button layout (must contain a Button with id matchActionButton)
                val buttonLayout = View.inflate(context, R.layout.match_action_button, null)
                val actionButton = buttonLayout.findViewById<Button>(R.id.matchActionButton)

                // Determine button state:
                // 1. If the current user has already sent a match request (their matches contain targetUserId)
                if (myMatches.contains(userId)) {
                    // If the target user has also matched back, then mutual match → "Message"
                    if (targetMatches.contains(currentUid)) {
                        actionButton.text = "Message"
                        actionButton.isEnabled = true
                        actionButton.setOnClickListener {
                            startOrOpenChat(currentUid, userId)
                        }
                    } else {
                        // Only current user has matched → "Awaiting Response..."
                        actionButton.text = "Awaiting Response..."
                        actionButton.isEnabled = false
                    }
                } else {
                    // No match request yet → "Match"
                    actionButton.text = "Match"
                    actionButton.isEnabled = true
                    actionButton.setOnClickListener {
                        performMatch(currentUid, userId)
                    }
                }

                // Replace the existing editProfileButton in the profile container with our action button.
                val profileContainer = binding.root.findViewById<ViewGroup>(R.id.profileContainer)
                val editIndex = profileContainer.indexOfChild(binding.editProfileButton)
                profileContainer.removeView(binding.editProfileButton)
                profileContainer.addView(buttonLayout, editIndex)
            }
        }

        // Load the target user's profile info for display
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

    /**
     * Creates a chat ID by sorting the two UIDs in descending order and joining with a dash.
     */
    private fun createChatId(uid1: String, uid2: String): String {
        return listOf(uid1, uid2).sortedDescending().joinToString("-")
    }

    /**
     * Starts or opens a chat with the target user.
     */
    private fun startOrOpenChat(uid1: String, uid2: String) {
        val chatId = createChatId(uid1, uid2)
        val chatRef = firestore.collection("chats").document(chatId)

        chatRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                val now = Timestamp.now()
                val chatData = mapOf(
                    "users" to listOf(uid1, uid2),
                    "lastMessage" to "Started chat",
                    "lastUpdated" to now
                )

                val userRef1 = firestore.collection("users").document(uid1)
                val userRef2 = firestore.collection("users").document(uid2)
                val batch = firestore.batch()
                batch.set(chatRef, chatData)
                // Optionally update each user's "chatsWith" field:
                batch.update(userRef1, "chatsWith", FieldValue.arrayUnion(uid2))
                batch.update(userRef2, "chatsWith", FieldValue.arrayUnion(uid1))

                batch.commit().addOnSuccessListener {
                    chatRef.collection("messages").document("init").set(
                        mapOf(
                            "from" to uid1,
                            "to" to uid2,
                            "message" to "Started chat",
                            "timestamp" to now
                        )
                    ).addOnSuccessListener {
                        navigateToChat(chatId, uid2)
                    }.addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to write initial message", Toast.LENGTH_SHORT).show()
                        it.printStackTrace()
                    }
                }.addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to create chat", Toast.LENGTH_SHORT).show()
                    it.printStackTrace()
                }
            } else {
                navigateToChat(chatId, uid2)
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to load chat", Toast.LENGTH_SHORT).show()
            it.printStackTrace()
        }
    }

    /**
     * Navigates to the ChatMessageFragment to open the chat.
     */
    private fun navigateToChat(chatId: String, otherUserId: String) {
        firestore.collection("users").document(otherUserId).get()
            .addOnSuccessListener { user ->
                val name = user.getString("name") ?: "Unknown"
                Toast.makeText(requireContext(), "Opening chat with $name", Toast.LENGTH_SHORT).show()
                val fragment = ChatMessageFragment.newInstance(chatId, otherUserId, name)
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load user info", Toast.LENGTH_SHORT).show()
                it.printStackTrace()
            }
    }

    /**
     * Called when the current user clicks "Match". This adds the targetUserId only to the current user’s "matches" field.
     */
    private fun performMatch(currentUid: String, otherUserId: String) {
        firestore.collection("users").document(currentUid)
            .update("matches", FieldValue.arrayUnion(otherUserId))
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Match request sent", Toast.LENGTH_SHORT).show()
                // Navigate back to MatchFragment
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to send match request.", Toast.LENGTH_SHORT).show()
                it.printStackTrace()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
