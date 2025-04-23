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

    private fun loadChats() {
        val uid = currentUserId ?: return
        firestore.collection("chats")
            .whereArrayContains("users", uid)
            .orderBy("lastUpdated")
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    binding.emptyText.visibility = View.VISIBLE
                    binding.chatRecycler.visibility = View.GONE
                    return@addOnSuccessListener
                }

                val chats = result.documents.mapNotNull { doc ->
                    val otherId = (doc.get("users") as? List<*>)?.filterIsInstance<String>()?.firstOrNull { it != uid }
                    val lastMessage = doc.getString("lastMessage") ?: ""
                    if (otherId == null) return@mapNotNull null
                    Triple(doc.id, otherId, lastMessage)
                }

                binding.chatRecycler.adapter = ChatListAdapter(chats)
                binding.emptyText.visibility = View.GONE
                binding.chatRecycler.visibility = View.VISIBLE
            }
    }

    inner class ChatListAdapter(private val chats: List<Triple<String, String, String>>) :
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
            val (chatId, otherId, lastMsg) = chats[position]
            firestore.collection("users").document(otherId).get().addOnSuccessListener { user ->
                val name = user.getString("name") ?: "Unknown"
                val pic = user.getString("profilePictureUrl")
                holder.nameView.text = name
                holder.messageView.text = lastMsg
                Glide.with(holder.imageView.context).load(pic).into(holder.imageView)

                holder.itemView.setOnClickListener {
                    val fragment = ChatMessageFragment.newInstance(chatId, otherId, name)
                    requireActivity().supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, fragment)
                        .addToBackStack(null)
                        .commit()
                }
            }
        }

        override fun getItemCount() = chats.size
    }
}
