package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigationrail.NavigationRailView;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.fragments.HomeFragment;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Build;

import com.poliku.polygoplus.fragments.ExploreFragment;
import com.poliku.polygoplus.fragments.MessagesFragment;
import com.poliku.polygoplus.fragments.ProfileFragment;
import com.poliku.polygoplus.network.ConnectivityHelper;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.api.model.BaseResponse;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class HomeActivity extends AppCompatActivity {
    @Inject PolyGoRepository polyGoRepository;
    private int previousTabIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_home);

        NavigationBarView navView = findViewById(R.id.bottomNavigationView);
        if (navView == null) navView = findViewById(R.id.navigationRail);
        
        // Initial fragment load
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment(), 0);
        }

        View mainView = findViewById(R.id.home_main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
                return insets;
            });
        }
        
        if (navView instanceof NavigationRailView) {
            ViewCompat.setOnApplyWindowInsetsListener(navView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, systemBars.bottom); 
                return insets;
            });
        }

        if (navView != null) {
            navView.setOnItemSelectedListener(item -> {
                Fragment fragment = null;
                int id = item.getItemId();
                int index = 0;
                
                if (id == R.id.nav_home) {
                    fragment = new HomeFragment();
                    index = 0;
                } else if (id == R.id.nav_explore) {
                    fragment = new ExploreFragment();
                    index = 1;
                } else if (id == R.id.nav_messages) {
                    fragment = new MessagesFragment();
                    index = 2;
                } else if (id == R.id.nav_profile) {
                    fragment = new ProfileFragment();
                    index = 3;
                }

                if (fragment != null && index != previousTabIndex) {
                    HapticManager.lightTap(findViewById(R.id.bottomNavigationView));
                    loadFragment(fragment, index);
                    previousTabIndex = index;
                    return true;
                }
                return id == item.getItemId(); // Allow re-selection logic if needed
            });
        }

        View fab = findViewById(R.id.fabAddProduct);
        if (fab != null) {
            fab.setOnClickListener(v -> {
                HapticManager.lightTap(v);
                showListingTypeChoice();
            });
        }

        checkCampusService();
        requestNotificationPermission();
        setupBackPress();
    }

    private void loadFragment(Fragment fragment, int index) {
        int animEnter, animExit;
        if (index > previousTabIndex) {
            animEnter = R.anim.slide_in_right;
            animExit = R.anim.slide_out_left;
        } else if (index < previousTabIndex) {
            animEnter = R.anim.slide_in_left;
            animExit = R.anim.slide_out_right;
        } else {
            animEnter = R.anim.fade_in;
            animExit = R.anim.fade_out;
        }

        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(animEnter, animExit)
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private long backPressedTime;
    private void setupBackPress() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish();
                } else {
                    Toast.makeText(HomeActivity.this, "Press back again to exit", Toast.LENGTH_SHORT).show();
                    HapticManager.lightTap(findViewById(R.id.home_main));
                }
                backPressedTime = System.currentTimeMillis();
            }
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void showListingTypeChoice() {
        if (!AppDataStore.isLoggedIn(this)) {
            Toast.makeText(this, "Login required to post listings", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(this);
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
        if (!ConnectivityHelper.isOnline(this)) return;
        polyGoRepository.getStatus(new Callback<BaseResponse>() {
            @Override public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {}
            @Override public void onFailure(Call<BaseResponse> call, Throwable t) {}
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
