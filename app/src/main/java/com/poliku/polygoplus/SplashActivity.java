package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        
        route();
    }

    private void route() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent;
            if (!com.poliku.polygoplus.data.AppDataStore.hasSeenOnboarding(this)) {
                intent = new Intent(SplashActivity.this, OnboardingActivity.class);
            } else {
                intent = new Intent(SplashActivity.this, HomeActivity.class);
            }
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 800); // Shorter delay as system already showed splash
    }
}
