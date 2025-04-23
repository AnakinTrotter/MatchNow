package edu.utap.matchnow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import edu.utap.matchnow.databinding.FragmentChatBinding
import java.util.*

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.chatRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.chatRecycler.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        loadChats()
    }

    private fun createChatId(uid1: String, uid2: String): String {
        return listOf(uid1, uid2).sortedDescending().joinToString("-")
    }

    private fun loadChats() {
        val uid = currentUserId ?: return

        firestore.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            val chatPartners = userDoc.get("chatsWith") as? List<*> ?: emptyList<Any>()
            if (chatPartners.isEmpty()) {
                binding.emptyText.visibility = View.VISIBLE
                binding.chatRecycler.visibility = View.GONE
                return@addOnSuccessListener
            }

            val chatList = mutableListOf<ChatDisplayInfo>()
            var loadedCount = 0

            for (otherIdAny in chatPartners) {
                val otherId = otherIdAny as? String ?: continue
                val chatId = createChatId(uid, otherId)
                val chatRef = firestore.collection("chats").document(chatId)

                chatRef.get().addOnSuccessListener { chatDoc ->
                    val lastMessage = chatDoc.getString("lastMessage") ?: ""
                    val lastUpdated = chatDoc.getTimestamp("lastUpdated")?.toDate() ?: Date(0)

                    firestore.collection("users").document(otherId).get().addOnSuccessListener { userDoc ->
                        val name = userDoc.getString("name") ?: "Unknown"
                        val profilePic = userDoc.getString("profilePictureUrl")
                        chatList.add(
                            ChatDisplayInfo(
                                chatId = chatId,
                                otherUserId = otherId,
                                otherUserName = name,
                                lastMessage = lastMessage,
                                profilePictureUrl = profilePic,
                                lastUpdated = lastUpdated
                            )
                        )
                        loadedCount++
                        if (loadedCount == chatPartners.size) {
                            chatList.sortByDescending { it.lastUpdated }
                            binding.chatRecycler.adapter = ChatListAdapter(chatList)
                            binding.emptyText.visibility = View.GONE
                            binding.chatRecycler.visibility = View.VISIBLE
                        }
                    }
                }.addOnFailureListener {
                    loadedCount++
                }
            }
        }.addOnFailureListener {
            binding.emptyText.visibility = View.VISIBLE
            binding.chatRecycler.visibility = View.GONE
        }
    }

    data class ChatDisplayInfo(
        val chatId: String,
        val otherUserId: String,
        val otherUserName: String,
        val lastMessage: String,
        val profilePictureUrl: String?,
        val lastUpdated: Date
    )

    inner class ChatListAdapter(private val chats: List<ChatDisplayInfo>) :
        RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

        inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameView: TextView = view.findViewById(R.id.matchNameAge)
            val imageView: ImageView = view.findViewById(R.id.matchImage)
            val messageView: TextView = view.findViewById(R.id.lastMessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat, parent, false)
            return ChatViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val chat = chats[position]
            holder.nameView.text = chat.otherUserName
            holder.messageView.text = chat.lastMessage
            Glide.with(holder.imageView.context).load(chat.profilePictureUrl).into(holder.imageView)

            holder.itemView.setOnClickListener {
                val fragment = ChatMessageFragment.newInstance(chat.chatId, chat.otherUserId, chat.otherUserName)
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

        override fun getItemCount() = chats.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
