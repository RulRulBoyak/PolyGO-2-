package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.data.AppDataStore;

public class SplashActivity extends AppCompatActivity {

    private boolean routed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        route();
    }

    private void route() {
        getWindow().getDecorView().postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            // Route from the cache populated by AppDataStore.initialize(); if
            // the async warm-up has not completed by this point the
            // synchronous read is safe — prefs() init is a fast no-op after the
            // first DISK_EXECUTOR call.
            navigate(AppDataStore.hasSeenOnboarding(SplashActivity.this));
        }, 800);
    }

    private void navigate(boolean hasSeenOnboarding) {
        if (routed) return;
        routed = true;
        Intent intent = hasSeenOnboarding
                ? new Intent(SplashActivity.this, HomeActivity.class)
                : new Intent(SplashActivity.this, OnboardingActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
}