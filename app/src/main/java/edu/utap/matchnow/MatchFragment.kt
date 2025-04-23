package edu.utap.matchnow

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import edu.utap.matchnow.databinding.FragmentMatchBinding
import java.util.*
import java.util.concurrent.TimeUnit

class MatchFragment : Fragment() {

    private var _binding: FragmentMatchBinding? = null
    private val binding get() = _binding!!
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser

    private var pollId: String? = null
    private var pollExpired = false
    private var timer: CountDownTimer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetchPollOfTheDay()
        fetchPotentialMatches()
    }

    private fun fetchPollOfTheDay() {
        val todayKey = getTodayPollId()
        val pollRef = firestore.collection("polls").document(todayKey)

        pollRef.get().addOnSuccessListener { poll ->
            if (!poll.exists()) {
                binding.pollQuestion.text = "No poll available today!"
                return@addOnSuccessListener
            }

            pollId = poll.id
            val question = poll.getString("question") ?: return@addOnSuccessListener
            val options = poll.get("options") as? List<String> ?: return@addOnSuccessListener
            val timestamp = poll.getTimestamp("timestamp") ?: return@addOnSuccessListener

            val rawResponses = poll.get("responses")
            val responses: Map<String, List<String>> = when (rawResponses) {
                is Map<*, *> -> rawResponses.mapNotNull { (key, value) ->
                    val keyStr = key as? String ?: return@mapNotNull null
                    val list = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    keyStr to list
                }.toMap()
                else -> emptyMap()
            }

            val currentUserId = currentUser?.uid ?: return@addOnSuccessListener
            val now = Date()
            val timeDiff = now.time - timestamp.toDate().time
            val timeLeft = 60 * 60 * 1000 - timeDiff

            binding.pollQuestion.text = question

            val selectedOption = responses.entries.find { (_, users) ->
                users.contains(currentUserId)
            }?.key?.toIntOrNull()

            if (timeLeft <= 0) {
                pollExpired = true
                if (selectedOption != null) {
                    showPollOptions(options, selectedOption, disabled = true)
                } else {
                    showPollExpired()
                }
            } else {
                startCountdown(timeLeft)
                if (selectedOption != null) {
                    showPollOptions(options, selectedOption, disabled = true)
                } else {
                    showPollOptions(options)
                }
            }
        }.addOnFailureListener {
            binding.pollQuestion.text = "Error loading poll."
        }
    }

    private fun getTodayPollId(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // Months are 0-indexed
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "poll-%04d-%02d-%02d".format(year, month, day)
    }

    private fun showPollOptions(
        options: List<String>,
        selectedOption: Int? = null,
        disabled: Boolean = false
    ) {
        binding.pollOptionsContainer.removeAllViews()
        options.forEachIndexed { index, optionText ->
            val button = Button(requireContext()).apply {
                text = optionText
                isEnabled = !disabled && selectedOption == null
                if (selectedOption == index) {
                    setBackgroundColor(resources.getColor(android.R.color.holo_blue_light))
                }
                setOnClickListener {
                    submitPollResponse(index)
                }
            }
            binding.pollOptionsContainer.addView(button)
        }
    }

    private fun showPollExpired() {
        binding.pollOptionsContainer.removeAllViews()
        binding.pollOptionsContainer.addView(TextView(requireContext()).apply {
            text = "The poll has expired for today, come back tomorrow!"
            setPadding(8, 8, 8, 8)
        })
    }

    private fun submitPollResponse(optionIndex: Int) {
        val userId = currentUser?.uid ?: return
        val pollRef = firestore.collection("polls").document(pollId ?: return)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(pollRef)

            val raw = snapshot.get("responses")
            Log.d("MatchFragment", "Raw responses = $raw")

            val currentResponses = when (raw) {
                is Map<*, *> -> raw.mapNotNull { (key, value) ->
                    val keyStr = key as? String ?: return@mapNotNull null
                    val list = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    keyStr to list
                }.toMap()
                else -> emptyMap()
            }

            Log.d("MatchFragment", "Parsed currentResponses = $currentResponses")

            val updatedResponses = mutableMapOf<String, Any>()
            currentResponses.forEach { (key, users) ->
                val newUsers = users.filterNot { it == userId }
                updatedResponses[key] = newUsers
            }

            val updatedList = (updatedResponses["$optionIndex"] as? List<String>) ?: emptyList()
            updatedResponses["$optionIndex"] = updatedList + userId

            Log.d("MatchFragment", "Updated responses = $updatedResponses")

            transaction.update(pollRef, "responses", updatedResponses)
        }.addOnSuccessListener {
            Toast.makeText(requireContext(), "Thanks for voting!", Toast.LENGTH_SHORT).show()
            fetchPollOfTheDay()
            timer?.cancel()
        }.addOnFailureListener { e ->
            Log.e("MatchFragment", "Failed to submit vote", e)
            Toast.makeText(requireContext(), "Failed to submit vote", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCountdown(timeLeftMillis: Long) {
        timer = object : CountDownTimer(timeLeftMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                binding.pollCountdown.text = "Time left: %02d:%02d".format(minutes, seconds)
            }

            override fun onFinish() {
                showPollExpired()
            }
        }.start()
    }

    private fun fetchPotentialMatches() {
        val currentUserId = currentUser?.uid ?: return
        val todayPollId = getTodayPollId()

        // Step 1: Get current user's data
        firestore.collection("users").document(currentUserId).get().addOnSuccessListener { currentUserDoc ->
            val myLat = currentUserDoc.getDouble("lat") ?: return@addOnSuccessListener
            val myLng = currentUserDoc.getDouble("lng") ?: return@addOnSuccessListener
            val searchRadius = currentUserDoc.getLong("searchRadius")?.toInt() ?: 100

            // Step 2: Get current user's poll response
            firestore.collection("polls").document(todayPollId).get().addOnSuccessListener { pollDoc ->
                val responses = pollDoc.get("responses") as? Map<String, List<String>> ?: emptyMap()

                val myResponseOption = responses.entries.find { it.value.contains(currentUserId) }?.key

                if (myResponseOption == null) {
                    binding.matchesRecycler.adapter = MatchesAdapter(emptyList()) // User hasn't voted
                    return@addOnSuccessListener
                }

                val matchingUserIds = responses[myResponseOption] ?: emptyList()

                // Step 3: Get all users and filter
                firestore.collection("users").get().addOnSuccessListener { result ->
                    val users = result.documents.mapNotNull { doc ->
                        val uid = doc.id
                        if (uid == currentUserId) return@mapNotNull null
                        if (!matchingUserIds.contains(uid)) return@mapNotNull null

                        val name = doc.getString("name") ?: return@mapNotNull null
                        val age = doc.getLong("age")?.toInt() ?: return@mapNotNull null
                        val profilePic = doc.getString("profilePictureUrl") ?: return@mapNotNull null
                        val lat = doc.getDouble("lat") ?: return@mapNotNull null
                        val lng = doc.getDouble("lng") ?: return@mapNotNull null

                        val distance = haversine(myLat, myLng, lat, lng)
                        if (distance <= searchRadius) {
                            Triple(name, age, profilePic)
                        } else {
                            null
                        }
                    }

                    binding.matchesRecycler.layoutManager = LinearLayoutManager(requireContext())
                    binding.matchesRecycler.adapter = MatchesAdapter(users)
                }
            }
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 3958.8 // Radius of Earth in miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }


    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        _binding = null
    }

    class MatchesAdapter(private val data: List<Triple<String, Int, String>>) :
        RecyclerView.Adapter<MatchesAdapter.MatchViewHolder>() {

        class MatchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.matchImage)
            val nameAgeView: TextView = view.findViewById(R.id.matchNameAge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_match, parent, false)
            return MatchViewHolder(view)
        }

        override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
            val (name, age, photoUrl) = data[position]
            holder.nameAgeView.text = "$name, $age"
            Glide.with(holder.imageView.context).load(photoUrl).into(holder.imageView)
        }

        override fun getItemCount() = data.size
    }
}
