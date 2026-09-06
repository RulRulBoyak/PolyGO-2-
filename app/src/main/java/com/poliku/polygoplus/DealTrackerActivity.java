package com.poliku.polygoplus;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.ui.HapticManager;

import java.util.Locale;

public class DealTrackerActivity extends AppCompatActivity {

    private TextView tvTimerClock;
    private CountDownTimer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deal_tracker);

        tvTimerClock = findViewById(R.id.tvTimerClock);
        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        setupTimeline();
        startInspectionTimer();

        findViewById(R.id.btnCloseDeal).setOnClickListener(v -> {
            HapticManager.success(this);
            Toast.makeText(this, "Transaction complete. Funds released to seller.", Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private void setupTimeline() {
        // Mocking the 6 stages of a PKS deal
        setStep(R.id.step1, "Offer Sent", "Waiting for seller acceptance", true);
        setStep(R.id.step2, "Price Agreed", "RM 45.00 confirmed by both", true);
        setStep(R.id.step3, "Meetup Scheduled", "Today at 4:30 PM, Cafeteria", true);
        setStep(R.id.step4, "Physical Inspection", "Buyer is testing the item", false);
        setStep(R.id.step5, "Payment Confirmed", "Waiting for buyer approval", false);
        setStep(R.id.step6, "Deal Closed", "Transaction officially complete", false);
    }

    private void setStep(int id, String title, String desc, boolean completed) {
        View v = findViewById(id);
        TextView tvTitle = v.findViewById(R.id.tvStepTitle);
        TextView tvDesc = v.findViewById(R.id.tvStepDesc);
        ImageView ivDot = v.findViewById(R.id.ivStepDot);
        View line = v.findViewById(R.id.stepLine);

        tvTitle.setText(title);
        tvDesc.setText(desc);

        if (completed) {
            tvTitle.setTextColor(getResources().getColor(R.color.airbnb_ink));
            tvDesc.setTextColor(getResources().getColor(R.color.pks_blue));
            ivDot.setColorFilter(getResources().getColor(R.color.pks_blue));
            line.setBackgroundColor(getResources().getColor(R.color.pks_blue));
        }
    }

    private void startInspectionTimer() {
        timer = new CountDownTimer(300000, 1000) { // 5 minutes
            @Override
            public void onTick(long ms) {
                int mins = (int) (ms / 1000) / 60;
                int secs = (int) (ms / 1000) % 60;
                tvTimerClock.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
            }

            @Override
            public void onFinish() {
                tvTimerClock.setText("00:00");
                HapticManager.error(DealTrackerActivity.this);
                Toast.makeText(DealTrackerActivity.this, "Inspection time ended.", Toast.LENGTH_SHORT).show();
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }
}
