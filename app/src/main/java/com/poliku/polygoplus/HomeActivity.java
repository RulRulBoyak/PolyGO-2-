package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.navigation.NavigationBarView;
import com.poliku.polygoplus.fragments.HomeFragment;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Build;
import com.poliku.polygoplus.fragments.ExploreFragment;
import com.poliku.polygoplus.fragments.MessagesFragment;
import com.poliku.polygoplus.fragments.ProfileFragment;
import com.poliku.polygoplus.ui.HapticManager;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_home);

        NavigationBarView navView = findViewById(R.id.bottomNavigationView);
        if (navView == null) navView = findViewById(R.id.navigationRail);
        
        loadFragment(new HomeFragment());
        if (navView != null) navView.setSelectedItemId(R.id.nav_home);

        View mainView = findViewById(R.id.home_main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, 0, systemBars.right, 0);
                return insets;
            });
        }

        View bottomBar = findViewById(R.id.bottomAppBarContainer);
        if (bottomBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                // Ensure bottom nav is clear of gesture bar
                v.setPadding(0, 0, 0, systemBars.bottom); 
                return insets;
            });
        }
        
        if (navView instanceof com.google.android.material.navigationrail.NavigationRailView) {
            ViewCompat.setOnApplyWindowInsetsListener(navView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, systemBars.bottom); 
                return insets;
            });
        }

        if (navView != null) {
            final NavigationBarView finalNavView = navView;
            navView.setOnItemSelectedListener(item -> {
                Fragment fragment = null;
                int id = item.getItemId();
                
                if (id == R.id.nav_home) {
                    fragment = new HomeFragment();
                } else if (id == R.id.nav_explore) {
                    fragment = new ExploreFragment();
                } else if (id == R.id.nav_messages) {
                    fragment = new MessagesFragment();
                } else if (id == R.id.nav_profile) {
                    fragment = new ProfileFragment();
                }

                if (fragment != null) {
                    HapticManager.lightTap(finalNavView);
                    loadFragment(fragment);
                    return true;
                }
                return false;
            });
        }

        View fab = findViewById(R.id.fabAddProduct);
        if (fab == null && navView instanceof com.google.android.material.navigationrail.NavigationRailView) {
            com.google.android.material.navigationrail.NavigationRailView rail = (com.google.android.material.navigationrail.NavigationRailView) navView;
            View header = rail.getHeaderView();
            if (header != null) fab = header.findViewById(R.id.fabAddProductRail);
        }
        
        if (fab != null) {
            fab.setOnClickListener(v -> {
                HapticManager.lightTap(v);
                showListingTypeChoice();
            });
        }
        checkCampusService();
        requestNotificationPermission();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Stay updated")
                            .setMessage("Enable notifications to receive alerts about your deals, messages, and campus events.")
                            .setPositiveButton("Allow", (d, w) -> {
                                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                            })
                            .setNegativeButton("Maybe later", null)
                            .show();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                }
            }
        }
    }

    private void showListingTypeChoice() {
        if (!com.poliku.polygoplus.data.AppDataStore.isLoggedIn(this)) {
            android.widget.Toast.makeText(this, "Login required to post listings", android.widget.Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        dialog.setContentView(R.layout.layout_listing_type_choice);
        dialog.findViewById(R.id.choiceProduct).setOnClickListener(view -> {
            HapticManager.lightTap(view);
            dialog.dismiss();
            startActivity(new Intent(this, EditProductActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        dialog.findViewById(R.id.choiceService).setOnClickListener(view -> {
            HapticManager.lightTap(view);
            dialog.dismiss();
            startActivity(new Intent(this, AddServiceActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        dialog.show();
    }

    private void checkCampusService() {
        if (!com.poliku.polygoplus.network.ConnectivityHelper.isOnline(this)) {
            Intent i = new Intent(this, ErrorStateActivity.class);
            i.putExtra(ErrorStateActivity.EXTRA_MODE, "offline");
            startActivity(i);
            return;
        }
        com.poliku.polygoplus.network.NetworkApi.getStatus(new com.poliku.polygoplus.network.NetworkApi.Callback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                boolean maintenance = response.optBoolean("maintenance", false);
                com.poliku.polygoplus.data.AppDataStore.setMaintenanceMode(HomeActivity.this, maintenance);
                if (maintenance) {
                    Intent i = new Intent(HomeActivity.this, ErrorStateActivity.class);
                    i.putExtra(ErrorStateActivity.EXTRA_MODE, "maintenance");
                    startActivity(i);
                }
            }

            @Override
            public void onError(String message) {
                Intent i = new Intent(HomeActivity.this, ErrorStateActivity.class);
                i.putExtra(ErrorStateActivity.EXTRA_MODE, "offline");
                startActivity(i);
            }
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
