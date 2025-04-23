package edu.utap.matchnow

import android.app.Activity
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.util.Locale

class MapPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_picker)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        // Optionally center to a default location (e.g., San Francisco)
        val defaultLocation = LatLng(37.7749, -122.4194)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))

        googleMap.setOnMapClickListener { latLng ->
            // Clear any existing markers and add a new one.
            googleMap.clear()
            googleMap.addMarker(MarkerOptions().position(latLng))
            // Use Geocoder to get an address from the tapped location.
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses: List<Address>? = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address: Address = addresses[0]
                // Extract city, state, and postal code.
                val city = address.locality ?: ""
                val state = address.adminArea ?: ""
                val postalCode = address.postalCode ?: ""
                val locationString = "$city, $state, $postalCode"
                // Return result to the calling Activity.
                val resultIntent = Intent()
                resultIntent.putExtra("selectedLocation", locationString)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "Unable to get address for this location", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
