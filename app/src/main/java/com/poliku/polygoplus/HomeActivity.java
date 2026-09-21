package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

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
import android.provider.Settings;

import com.poliku.polygoplus.fragments.ExploreFragment;
import com.poliku.polygoplus.fragments.MessagesFragment;
import com.poliku.polygoplus.fragments.ProfileFragment;
import com.poliku.polygoplus.network.AuthSessionHandler;
import com.poliku.polygoplus.network.ConnectivityHelper;
import com.poliku.polygoplus.network.NetworkErrorHandler;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.GuidedTourOverlay;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.api.model.BaseResponse;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class HomeActivity extends AppCompatActivity {
    public static final String EXTRA_REPLAY_TOUR = "replay_tour";
    @Inject PolyGoRepository polyGoRepository;
    private int previousTabIndex = 0;
    private Fragment homeFragment, exploreFragment, messagesFragment, profileFragment;
    private Fragment currentVisibleFragment;
    private static final String TAG_HOME = "home_tab";
    private static final String TAG_EXPLORE = "explore_tab";
    private static final String TAG_MESSAGES = "messages_tab";
    private static final String TAG_PROFILE = "profile_tab";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_home);

        NavigationBarView navView = findViewById(R.id.bottomNavigationView);
        if (navView == null) navView = findViewById(R.id.navigationRail);
        final NavigationBarView nav = navView;

        previousTabIndex = savedInstanceState != null ? savedInstanceState.getInt("tab_index", 0) : 0;
        restoreFragments(savedInstanceState);

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
            navView.setItemActiveIndicatorEnabled(false);
            navView.setOnItemSelectedListener(item -> {
                int index = indexForId(item.getItemId());
                if (index < 0) return false;
                if (index == previousTabIndex) return true;

                HapticManager.lightTap(nav);
                switchTab(fragmentFor(index), index);
                previousTabIndex = index;
                return true;
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
        boolean replayTour = getIntent().getBooleanExtra(EXTRA_REPLAY_TOUR, false);
        if (replayTour || !AppDataStore.hasSeenOnboarding(this)) {
            getWindow().getDecorView().post(() -> showGuidedTour(nav, fab, replayTour));
        } else {
            requestNotificationPermission();
        }
        setupBackPress();
        handleNotificationIntent(getIntent());
    }

    private void showGuidedTour(NavigationBarView nav, View fab, boolean replay) {
        if (nav == null || fab == null || isFinishing()) return;
        View[] targets = {nav.findViewById(R.id.nav_home), nav.findViewById(R.id.nav_explore),
            fab, nav.findViewById(R.id.nav_messages), nav.findViewById(R.id.nav_profile)};
        for (View target : targets) if (target == null) return;
        String[] titles = getResources().getStringArray(R.array.tour_titles);
        String[] bodies = getResources().getStringArray(R.array.tour_bodies);
        GuidedTourOverlay overlay = new GuidedTourOverlay(this, targets, titles, bodies, () -> {
            AppDataStore.setOnboardingSeen(this);
            if (!replay) requestNotificationPermission();
        });
        addContentView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent == null) return;
        String threadId = intent.getStringExtra("thread_id");
        if (threadId == null || threadId.isEmpty()) return;

        // Consume the extra so a config change / re-onCreate never re-launches chat.
        intent.removeExtra("thread_id");

        if (getIntent() == intent) {
            setIntent(new Intent(this, HomeActivity.class));
        }

        Intent chat = new Intent(this, ChatActivity.class);
        chat.putExtra(ChatActivity.EXTRA_THREAD_ID, threadId);
        chat.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(chat);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private void restoreFragments(Bundle savedInstanceState) {
        FragmentManager fm = getSupportFragmentManager();
        homeFragment = fm.findFragmentByTag(TAG_HOME);
        exploreFragment = fm.findFragmentByTag(TAG_EXPLORE);
        messagesFragment = fm.findFragmentByTag(TAG_MESSAGES);
        profileFragment = fm.findFragmentByTag(TAG_PROFILE);

        if (savedInstanceState == null && homeFragment == null) {
            homeFragment = new HomeFragment();
            fm.beginTransaction()
                    .add(R.id.fragment_container, homeFragment, TAG_HOME)
                    .commit();
            currentVisibleFragment = homeFragment;
            previousTabIndex = 0;
            return;
        }

        for (Fragment f : new Fragment[]{homeFragment, exploreFragment, messagesFragment, profileFragment}) {
            if (f != null && f.isVisible()) {
                currentVisibleFragment = f;
                break;
            }
        }
        if (currentVisibleFragment == null) {
            currentVisibleFragment = homeFragment;
            if (currentVisibleFragment != null && !currentVisibleFragment.isAdded()) {
                fm.beginTransaction()
                        .add(R.id.fragment_container, currentVisibleFragment, TAG_HOME)
                        .commit();
            }
        }
    }

    private int indexForId(int id) {
        if (id == R.id.nav_home) return 0;
        if (id == R.id.nav_explore) return 1;
        if (id == R.id.nav_messages) return 2;
        if (id == R.id.nav_profile) return 3;
        return -1;
    }

    private Fragment fragmentFor(int index) {
        if (index == 0) {
            if (homeFragment == null) homeFragment = new HomeFragment();
            return homeFragment;
        } else if (index == 1) {
            if (exploreFragment == null) exploreFragment = new ExploreFragment();
            return exploreFragment;
        } else if (index == 2) {
            if (messagesFragment == null) messagesFragment = new MessagesFragment();
            return messagesFragment;
        } else if (index == 3) {
            if (profileFragment == null) profileFragment = new ProfileFragment();
            return profileFragment;
        }
        return null;
    }

    private void switchTab(Fragment target, int index) {
        if (target == null) return;
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

        FragmentTransaction tx = getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(animEnter, animExit);
        if (currentVisibleFragment != null && currentVisibleFragment != target) {
            tx.hide(currentVisibleFragment);
        }
        if (target.isAdded()) {
            tx.show(target);
        } else {
            tx.add(R.id.fragment_container, target, tabTag(index));
        }
        tx.commit();
        currentVisibleFragment = target;
    }

    private String tabTag(int index) {
        if (index == 1) return TAG_EXPLORE;
        if (index == 2) return TAG_MESSAGES;
        if (index == 3) return TAG_PROFILE;
        return TAG_HOME;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("tab_index", previousTabIndex);
        super.onSaveInstanceState(outState);
    }

    private long backPressedTime;
    private void setupBackPress() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish();
                } else {
                    Toast.makeText(HomeActivity.this, R.string.toast_press_back_again, Toast.LENGTH_SHORT).show();
                    HapticManager.lightTap(findViewById(R.id.home_main));
                }
                backPressedTime = System.currentTimeMillis();
            }
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;

        boolean asked = AppDataStore.hasAskedNotificationPermission(this);
        if (!asked) {
            AppDataStore.markNotificationPermissionAsked(this);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
            showNotificationRationale(false);
        } else {
            showNotificationRationale(true);
        }
    }

    private void showNotificationRationale(boolean goToSettings) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.notif_permission_title)
                .setMessage(R.string.notif_permission_message)
                .setPositiveButton(goToSettings ? R.string.notif_open_settings : R.string.notif_allow, (d, w) -> {
                    if (goToSettings) {
                        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                        startActivity(intent);
                    } else {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                    }
                })
                .setNegativeButton(R.string.notif_not_now, null)
                .show();
    }

    private void showListingTypeChoice() {
        try {
            if (!AppDataStore.isLoggedIn(this)) {
                Toast.makeText(this, R.string.toast_login_required_post, Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                return;
            }

            BottomSheetDialog dialog = new BottomSheetDialog(this);
            // Safter inflation: use the dialog's context
            View content = View.inflate(dialog.getContext(), R.layout.layout_listing_type_choice, null);
            dialog.setContentView(content);

            View product = content.findViewById(R.id.choiceProduct);
            View service = content.findViewById(R.id.choiceService);

            if (product != null) {
                product.setOnClickListener(view -> {
                    try {
                        HapticManager.lightTap(view);
                        dialog.dismiss();
                        Intent intent = new Intent(HomeActivity.this, EditProductActivity.class);
                        startActivity(intent);
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    } catch (Exception e) {
                        Toast.makeText(HomeActivity.this, "Err: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            if (service != null) {
                service.setOnClickListener(view -> {
                    try {
                        HapticManager.lightTap(view);
                        dialog.dismiss();
                        Intent intent = new Intent(HomeActivity.this, AddServiceActivity.class);
                        startActivity(intent);
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    } catch (Exception e) {
                        Toast.makeText(HomeActivity.this, "Err: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            dialog.show();
        } catch (Exception e) {
            Toast.makeText(this, "Layout Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void checkCampusService() {
        if (!ConnectivityHelper.isOnline(this)) return;
        polyGoRepository.getStatus(new Callback<BaseResponse>() {
            @Override public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    boolean maintenance = response.body().isMaintenance();
                    AppDataStore.setMaintenanceMode(HomeActivity.this, maintenance);
                    if (maintenance) {
                        if (!(AuthSessionHandler.getTopActivity() instanceof ErrorStateActivity)) {
                            Intent intent = new Intent(HomeActivity.this, ErrorStateActivity.class)
                                    .putExtra(ErrorStateActivity.EXTRA_MODE, "maintenance")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try {
                                startActivity(intent);
                            } catch (Exception ignored) {
                                // Activity is leaving - the next Splash launch will gate.
                            }
                        }
                    } else {
                        NetworkErrorHandler.notifyServerOk();
                    }
                }
            }
            @Override public void onFailure(Call<BaseResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                NetworkErrorHandler.notifyServerOk();
            }
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
