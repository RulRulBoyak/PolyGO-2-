package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        route();
    }

    private void route() {
        // Post on the decor view's handler: if this activity is destroyed the
        // runnable is removed automatically (no activity leak).
        getWindow().getDecorView().postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            Intent intent;
            // Rule 3.3: Verify first-run status immediately on cold start
            if (!com.poliku.polygoplus.data.AppDataStore.hasSeenOnboarding(this)) {
                intent = new Intent(SplashActivity.this, OnboardingActivity.class);
            } else {
                intent = new Intent(SplashActivity.this, HomeActivity.class);
            }
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        }, 800);
    }
}