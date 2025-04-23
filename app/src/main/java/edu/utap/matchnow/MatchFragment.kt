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
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "poll-%04d-%02d-%02d".format(year, month, day)
    }

    private fun showPollOptions(options: List<String>, selectedOption: Int? = null, disabled: Boolean = false) {
        binding.pollOptionsContainer.removeAllViews()
        options.forEachIndexed { index, optionText ->
            val button = Button(requireContext()).apply {
                text = optionText
                isEnabled = !disabled && selectedOption == null
                if (selectedOption == index) {
                    setBackgroundColor(resources.getColor(android.R.color.holo_blue_light))
                }
                setOnClickListener { submitPollResponse(index) }
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

    private fun submitPollResponse(optionIndex: Int) {
        val userId = currentUser?.uid ?: return
        val pollRef = firestore.collection("polls").document(pollId ?: return)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(pollRef)
            val raw = snapshot.get("responses")

            val currentResponses = when (raw) {
                is Map<*, *> -> raw.mapNotNull { (key, value) ->
                    val keyStr = key as? String ?: return@mapNotNull null
                    val list = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    keyStr to list
                }.toMap()
                else -> emptyMap()
            }

            val updatedResponses = mutableMapOf<String, Any>()
            currentResponses.forEach { (key, users) ->
                val newUsers = users.filterNot { it == userId }
                updatedResponses[key] = newUsers
            }

            val updatedList = (updatedResponses["$optionIndex"] as? List<String>) ?: emptyList()
            updatedResponses["$optionIndex"] = updatedList + userId

            transaction.update(pollRef, "responses", updatedResponses)
        }.addOnSuccessListener {
            Toast.makeText(requireContext(), "Thanks for voting!", Toast.LENGTH_SHORT).show()
            fetchPollOfTheDay()
            timer?.cancel()
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to submit vote", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchPotentialMatches() {
        val currentUserId = currentUser?.uid ?: return
        val todayPollId = getTodayPollId()
        Log.d("MatchDebug", "Fetching matches for current user: $currentUserId")

        firestore.collection("users").document(currentUserId).get().addOnSuccessListener { currentUserDoc ->
            val myLat = currentUserDoc.getDouble("lat") ?: return@addOnSuccessListener
            val myLng = currentUserDoc.getDouble("lng") ?: return@addOnSuccessListener
            val myRadius = currentUserDoc.getLong("searchRadius")?.toInt() ?: 100
            val myMatches = currentUserDoc.get("matches") as? List<String> ?: emptyList()
            Log.d("MatchDebug", "My matches: $myMatches")

            firestore.collection("polls").document(todayPollId).get().addOnSuccessListener { pollDoc ->
                val responses = pollDoc.get("responses") as? Map<String, List<String>> ?: emptyMap()
                val myResponseOption = responses.entries.find { it.value.contains(currentUserId) }?.key
                if (myResponseOption == null) {
                    Log.d("MatchDebug", "User $currentUserId did not respond to today's poll")
                    attachAdapters(emptyList(), emptyList(), emptyList(), emptyList())
                    return@addOnSuccessListener
                }

                val matchingUserIds = responses[myResponseOption] ?: emptyList()
                Log.d("MatchDebug", "Users who selected same option: $matchingUserIds")

                firestore.collection("users").get().addOnSuccessListener { result ->
                    val confirmed = mutableListOf<Pair<Triple<String, Int, String>, String>>()
                    val potential = mutableListOf<Pair<Triple<String, Int, String>, String>>()

                    result.documents.forEach { doc ->
                        val uid = doc.id
                        if (uid == currentUserId) return@forEach

                        if (!matchingUserIds.contains(uid)) {
                            Log.d("MatchDebug", "Skipping $uid — not in same poll response")
                            return@forEach
                        }

                        val name = doc.getString("name") ?: return@forEach
                        val age = doc.getLong("age")?.toInt() ?: return@forEach
                        val profilePic = doc.getString("profilePictureUrl") ?: return@forEach
                        val lat = doc.getDouble("lat") ?: return@forEach
                        val lng = doc.getDouble("lng") ?: return@forEach
                        val theirRadius = doc.getLong("searchRadius")?.toInt() ?: 100
                        val theirMatches = doc.get("matches") as? List<String> ?: emptyList()

                        val distance = haversine(myLat, myLng, lat, lng)
                        Log.d("MatchDebug", "Checking $uid — Distance: $distance")

                        val userInfo = Triple(name, age, profilePic)
                        val isMutualMatch = uid in myMatches && currentUserId in theirMatches
                        val isPotential = uid in myMatches && currentUserId !in theirMatches &&
                                distance <= myRadius && distance <= theirRadius

                        if (isMutualMatch) {
                            Log.d("MatchDebug", "✅ Confirmed match: $uid")
                            confirmed.add(userInfo to uid)
                        } else if (isPotential) {
                            Log.d("MatchDebug", "➕ Potential match: $uid")
                            potential.add(userInfo to uid)
                        } else {
                            Log.d("MatchDebug", "❌ No match or out of range: $uid")
                        }
                    }

                    val (confirmedProfiles, confirmedIds) = confirmed.unzipOrEmpty()
                    val (potentialProfiles, potentialIds) = potential.unzipOrEmpty()

                    Log.d("MatchDebug", "Final confirmed: $confirmedProfiles")
                    Log.d("MatchDebug", "Final potential: $potentialProfiles")

                    attachAdapters(confirmedProfiles, confirmedIds, potentialProfiles, potentialIds)
                }
            }
        }
    }

    private fun <T, U> List<Pair<T, U>>.unzipOrEmpty(): Pair<List<T>, List<U>> {
        return if (this.isNotEmpty()) this.unzip() else Pair(emptyList(), emptyList())
    }

    private fun attachAdapters(
        confirmedProfiles: List<Triple<String, Int, String>>,
        confirmedIds: List<String>,
        potentialProfiles: List<Triple<String, Int, String>>,
        potentialIds: List<String>
    ) {
        binding.confirmedMatchesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.confirmedMatchesRecycler.adapter = MatchesAdapter(confirmedProfiles, confirmedIds)

        binding.matchesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.matchesRecycler.adapter = MatchesAdapter(potentialProfiles, potentialIds)
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 3958.8
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

    class MatchesAdapter(
        private val data: List<Triple<String, Int, String>>,
        private val userIds: List<String>
    ) : RecyclerView.Adapter<MatchesAdapter.MatchViewHolder>() {

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

            holder.itemView.setOnClickListener {
                val fragment = MatchProfileFragment().apply {
                    arguments = Bundle().apply {
                        putString("userId", userIds[position])
                    }
                }
                (holder.itemView.context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager
                    ?.beginTransaction()
                    ?.replace(R.id.fragmentContainer, fragment)
                    ?.addToBackStack(null)
                    ?.commit()
            }
        }

        override fun getItemCount() = data.size
    }
}