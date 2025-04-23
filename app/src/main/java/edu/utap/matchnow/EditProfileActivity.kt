package edu.utap.matchnow

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import edu.utap.matchnow.databinding.ActivityEditProfileBinding

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val firestore = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser
    private val userDocRef get() = firestore.collection("users").document(currentUser!!.uid)

    // Local URIs for new images (if chosen)
    private var newProfilePicUri: Uri? = null
    private var newPhotoUri1: Uri? = null
    private var newPhotoUri2: Uri? = null
    private var newPhotoUri3: Uri? = null

    // ActivityResultLaunchers for image picking
    private lateinit var profilePicLauncher: ActivityResultLauncher<Intent>
    private lateinit var photo1Launcher: ActivityResultLauncher<Intent>
    private lateinit var photo2Launcher: ActivityResultLauncher<Intent>
    private lateinit var photo3Launcher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Register image pickers with explicit lambda parameter names.
        profilePicLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                newProfilePicUri = result.data?.data
                newProfilePicUri?.let { uri ->
                    Glide.with(this).load(uri).into(binding.profilePicture)
                }
            }
        }
        photo1Launcher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                newPhotoUri1 = result.data?.data
                newPhotoUri1?.let { uri ->
                    Glide.with(this).load(uri).into(binding.photo1)
                }
            }
        }
        photo2Launcher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                newPhotoUri2 = result.data?.data
                newPhotoUri2?.let { uri ->
                    Glide.with(this).load(uri).into(binding.photo2)
                }
            }
        }
        photo3Launcher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                newPhotoUri3 = result.data?.data
                newPhotoUri3?.let { uri ->
                    Glide.with(this).load(uri).into(binding.photo3)
                }
            }
        }

        // Load existing profile data from Firestore (if it exists)
        currentUser?.let {
            userDocRef.get().addOnSuccessListener { document ->
                if (document.exists()) {
                    binding.editTextName.setText(document.getString("name") ?: "")
                    binding.editTextAge.setText(document.getLong("age")?.toString() ?: "")
                    binding.editTextLoveLanguage.setText(document.getString("loveLanguage") ?: "")
                    binding.editTextBio.setText(document.getString("bio") ?: "")
                    binding.editTextLocation.setText(document.getString("location") ?: "")

                    val profilePicUrl = document.getString("profilePictureUrl")
                    val photos = document.get("photos") as? List<*>
                    if (!profilePicUrl.isNullOrEmpty()) {
                        Glide.with(this).load(profilePicUrl).into(binding.profilePicture)
                    }
                    if (photos != null && photos.isNotEmpty()) {
                        // Since photos is non-empty, load the first photo directly.
                        Glide.with(this).load(photos[0]).into(binding.photo1)
                        if (photos.size > 1) {
                            Glide.with(this).load(photos[1]).into(binding.photo2)
                        }
                        if (photos.size > 2) {
                            Glide.with(this).load(photos[2]).into(binding.photo3)
                        }
                    }
                }
            }
        }

        // Set click listeners for image selection
        binding.profilePicture.setOnClickListener { launchImagePicker(profilePicLauncher) }
        binding.photo1.setOnClickListener { launchImagePicker(photo1Launcher) }
        binding.photo2.setOnClickListener { launchImagePicker(photo2Launcher) }
        binding.photo3.setOnClickListener { launchImagePicker(photo3Launcher) }

        // Save Changes button: update Firestore and Storage
        binding.saveChangesButton.setOnClickListener { saveProfile() }
    }

    private fun launchImagePicker(launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        launcher.launch(intent)
    }

    private fun saveProfile() {
        binding.saveChangesButton.isEnabled = false

        // Gather inputs
        val name = binding.editTextName.text.toString().trim()
        val age = binding.editTextAge.text.toString().toIntOrNull() ?: 0
        val loveLanguage = binding.editTextLoveLanguage.text.toString().trim()
        val bio = binding.editTextBio.text.toString().trim()
        val location = binding.editTextLocation.text.toString().trim()

        val updates = hashMapOf<String, Any>(
            "name" to name,
            "age" to age,
            "loveLanguage" to loveLanguage,
            "bio" to bio,
            "location" to location
        )

        // Prepare image upload tasks if any new images have been selected.
        val uploadTasks = mutableListOf<Task<Uri>>()
        val uid = currentUser!!.uid

        if (newProfilePicUri != null) {
            uploadTasks.add(uploadImage(newProfilePicUri!!, "users/$uid/profilePicture.jpg"))
        }
        if (newPhotoUri1 != null) {
            uploadTasks.add(uploadImage(newPhotoUri1!!, "users/$uid/photo1.jpg"))
        }
        if (newPhotoUri2 != null) {
            uploadTasks.add(uploadImage(newPhotoUri2!!, "users/$uid/photo2.jpg"))
        }
        if (newPhotoUri3 != null) {
            uploadTasks.add(uploadImage(newPhotoUri3!!, "users/$uid/photo3.jpg"))
        }

        if (uploadTasks.isEmpty()) {
            // No new images selected; update Firestore directly.
            userDocRef.set(updates, SetOptions.merge()).addOnCompleteListener {
                binding.saveChangesButton.isEnabled = true
                finish() // Close activity after saving.
            }
        } else {
            Tasks.whenAllSuccess<Uri>(uploadTasks)
                .addOnSuccessListener { results ->
                    var profilePicUrl: String? = null
                    val photoUrls = mutableListOf<String>()

                    // Build a list of the URIs in the order they were uploaded.
                    val uriList = listOf(newProfilePicUri, newPhotoUri1, newPhotoUri2, newPhotoUri3)
                    var resultIndex = 0
                    uriList.forEachIndexed { idx, uri ->
                        if (uri != null) {
                            val downloadUrl = (results[resultIndex] as Uri).toString()
                            resultIndex++
                            // The first URI (newProfilePicUri) is used for profilePictureUrl
                            if (idx == 0) {
                                profilePicUrl = downloadUrl
                            } else {
                                photoUrls.add(downloadUrl)
                            }
                        }
                    }
                    profilePicUrl?.let { updates["profilePictureUrl"] = it }
                    if (photoUrls.isNotEmpty()) {
                        updates["photos"] = photoUrls
                    }
                    userDocRef.set(updates, SetOptions.merge()).addOnCompleteListener {
                        binding.saveChangesButton.isEnabled = true
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    binding.saveChangesButton.isEnabled = true
                    Log.e("EditProfileActivity", "Error uploading images", e)
                }
        }
    }

    private fun uploadImage(uri: Uri, path: String): Task<Uri> {
        val ref = storage.reference.child(path)
        return ref.putFile(uri)
            .continueWithTask { task: Task<UploadTask.TaskSnapshot> ->
                if (!task.isSuccessful) {
                    throw task.exception ?: Exception("Upload failed")
                }
                ref.downloadUrl
            }
    }
}
