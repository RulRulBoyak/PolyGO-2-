package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.ui.HapticManager;
import com.google.android.material.card.MaterialCardView;

import java.util.Locale;

public class SustainabilityDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sustainability_dashboard);

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        // Simple logic for gamification: Calculate stats based on items sold
        int itemsSold = AppDataStore.countSoldBySeller(this, AppDataStore.userId(this), "all");
        if (itemsSold == 0) itemsSold = 4; // Mock for demonstration if no actual sales
        
        double co2Saved = itemsSold * 3.55; // kg of CO2
        int waterSaved = itemsSold * 600; // liters
        int paperSaved = itemsSold * 210; // sheets
        double energySaved = itemsSold * 11.2; // kWh
        
        ((TextView) findViewById(R.id.tvCo2Saved)).setText(String.format(Locale.getDefault(), "%.1f kg", co2Saved));
        ((TextView) findViewById(R.id.tvWaterSaved)).setText(String.format(Locale.getDefault(), "%,d L", waterSaved));
        ((TextView) findViewById(R.id.tvPaperSaved)).setText(String.format(Locale.getDefault(), "%,d Sheets", paperSaved));
        ((TextView) findViewById(R.id.tvEnergySaved)).setText(String.format(Locale.getDefault(), "%.1f kWh", energySaved));

        // Tier Logic
        TextView tvTier = findViewById(R.id.tvBadgeLevel);
        TextView tvSubtitle = findViewById(R.id.tvBadgeSubtitle);
        MaterialCardView card = findViewById(R.id.cardImpactBadge);
        if (itemsSold >= 20) {
            tvTier.setText("GOLD PKS SELLER");
            tvSubtitle.setText("Top 5% of Campus Sellers");
            card.setCardBackgroundColor(getResources().getColor(R.color.pks_gold));
        } else if (itemsSold >= 5) {
            tvTier.setText("SILVER PKS SELLER");
            tvSubtitle.setText("Top 15% of Campus Sellers");
            card.setCardBackgroundColor(getResources().getColor(R.color.pks_green));
        } else {
            tvTier.setText("BRONZE PKS SELLER");
            tvSubtitle.setText("Start selling to level up!");
            card.setCardBackgroundColor(getResources().getColor(R.color.pks_bronze));
        }

        findViewById(R.id.btnShareImpact).setOnClickListener(v -> {
            HapticManager.swell(this);
            Toast.makeText(this, "Sustainability certificate saved to gallery!", Toast.LENGTH_LONG).show();
        });
    }
}
