package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.network.AuthSessionHandler;
import com.poliku.polygoplus.network.ConnectivityHelper;
import com.poliku.polygoplus.network.NetworkErrorHandler;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class SplashActivity extends AppCompatActivity {

    @Inject
    PolyGoRepository polyGoRepository;

    private boolean routed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        route();
    }

    private void route() {
        // Repair any stale cached flag, then the delayed block below navigates
        // with whatever the probe reported (falling back to the cache on timeout).
        probeStatus();
        getWindow().getDecorView().postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (AppDataStore.isMaintenanceMode(SplashActivity.this)) {
                showMaintenance();
            } else {
                navigate(AppDataStore.hasSeenOnboarding(SplashActivity.this));
            }
        }, 800);
    }

    /**
     * Live status.php pre-flight. When the admin flips the kill switch this
     * locks the app on the maintenance screen before the user reaches Home;
     * on downtime it falls back to the cached flag / normal route.
     */
    private void probeStatus() {
        if (!ConnectivityHelper.isOnline(this)) return;
        polyGoRepository.getStatus(new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    AppDataStore.setMaintenanceMode(SplashActivity.this, response.body().isMaintenance());
                    if (response.body().isMaintenance()) {
                        showMaintenance();
                    } else {
                        NetworkErrorHandler.notifyServerOk();
                    }
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                NetworkErrorHandler.notifyServerOk();
            }
        });
    }

    /**
     * Locks the app to the maintenance screen. Guarded by `routed` so a pending
     * navigate() (or a second probe callback) can never stack a second launch.
     */
    private synchronized void showMaintenance() {
        if (routed) return;
        routed = true;
        if (isFinishing() || isDestroyed()) return;
        if (AuthSessionHandler.getTopActivity() instanceof ErrorStateActivity) return;
        Intent intent = new Intent(SplashActivity.this, ErrorStateActivity.class)
                .putExtra(ErrorStateActivity.EXTRA_MODE, "maintenance")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            // No launcher intent available yet - stay silent.
        }
        finish();
    }

    private void navigate(boolean hasSeenOnboarding) {
        if (routed) return;
        routed = true;
        Intent intent;
        if (AppDataStore.isBanned(this)) {
            // Suspension cached from a mid-session 401; don't reopen the app.
            intent = new Intent(SplashActivity.this, BannedActivity.class);
        } else {
            // Browsing is available to guests. Protected actions request login
            // only when the user chooses to perform one.
            intent = new Intent(SplashActivity.this, HomeActivity.class);
        }
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
}
