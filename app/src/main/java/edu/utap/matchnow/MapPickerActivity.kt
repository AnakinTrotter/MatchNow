package edu.utap.matchnow

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.*
import java.util.Locale

class MapPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var saveButton: Button

    // Variables to hold the chosen location (display string and coordinates).
    private var selectedDisplayLocation: String? = null
    private var selectedCoordinates: LatLng? = null

    // Coroutine scope for background operations.
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide the app bar for a full-screen experience.
        supportActionBar?.hide()
        setContentView(R.layout.activity_map_picker)

        // Initialize UI elements.
        searchEditText = findViewById(R.id.searchEditText)
        searchButton = findViewById(R.id.searchButton)
        saveButton = findViewById(R.id.saveButton)
        // Disable the Save button until a location is chosen.
        saveButton.isEnabled = false

        // Register the permission launcher.
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                try {
                    if (::googleMap.isInitialized) {
                        googleMap.isMyLocationEnabled = true
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            } else {
                Toast.makeText(
                    this,
                    "Location permission not granted. Current location will not be shown.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Set up search button.
        searchButton.setOnClickListener {
            val query = searchEditText.text.toString()
            if (query.isNotBlank()) {
                scope.launch { searchForLocation(query) }
            } else {
                Toast.makeText(this, "Please enter a location to search.", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up save button.
        saveButton.setOnClickListener {
            if (selectedDisplayLocation != null && selectedCoordinates != null) {
                val resultIntent = Intent().apply {
                    putExtra("selectedLocation", selectedDisplayLocation)
                    putExtra("selectedLat", selectedCoordinates!!.latitude)
                    putExtra("selectedLng", selectedCoordinates!!.longitude)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "No location selected.", Toast.LENGTH_SHORT).show()
            }
        }

        // Initialize the map fragment.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Center on a default location (San Francisco, for example).
        val defaultLocation = LatLng(37.7749, -122.4194)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))

        // Check and request location permission.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // When a user taps on the map, update marker and obtain city & state.
        googleMap.setOnMapClickListener { latLng ->
            // Clear previous markers and add a new one.
            googleMap.clear()
            googleMap.addMarker(MarkerOptions().position(latLng))
            // Update selected coordinates.
            selectedCoordinates = latLng
            // Get city and state using Geocoder.
            scope.launch {
                val cityState = getCityState(latLng)
                withContext(Dispatchers.Main) {
                    if (cityState != null) {
                        selectedDisplayLocation = cityState
                        // Optionally update the search text to reflect the selection.
                        searchEditText.setText(cityState)
                        // Enable the Save button since we have a valid selection.
                        saveButton.isEnabled = true
                    } else {
                        Toast.makeText(
                            this@MapPickerActivity,
                            "Unable to get city/state for this location",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // Suspend function to fetch "City, State" from a LatLng.
    private suspend fun getCityState(latLng: LatLng): String? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(this@MapPickerActivity, Locale.getDefault())
            val addresses: List<Address>? = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val city = address.locality ?: ""
                val state = address.adminArea ?: ""
                return@withContext "$city, $state".trim().takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    // Suspend function to search for a location based on a query.
    private suspend fun searchForLocation(query: String) = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(this@MapPickerActivity, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(query, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val latLng = LatLng(address.latitude, address.longitude)
                // Update the selected coordinates.
                selectedCoordinates = latLng
                // Get city and state as the display string.
                val display = withContext(Dispatchers.IO) { getCityState(latLng) }
                withContext(Dispatchers.Main) {
                    if (display != null) {
                        selectedDisplayLocation = display
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        googleMap.clear()
                        googleMap.addMarker(MarkerOptions().position(latLng))
                        saveButton.isEnabled = true
                    } else {
                        Toast.makeText(this@MapPickerActivity, "No valid address found.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MapPickerActivity, "No location found.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MapPickerActivity, "Error searching for location.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
