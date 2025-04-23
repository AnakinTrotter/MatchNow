package edu.utap.matchnow

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
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

    // Local URIs for new images.
    private var newProfilePicUri: Uri? = null
    private var newPhotoUri1: Uri? = null
    private var newPhotoUri2: Uri? = null
    private var newPhotoUri3: Uri? = null

    // ActivityResultLaunchers for image picking and location selection.
    private lateinit var profilePicLauncher: ActivityResultLauncher<Intent>
    private lateinit var photo1Launcher: ActivityResultLauncher<Intent>
    private lateinit var photo2Launcher: ActivityResultLauncher<Intent>
    private lateinit var photo3Launcher: ActivityResultLauncher<Intent>
    private lateinit var locationPickerLauncher: ActivityResultLauncher<Intent>

    // Predefined list of the 5 love languages.
    private val loveLanguages = listOf(
        "Words of Affirmation",
        "Acts of Service",
        "Receiving Gifts",
        "Quality Time",
        "Physical Touch"
    )

    // New variables to hold the selected location coordinates.
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide the app bar for a full-screen experience.
        supportActionBar?.hide()
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Disable manual typing in the location EditText.
        binding.editTextLocation.isFocusable = false
        binding.editTextLocation.isClickable = true

        // Set up the love language spinner.
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            loveLanguages
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLoveLanguage.adapter = adapter

        // Register image pickers.
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

        // Register the location picker launcher.
        locationPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Expect the returned location string (city, state)
                // along with selectedLat and selectedLng from MapPickerActivity.
                val selectedLocation = result.data?.getStringExtra("selectedLocation")
                val lat = result.data?.getDoubleExtra("selectedLat", 0.0)
                val lng = result.data?.getDoubleExtra("selectedLng", 0.0)
                if (selectedLocation != null && lat != null && lng != null) {
                    binding.editTextLocation.setText(selectedLocation)
                    selectedLat = lat
                    selectedLng = lng
                }
            }
        }

        // Load existing profile data from Firestore.
        currentUser?.let {
            userDocRef.get().addOnSuccessListener { document ->
                if (document.exists()) {
                    binding.editTextName.setText(document.getString("name") ?: "")
                    binding.editTextAge.setText(document.getLong("age")?.toString() ?: "")
                    val storedLoveLanguage = document.getString("loveLanguage") ?: ""
                    val spinnerPosition = loveLanguages.indexOf(storedLoveLanguage)
                    if (spinnerPosition >= 0) {
                        binding.spinnerLoveLanguage.setSelection(spinnerPosition)
                    }
                    binding.editTextBio.setText(document.getString("bio") ?: "")
                    binding.editTextLocation.setText(document.getString("location") ?: "")
                    binding.editTextSearchRadius.setText(document.getLong("searchRadius")?.toString() ?: "")

                    val profilePicUrl = document.getString("profilePictureUrl")
                    val photos = document.get("photos") as? List<*>
                    if (!profilePicUrl.isNullOrEmpty()) {
                        Glide.with(this).load(profilePicUrl).into(binding.profilePicture)
                    }
                    if (photos != null && photos.isNotEmpty()) {
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

        // Set click listeners for image selection.
        binding.profilePicture.setOnClickListener { launchImagePicker(profilePicLauncher) }
        binding.photo1.setOnClickListener { launchImagePicker(photo1Launcher) }
        binding.photo2.setOnClickListener { launchImagePicker(photo2Launcher) }
        binding.photo3.setOnClickListener { launchImagePicker(photo3Launcher) }

        // Set click listener for selecting location on map.
        binding.selectLocationButton.setOnClickListener {
            val intent = Intent(this, MapPickerActivity::class.java)
            locationPickerLauncher.launch(intent)
        }

        // Save Changes button.
        binding.saveChangesButton.setOnClickListener { saveProfile() }
    }

    private fun launchImagePicker(launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        launcher.launch(intent)
    }

    private fun saveProfile() {
        binding.saveChangesButton.isEnabled = false

        // Validate inputs.
        val name = binding.editTextName.text.toString().trim()
        val age = binding.editTextAge.text.toString().toIntOrNull() ?: 0
        if (age < 18) {
            Toast.makeText(this, "Age must be at least 18", Toast.LENGTH_SHORT).show()
            binding.saveChangesButton.isEnabled = true
            return
        }
        val loveLanguage = binding.spinnerLoveLanguage.selectedItem.toString()
        val bio = binding.editTextBio.text.toString().trim()
        // Location is provided only via map.
        val location = binding.editTextLocation.text.toString().trim()
        val searchRadius = binding.editTextSearchRadius.text.toString().toIntOrNull() ?: 0

        if (searchRadius < 1) {
            Toast.makeText(this, "Search Radius must be a positive number", Toast.LENGTH_SHORT).show()
            binding.saveChangesButton.isEnabled = true
            return
        }

        // Prepare fields to update.
        val updates = hashMapOf<String, Any>(
            "name" to name,
            "age" to age,
            "loveLanguage" to loveLanguage,
            "bio" to bio,
            "location" to location,
            "searchRadius" to searchRadius
        )
        // Add coordinates if available.
        if (selectedLat != null && selectedLng != null) {
            updates["lat"] = selectedLat!!
            updates["lng"] = selectedLng!!
        }

        // Prepare image upload tasks.
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
            userDocRef.set(updates, SetOptions.merge()).addOnCompleteListener {
                binding.saveChangesButton.isEnabled = true
                finish()
            }
        } else {
            Tasks.whenAllSuccess<Uri>(uploadTasks)
                .addOnSuccessListener { results ->
                    var profilePicUrl: String? = null
                    val photoUrls = mutableListOf<String>()
                    val uriList = listOf(newProfilePicUri, newPhotoUri1, newPhotoUri2, newPhotoUri3)
                    var resultIndex = 0
                    uriList.forEachIndexed { idx, uri ->
                        if (uri != null) {
                            val downloadUrl = (results[resultIndex] as Uri).toString()
                            resultIndex++
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
