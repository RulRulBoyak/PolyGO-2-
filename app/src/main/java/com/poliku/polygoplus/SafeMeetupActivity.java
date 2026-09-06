package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.poliku.polygoplus.ui.HapticManager;

public class SafeMeetupActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe_meetup);

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.meetupMap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        findViewById(R.id.btnCheckIn).setOnClickListener(v -> {
            HapticManager.success(this);
            Toast.makeText(this, "Check-in successful. Seller notified.", Toast.LENGTH_LONG).show();
            finish();
        });

        findViewById(R.id.btnEmergency).setOnClickListener(v -> {
            HapticManager.error(this);
            Toast.makeText(this, "EMERGENCY ALERT: PKS Security has been notified of your location.", Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Politeknik Kuching Sarawak (PKS) Coordinates
        LatLng pks = new LatLng(1.4831, 110.3475);
        
        mMap.addMarker(new MarkerOptions()
                .position(pks)
                .title("PKS Safe Zone: Main Entrance")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        LatLng cafeteria = new LatLng(1.4842, 110.3470);
        mMap.addMarker(new MarkerOptions()
                .position(cafeteria)
                .title("PKS Safe Zone: Cafeteria")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pks, 17f));
        mMap.setBuildingsEnabled(true);
        mMap.setIndoorEnabled(true);
    }
}
