package com.poliku.polygoplus;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
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
    private Marker myMarker;
    private String threadId, listingId, sellerId, otherName, landmark;
    private boolean dealActive;
    private ObjectAnimator pulseAnimator;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private FusedLocationProviderClient fusedLocationClient;
    private Runnable pendingLocationAction;
    private ExtendedFloatingActionButton btnEmergency;

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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                    if (pendingLocationAction != null) {
                        Runnable action = pendingLocationAction;
                        pendingLocationAction = null;
                        action.run();
                    }
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

        findViewById(R.id.btnShareLocation).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (!dealActive) {
                Toast.makeText(this, getString(R.string.safe_meetup_location_off), Toast.LENGTH_LONG).show();
                return;
            }
            if (!hasLocationPermission()) {
                pendingLocationAction = this::shareCurrentLocation;
                locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
            } else {
                shareCurrentLocation();
            }
        });

        btnEmergency = findViewById(R.id.btnEmergency);
        btnEmergency.setOnClickListener(v -> triggerEmergency());

        // An active meetup may share a live position, so ask for the permission
        // up front instead of surprising the user mid-flow.
        if (dealActive && !hasLocationPermission()) {
            pendingLocationAction = this::refreshMyLocation;
            locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION});
        } else if (mMap != null && dealActive && hasLocationPermission()) {
            refreshMyLocation();
        }

        startEmergencyPulse();
    }

    private boolean hasLocationPermission() {
        boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return fine || coarse;
    }

    private void triggerEmergency() {
        HapticManager.error(this);
        if (!AppDataStore.hasSeenMeetupDisclosure(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.safe_meetup_disclosure_title)
                    .setMessage(R.string.safe_meetup_disclosure_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.safe_meetup_disclosure_understood, (d, w) -> {
                        AppDataStore.markMeetupDisclosureSeen(this);
                        new MaterialAlertDialogBuilder(this)
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
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.safe_meetup_emergency_title)
                .setMessage(R.string.safe_meetup_emergency_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.safe_meetup_emergency_send, (d, w) -> requestEmergencyLocation())
                .show();
    }

    private void requestEmergencyLocation() {
        if (hasLocationPermission()) {
            sendEmergencyAlert();
        } else {
            pendingLocationAction = this::sendEmergencyAlert;
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

        if (hasLocationPermission()) {
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

        polyGoRepository.sendMessage(userId, threadId, listingId, sellerId, message, new Callback<PolyGoApi.SendMessageResponse>() {
            @Override public void onResponse(Call<PolyGoApi.SendMessageResponse> call, Response<PolyGoApi.SendMessageResponse> response) {
                Toast.makeText(SafeMeetupActivity.this, R.string.safe_meetup_emergency_sent, Toast.LENGTH_LONG).show();
            }
            @Override public void onFailure(Call<PolyGoApi.SendMessageResponse> call, Throwable t) {
                AppDataStore.sendMessage(SafeMeetupActivity.this, threadId, message);
                Toast.makeText(SafeMeetupActivity.this, R.string.safe_meetup_emergency_sent, Toast.LENGTH_LONG).show();
            }
        });

        polyGoRepository.notifyPksSecurity(userId, threadId, listingId, safeLandmark, latitude, longitude, new Callback<BaseResponse>() {
            @Override public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) { }
            @Override public void onFailure(Call<BaseResponse> call, Throwable t) { }
        });
    }

    /** Send a non-emergency "I am here" chat message with the current coordinates. */
    private void shareCurrentLocation() {
        String userId = AppDataStore.userId(this);
        String safeLandmark = landmark == null || landmark.trim().isEmpty() ? "PKS Library" : landmark;

        if (userId == null || threadId == null || sellerId == null) {
            Toast.makeText(this, getString(R.string.safe_meetup_share_sent), Toast.LENGTH_LONG).show();
            return;
        }

        requestCurrentLocation((lat, lng) -> {
            final String text;
            if (lat != null && lng != null) {
                text = String.format(getString(R.string.safe_meetup_share_location),
                        AppDataStore.userName(this), safeLandmark)
                        + " Location: https://maps.google.com/?q=" + lat + "," + lng + ".";
            } else {
                text = String.format(getString(R.string.safe_meetup_share_location_nofix),
                        AppDataStore.userName(this), safeLandmark);
            }

            polyGoRepository.sendMessage(userId, threadId, listingId, sellerId, text, new Callback<PolyGoApi.SendMessageResponse>() {
                @Override public void onResponse(Call<PolyGoApi.SendMessageResponse> call, Response<PolyGoApi.SendMessageResponse> response) {
                    Toast.makeText(SafeMeetupActivity.this, R.string.safe_meetup_share_sent, Toast.LENGTH_LONG).show();
                }
                @Override public void onFailure(Call<PolyGoApi.SendMessageResponse> call, Throwable t) {
                    AppDataStore.sendMessage(SafeMeetupActivity.this, threadId, text);
                    Toast.makeText(SafeMeetupActivity.this, R.string.safe_meetup_share_sent, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void refreshMyLocation() {
        requestCurrentLocation((lat, lng) -> {
            if (lat == null || lng == null || mMap == null) {
                return;
            }
            LatLng position = new LatLng(lat, lng);
            if (myMarker == null) {
                myMarker = mMap.addMarker(new MarkerOptions()
                        .position(position)
                        .title(getString(R.string.safe_meetup_your_position))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            } else {
                myMarker.setPosition(position);
            }
            if (mMap.getCameraPosition() == null || mMap.getCameraPosition().zoom < 15f) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 16f));
            }
        });
    }

    private void requestCurrentLocation(LocationResult result) {
        if (!hasLocationPermission()) {
            result.onResult(null, null);
            return;
        }
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        result.onResult(loc.getLatitude(), loc.getLongitude());
                    } else {
                        requestFreshFix(result);
                    }
                })
                .addOnFailureListener(e -> requestFreshFix(result));
    }

    private void requestFreshFix(LocationResult result) {
        try {
            CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setDurationMillis(10000)
                    .build();
            fusedLocationClient.getCurrentLocation(request, new CancellationTokenSource().getToken())
                    .addOnSuccessListener(loc -> result.onResult(loc == null ? null : loc.getLatitude(), loc == null ? null : loc.getLongitude()))
                    .addOnFailureListener(e -> result.onResult(null, null))
                    .addOnCanceledListener(() -> result.onResult(null, null));
        } catch (Exception ignored) {
            result.onResult(null, null);
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
        View btn = btnEmergency;
        if (btn == null) {
            return;
        }
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

        // Map may become ready after the permission flow completed in onCreate.
        if (dealActive && hasLocationPermission()) {
            refreshMyLocation();
        }
    }
}