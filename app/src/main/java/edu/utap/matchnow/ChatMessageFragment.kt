package edu.utap.matchnow

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ChatMessageFragment : Fragment() {

    private lateinit var chatId: String
    private lateinit var otherUserId: String
    private lateinit var otherUserName: String
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        fun newInstance(chatId: String, otherUserId: String, otherUserName: String) =
            ChatMessageFragment().apply {
                arguments = Bundle().apply {
                    putString("chatId", chatId)
                    putString("otherUserId", otherUserId)
                    putString("otherUserName", otherUserName)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatId = arguments?.getString("chatId") ?: ""
        otherUserId = arguments?.getString("otherUserId") ?: ""
        otherUserName = arguments?.getString("otherUserName") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_chat_message, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.messagesRecycler)
        val input = view.findViewById<EditText>(R.id.messageInput)
        val send = view.findViewById<ImageButton>(R.id.sendButton)
        val back = view.findViewById<ImageButton>(R.id.backButton)
        val title = view.findViewById<TextView>(R.id.chatTitle)

        title.text = otherUserName
        recycler.layoutManager = LinearLayoutManager(requireContext())

        val messages = mutableListOf<String>()
        val adapter = object : RecyclerView.Adapter<MessageViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
                val tv = TextView(parent.context)
                tv.setPadding(16, 8, 16, 8)
                return MessageViewHolder(tv)
            }

            override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
                (holder.itemView as TextView).text = messages[position]
            }

            override fun getItemCount() = messages.size
        }
        recycler.adapter = adapter

        // Load messages
        firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshots, _ ->
                messages.clear()
                snapshots?.forEach { doc ->
                    val sender = doc.getString("from")
                    val text = doc.getString("message") ?: ""
                    val time = doc.getTimestamp("timestamp")?.toDate()
                    val label = if (doc.id == "init") {
                        "Started a chat with $otherUserName at ${
                            DateFormat.format("MMM dd, hh:mm a", time)
                        }"
                    } else {
                        val prefix = if (sender == currentUserId) "You: " else "$otherUserName: "
                        "$prefix$text"
                    }
                    messages.add(label)
                }
                adapter.notifyDataSetChanged()
                recycler.scrollToPosition(messages.size - 1)
            }

        // Send message
        send.setOnClickListener {
            val msg = input.text.toString().trim()
            if (msg.isEmpty()) return@setOnClickListener
            val message = mapOf(
                "from" to currentUserId,
                "to" to otherUserId,
                "message" to msg,
                "timestamp" to Timestamp.now()
            )
            firestore.collection("chats").document(chatId)
                .collection("messages")
                .add(message)
            firestore.collection("chats").document(chatId).update(
                "lastMessage", msg,
                "lastUpdated", Timestamp.now()
            )
            input.setText("")
        }

        back.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
