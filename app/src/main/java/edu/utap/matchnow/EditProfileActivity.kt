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

    // Local URIs for newly selected images
    private var newProfilePicUri: Uri? = null
    private var newPhotoUri1: Uri? = null
    private var newPhotoUri2: Uri? = null
    private var newPhotoUri3: Uri? = null

    // ActivityResultLaunchers for image picking and location selection
    private lateinit var profilePicLauncher: ActivityResultLauncher<Intent>
    private lateinit var photo1Launcher: ActivityResultLauncher<Intent>
    private lateinit var photo2Launcher: ActivityResultLauncher<Intent>
    private lateinit var photo3Launcher: ActivityResultLauncher<Intent>
    private lateinit var locationPickerLauncher: ActivityResultLauncher<Intent>

    // Predefined list of love languages
    private val loveLanguages = listOf(
        "Words of Affirmation",
        "Acts of Service",
        "Receiving Gifts",
        "Quality Time",
        "Physical Touch"
    )

    // Variables to hold the selected location coordinates
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null

    // Store existing data from Firestore
    private var existingProfilePicUrl: String? = null
    private var existingPhotoUrls = mutableListOf<String>()
    private var existingLat: Double? = null
    private var existingLng: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide the app bar for a full-screen experience
        supportActionBar?.hide()
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Disable manual typing in the location EditText
        binding.editTextLocation.isFocusable = false
        binding.editTextLocation.isClickable = true

        // Set up the love language spinner
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            loveLanguages
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLoveLanguage.adapter = adapter

        // Register image pickers
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

        // Register the location picker launcher
        locationPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
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

        // Load existing profile data from Firestore
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

                    val firestoreLocation = document.getString("location") ?: ""
                    binding.editTextLocation.setText(firestoreLocation)

                    existingLat = document.getDouble("lat")
                    existingLng = document.getDouble("lng")

                    binding.editTextSearchRadius.setText(document.getLong("searchRadius")?.toString() ?: "")

                    // Existing profile picture
                    val profilePicUrl = document.getString("profilePictureUrl")
                    if (!profilePicUrl.isNullOrEmpty()) {
                        existingProfilePicUrl = profilePicUrl
                        Glide.with(this).load(profilePicUrl).into(binding.profilePicture)
                    }

                    // Existing photos
                    val photos = document.get("photos") as? List<*>
                    if (photos != null) {
                        existingPhotoUrls = photos.filterIsInstance<String>().toMutableList()

                        if (existingPhotoUrls.isNotEmpty()) {
                            Glide.with(this).load(existingPhotoUrls[0]).into(binding.photo1)
                        }
                        if (existingPhotoUrls.size > 1) {
                            Glide.with(this).load(existingPhotoUrls[1]).into(binding.photo2)
                        }
                        if (existingPhotoUrls.size > 2) {
                            Glide.with(this).load(existingPhotoUrls[2]).into(binding.photo3)
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

        // Set click listener for selecting location on map
        binding.selectLocationButton.setOnClickListener {
            val intent = Intent(this, MapPickerActivity::class.java)
            locationPickerLauncher.launch(intent)
        }

        // Save Changes button
        binding.saveChangesButton.setOnClickListener { saveProfile() }
    }

    private fun launchImagePicker(launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        launcher.launch(intent)
    }

    private fun saveProfile() {
        binding.saveChangesButton.isEnabled = false

        val name = binding.editTextName.text.toString().trim()
        val age = binding.editTextAge.text.toString().toIntOrNull()
        val loveLanguage = binding.spinnerLoveLanguage.selectedItem.toString()
        val bio = binding.editTextBio.text.toString().trim()
        val location = binding.editTextLocation.text.toString().trim()
        val searchRadius = binding.editTextSearchRadius.text.toString().toIntOrNull()

        // Final lat/lng (either newly selected or existing)
        val finalLat = selectedLat ?: existingLat
        val finalLng = selectedLng ?: existingLng

        // VALIDATION
        if (name.isEmpty()) {
            Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
            binding.saveChangesButton.isEnabled = true
            return
        }
        if (age == null || age < 18) {
            Toast.makeText(this, "Age must be at least 18", Toast.LENGTH_SHORT).show()
            binding.saveChangesButton.isEnabled = true
            return
        }
        if (bio.isEmpty()) {
            Toast.makeText(this, "Bio is required", Toast.LENGTH_SHORT).show()
            binding.saveChangesButton.isEnabled = true
            return
        }
        if (finalLat == null || finalLng == null || location.isEmpty()) {
            Toast.makeText(this, "Location must be selected (map or existing)", Toast.LENGTH_SHORT).show()
            binding.saveChangesButton.isEnabled = true
            return
        }
        if (searchRadius == null || searchRadius < 1) {
            Toast.makeText(this, "Search Radius must be a positive number", Toast.LENGTH_SHORT).show()
            binding.saveChangesButton.isEnabled = true
            return
        }

        // Profile picture required
        val hasProfilePic = (newProfilePicUri != null || existingProfilePicUrl != null)
        if (!hasProfilePic) {
            Toast.makeText(this, "Profile picture is required", Toast.LENGTH_SHORT).show()
            binding.saveChangesButton.isEnabled = true
            return
        }

        // Collect new photos
        val newPhotoUris = mutableListOf<Uri>()
        if (newPhotoUri1 != null) newPhotoUris.add(newPhotoUri1!!)
        if (newPhotoUri2 != null) newPhotoUris.add(newPhotoUri2!!)
        if (newPhotoUri3 != null) newPhotoUris.add(newPhotoUri3!!)

        // If user has X existing photos, we need enough to still have at least 3 total
        val finalPhotoCount = existingPhotoUrls.size + newPhotoUris.size
        if (finalPhotoCount < 3) {
            Toast.makeText(this, "You must have at least 3 photos total", Toast.LENGTH_SHORT).show()
            binding.saveChangesButton.isEnabled = true
            return
        }

        val updates = hashMapOf<String, Any>(
            "name" to name,
            "age" to age,
            "loveLanguage" to loveLanguage,
            "bio" to bio,
            "location" to location,
            "lat" to finalLat,
            "lng" to finalLng,
            "searchRadius" to searchRadius
        )

        val uid = currentUser!!.uid

        // Build tasks for newly selected images only
        val uploadTasks = mutableListOf<Task<Uri>>()

        // 1) If there's a new profile pic
        newProfilePicUri?.let { uri ->
            uploadTasks.add(uploadImage(uri, "users/$uid/profilePicture.jpg"))
        }

        // 2) For new photos
        newPhotoUris.forEachIndexed { idx, photoUri ->
            uploadTasks.add(uploadImage(photoUri, "users/$uid/photo$idx.jpg"))
        }

        if (uploadTasks.isEmpty()) {
            // No new uploads, just store existing profile pic + existing photos
            updates["profilePictureUrl"] = existingProfilePicUrl!!
            updates["photos"] = existingPhotoUrls
            userDocRef.set(updates, SetOptions.merge()).addOnCompleteListener {
                binding.saveChangesButton.isEnabled = true
                setResult(Activity.RESULT_OK)
                finish()
            }
        } else {
            // We have new uploads
            Tasks.whenAllSuccess<Uri>(uploadTasks)
                .addOnSuccessListener { results ->
                    var resultIndex = 0

                    // Decide final profile pic
                    val finalProfilePicUrl = if (newProfilePicUri != null) {
                        val uri = results[resultIndex] as Uri
                        resultIndex++
                        uri.toString()
                    } else {
                        existingProfilePicUrl ?: ""
                    }

                    // Gather newly uploaded photo URLs in the order they were uploaded
                    val newlyUploadedPhotoUrls = mutableListOf<String>()
                    // The next results in 'results' are the new photos
                    while (resultIndex < results.size) {
                        newlyUploadedPhotoUrls.add((results[resultIndex] as Uri).toString())
                        resultIndex++
                    }

                    // Make sure existingPhotoUrls has at least 3 slots
                    // (If it has fewer, pad so we can safely do [0],[1],[2])
                    while (existingPhotoUrls.size < 3) {
                        existingPhotoUrls.add("")
                    }

                    // Now replace whichever slots actually got new URIs
                    // We'll track how many newPhotoUris we've used so far
                    var newlyUploadedIndex = 0

                    // If user picked a new photo for slot 1, replace existingPhotoUrls[0]
                    if (newPhotoUri1 != null) {
                        existingPhotoUrls[0] = newlyUploadedPhotoUrls[newlyUploadedIndex]
                        newlyUploadedIndex++
                    }
                    // If user picked a new photo for slot 2, replace existingPhotoUrls[1]
                    if (newPhotoUri2 != null) {
                        existingPhotoUrls[1] = newlyUploadedPhotoUrls[newlyUploadedIndex]
                        newlyUploadedIndex++
                    }
                    // If user picked a new photo for slot 3, replace existingPhotoUrls[2]
                    if (newPhotoUri3 != null) {
                        existingPhotoUrls[2] = newlyUploadedPhotoUrls[newlyUploadedIndex]
                        newlyUploadedIndex++
                    }

                    updates["profilePictureUrl"] = finalProfilePicUrl
                    updates["photos"] = existingPhotoUrls

                    userDocRef.set(updates, SetOptions.merge()).addOnCompleteListener {
                        binding.saveChangesButton.isEnabled = true
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    binding.saveChangesButton.isEnabled = true
                    Log.e("EditProfileActivity", "Error uploading images", e)
                    Toast.makeText(this, "Image upload failed. Please try again.", Toast.LENGTH_SHORT).show()
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
