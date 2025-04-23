package edu.utap.matchnow

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
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
import java.util.Locale

class MapPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap

    // Launcher for requesting location permission.
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_picker)

        // Register the permission launcher.
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                // If permission is granted, enable the My Location layer.
                if (::googleMap.isInitialized) {
                    try {
                        googleMap.isMyLocationEnabled = true
                    } catch (e: SecurityException) {
                        e.printStackTrace()
                    }
                }
            } else {
                Toast.makeText(
                    this,
                    "Location permission not granted. Current location will not be shown.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Initialize the map fragment.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Optionally, center the map on a default location (e.g., San Francisco).
        val defaultLocation = LatLng(37.7749, -122.4194)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))

        // Check for location permission if you want to show the “My Location” layer.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
        } else {
            // Request the location permission.
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Set a tap listener to let the user select a location.
        googleMap.setOnMapClickListener { latLng ->
            // Clear any existing markers and add a new marker at the tapped location.
            googleMap.clear()
            googleMap.addMarker(MarkerOptions().position(latLng))

            // Use Geocoder to get an address for the tapped location.
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses: List<Address>? = try {
                geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                // Build a location string (customize this as needed).
                val city = address.locality ?: ""
                val state = address.adminArea ?: ""
                val postalCode = address.postalCode ?: ""
                val locationString = "$city, $state, $postalCode"

                // Return the result to the calling Activity.
                val resultIntent = Intent().apply {
                    putExtra("selectedLocation", locationString)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "Unable to get address for this location", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
