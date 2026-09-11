package com.poliku.polygoplus;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.HapticManager;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class SafeMeetupActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static final String EXTRA_THREAD_ID = "thread_id";
    public static final String EXTRA_LISTING_ID = "listing_id";
    public static final String EXTRA_SELLER_ID = "seller_id";
    public static final String EXTRA_OTHER_NAME = "other_name";
    public static final String EXTRA_LANDMARK = "landmark";
    public static final String EXTRA_DEAL_ACTIVE = "deal_active";
    public static final String EXTRA_ARRIVED = "arrived";

    @Inject PolyGoRepository polyGoRepository;

    private GoogleMap mMap;
    private String threadId, listingId, sellerId, otherName, landmark;
    private boolean dealActive;
    private ObjectAnimator pulseAnimator;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    private static final LatLng PKS_CENTER = new LatLng(1.4831, 110.3475);
    private static final LatLng[] PKS_POIS = {
        new LatLng(1.4831, 110.3475),
        new LatLng(1.4842, 110.3470)
    };
    private static final String[] PKS_POI_NAMES = {"Main Entrance", "Cafeteria"};

    private interface LocationResult {
        void onResult(Double latitude, Double longitude);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe_meetup);

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                    boolean hasAny = false;
                    for (Boolean b : granted.values()) {
                        if (Boolean.TRUE.equals(b)) {
                            hasAny = true;
                            break;
                        }
                    }
                    sendEmergencyAlert();
                });

        threadId = getIntent().getStringExtra(EXTRA_THREAD_ID);
        listingId = getIntent().getStringExtra(EXTRA_LISTING_ID);
        sellerId = getIntent().getStringExtra(EXTRA_SELLER_ID);
        otherName = getIntent().getStringExtra(EXTRA_OTHER_NAME);
        landmark = getIntent().getStringExtra(EXTRA_LANDMARK);
        dealActive = getIntent().getBooleanExtra(EXTRA_DEAL_ACTIVE, false);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.meetupMap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        TextView tvPrivacy = findViewById(R.id.tvPrivacyState);
        if (dealActive) {
            tvPrivacy.setText(getString(R.string.safe_meetup_location_on));
        } else {
            tvPrivacy.setText(getString(R.string.safe_meetup_location_off));
        }

        findViewById(R.id.btnCheckIn).setOnClickListener(v -> {
            HapticManager.success(this);
            if (!dealActive) {
                Toast.makeText(this, getString(R.string.safe_meetup_location_off), Toast.LENGTH_LONG).show();
                return;
            }
            Intent result = new Intent();
            result.putExtra(EXTRA_ARRIVED, true);
            result.putExtra(EXTRA_LANDMARK, landmark == null || landmark.trim().isEmpty() ? "PKS Library" : landmark);
            setResult(RESULT_OK, result);
            finish();
        });

        findViewById(R.id.btnEmergency).setOnClickListener(v -> triggerEmergency());

        startEmergencyPulse();
    }

    private void triggerEmergency() {
        HapticManager.error(this);
        if (!AppDataStore.hasSeenMeetupDisclosure(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.safe_meetup_disclosure_title)
                    .setMessage(R.string.safe_meetup_disclosure_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.safe_meetup_disclosure_understood, (d, w) -> {
                        AppDataStore.markMeetupDisclosureSeen(this);
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.safe_meetup_emergency_title)
                                .setMessage(R.string.safe_meetup_emergency_confirm)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(R.string.safe_meetup_emergency_send, (d2, w2) -> requestEmergencyLocation())
                                .show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.safe_meetup_emergency_title)
                .setMessage(R.string.safe_meetup_emergency_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.safe_meetup_emergency_send, (d, w) -> requestEmergencyLocation())
                .show();
    }

    private void requestEmergencyLocation() {
        boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (fine || coarse) {
            sendEmergencyAlert();
        } else {
            locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private void sendEmergencyAlert() {
        String userId = AppDataStore.userId(this);
        String safeLandmark = landmark == null || landmark.trim().isEmpty() ? "PKS Library" : landmark;

        if (userId == null || threadId == null || sellerId == null) {
            Toast.makeText(this, R.string.safe_meetup_emergency_sent, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (fine || coarse) {
            requestCurrentLocation((lat, lng) -> dispatchEmergencyAlert(userId, listingId, safeLandmark, lat, lng));
        } else {
            dispatchEmergencyAlert(userId, listingId, safeLandmark, null, null);
        }
    }

    private void dispatchEmergencyAlert(String userId, String listingId, String safeLandmark, Double latitude, Double longitude) {
        String text = String.format(getString(R.string.safe_meetup_emergency), AppDataStore.userName(this), safeLandmark);
        if (latitude != null && longitude != null) {
            text += " Location: https://maps.google.com/?q=" + latitude + "," + longitude + ". " + locationContext(latitude, longitude);
        }
        final String message = text;

        polyGoRepository.sendMessage(userId, threadId, listingId, sellerId, message, new Callback<BaseResponse>() {
            @Override public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                Toast.makeText(SafeMeetupActivity.this, R.string.safe_meetup_emergency_sent, Toast.LENGTH_LONG).show();
            }
            @Override public void onFailure(Call<BaseResponse> call, Throwable t) {
                AppDataStore.sendMessage(SafeMeetupActivity.this, threadId, message);
                Toast.makeText(SafeMeetupActivity.this, R.string.safe_meetup_emergency_sent, Toast.LENGTH_LONG).show();
            }
        });

        polyGoRepository.notifyPksSecurity(userId, threadId, listingId, safeLandmark, latitude, longitude, new Callback<BaseResponse>() {
            @Override public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) { }
            @Override public void onFailure(Call<BaseResponse> call, Throwable t) { }
        });
    }

    private void requestCurrentLocation(LocationResult result) {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) {
            result.onResult(null, null);
            return;
        }
        Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (last != null) {
            result.onResult(last.getLatitude(), last.getLongitude());
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                lm.getCurrentLocation(LocationManager.FUSED_PROVIDER, null, getMainExecutor(), loc -> {
                    runOnUiThread(() -> result.onResult(loc == null ? null : loc.getLatitude(), loc == null ? null : loc.getLongitude()));
                });
            } catch (Exception ignored) {
                result.onResult(null, null);
            }
        } else {
            try {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, new LocationListener() {
                    @Override public void onLocationChanged(Location l) {
                        if (l != null) result.onResult(l.getLatitude(), l.getLongitude());
                        else result.onResult(null, null);
                    }
                    @Override public void onProviderEnabled(String provider) { }
                    @Override public void onProviderDisabled(String provider) { }
                }, null);
            } catch (Exception ignored) {
                result.onResult(null, null);
            }
        }
    }

    private String locationContext(double lat, double lng) {
        boolean inside = haversine(PKS_CENTER.latitude, PKS_CENTER.longitude, lat, lng) < 1500;
        String nearest = "Main Entrance";
        double best = Double.MAX_VALUE;
        for (int i = 0; i < PKS_POIS.length; i++) {
            double d = haversine(PKS_POIS[i].latitude, PKS_POIS[i].longitude, lat, lng);
            if (d < best) {
                best = d;
                nearest = PKS_POI_NAMES[i];
            }
        }
        return "Campus radius: " + (inside ? "inside" : "outside") + " PKS campus. Nearest landmark: " + nearest + ".";
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void startEmergencyPulse() {
        View btn = findViewById(R.id.btnEmergency);
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(btn,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.08f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.08f));
        pulseAnimator.setDuration(700);
        pulseAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseAnimator.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pulseAnimator != null) pulseAnimator.cancel();
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