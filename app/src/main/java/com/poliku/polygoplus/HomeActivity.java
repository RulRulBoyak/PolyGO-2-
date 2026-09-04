package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.poliku.polygoplus.fragments.HomeFragment;
import com.poliku.polygoplus.fragments.ExploreFragment; // We will use this as our 'Explore' fragment
import com.poliku.polygoplus.fragments.MessagesFragment;
import com.poliku.polygoplus.fragments.ProfileFragment;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        EdgeToEdge.enable(this);
        com.poliku.polygoplus.data.AppDataStore.initialize(this);
        com.poliku.polygoplus.network.NetworkApi.init(this);
        if (!com.poliku.polygoplus.data.AppDataStore.hasSeenOnboarding(this)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        
        // Set default fragment
        loadFragment(new HomeFragment());
        bottomNavigationView.setSelectedItemId(R.id.nav_home);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, 0);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomAppBar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Add full system bar bottom padding to ensure text and icons are clear of the gesture bar
            v.setPadding(0, 0, 0, systemBars.bottom); 
            return insets;
        });

        bottomNavigationView.setOnItemSelectedListener(item -> {
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
            } else if (id == R.id.nav_placeholder) {
                return false;
            }

            if (fragment != null) {
                loadFragment(fragment);
                return true;
            }
            return false;
        });

        findViewById(R.id.fabAddProduct).setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            if (!com.poliku.polygoplus.data.AppDataStore.isLoggedIn(this)) {
                android.widget.Toast.makeText(this, "Login required to post listings", android.widget.Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                return;
            }
            com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
            dialog.setContentView(R.layout.layout_listing_type_choice);
            dialog.findViewById(R.id.choiceProduct).setOnClickListener(view -> {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                dialog.dismiss();
                startActivity(new Intent(this, EditProductActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            });
            dialog.findViewById(R.id.choiceService).setOnClickListener(view -> {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                dialog.dismiss();
                startActivity(new Intent(this, AddServiceActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            });
            dialog.show();
        });
        checkCampusService();
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
