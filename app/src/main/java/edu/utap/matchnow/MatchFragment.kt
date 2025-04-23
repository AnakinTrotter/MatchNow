package edu.utap.matchnow

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
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
        val todayKey = getTodayPollId() // e.g. "poll-2025-04-23"
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

            binding.pollQuestion.text = question

            val now = Date()
            val timeDiff = now.time - timestamp.toDate().time
            val timeLeft = 60 * 60 * 1000 - timeDiff // 1 hour in ms

            if (timeLeft <= 0) {
                pollExpired = true
                showPollExpired()
            } else {
                startCountdown(timeLeft)
                showPollOptions(options)
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

    private fun showPollOptions(options: List<String>) {
        binding.pollOptionsContainer.removeAllViews()
        options.forEachIndexed { index, optionText ->
            val button = Button(requireContext()).apply {
                text = optionText
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
        pollRef.update("responses.$optionIndex", FieldValue.arrayUnion(userId))
        Toast.makeText(requireContext(), "Thanks for voting!", Toast.LENGTH_SHORT).show()
        showPollExpired()
        timer?.cancel()
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
        val recycler = binding.matchesRecycler
        recycler.layoutManager = LinearLayoutManager(requireContext())
        firestore.collection("users").get().addOnSuccessListener { result ->
            val users = result.documents.mapNotNull {
                val name = it.getString("name") ?: return@mapNotNull null
                val age = it.getLong("age")?.toInt() ?: return@mapNotNull null
                val profilePic = it.getString("profilePictureUrl") ?: return@mapNotNull null
                Triple(name, age, profilePic)
            }
            recycler.adapter = MatchesAdapter(users)
        }
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
