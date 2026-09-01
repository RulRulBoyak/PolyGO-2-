package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);
        com.poliku.polygoplus.data.AppDataStore.initialize(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Class<?> next;
            if (!com.poliku.polygoplus.data.AppDataStore.hasSeenOnboarding(this)) {
                next = OnboardingActivity.class;
            } else {
                // Always go to HomeActivity for both Guests and Logged-in users
                next = HomeActivity.class;
            }
            Intent intent = new Intent(SplashActivity.this, next);
            startActivity(intent);
            finish();
        }, 2000);
    }
}
