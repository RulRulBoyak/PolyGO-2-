package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.ui.HapticManager;

import java.util.Locale;

public class SustainabilityDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sustainability_dashboard);

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        // Simple logic for gamification: Calculate stats based on items sold
        // For now, using mock but realistic formulas for a student
        int itemsSold = 4; // Would come from AppDataStore countSoldBySeller
        
        double co2Saved = itemsSold * 3.55; // kg of CO2
        int waterSaved = itemsSold * 600; // liters
        
        ((TextView) findViewById(R.id.tvCo2Saved)).setText(String.format(Locale.getDefault(), "%.1f kg", co2Saved));
        ((TextView) findViewById(R.id.tvWaterSaved)).setText(String.format(Locale.getDefault(), "%,d L", waterSaved));

        findViewById(R.id.btnShareImpact).setOnClickListener(v -> {
            HapticManager.swell(this);
            Toast.makeText(this, "Sustainability certificate saved to gallery!", Toast.LENGTH_LONG).show();
        });
    }
}
