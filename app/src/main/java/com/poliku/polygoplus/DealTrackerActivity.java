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
            Toast.makeText(this, R.string.toast_transaction_complete_funds_released, Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private void setupTimeline() {
        // Mocking the 6 stages of a PKS deal
        setStep(R.id.step1, getString(R.string.deal_step_offer_sent), getString(R.string.deal_step_waiting_acceptance), true);
        setStep(R.id.step2, getString(R.string.deal_step_price_agreed), getString(R.string.deal_step_price_confirmed), true);
        setStep(R.id.step3, getString(R.string.deal_step_meetup_scheduled), getString(R.string.deal_step_meetup_details), true);
        setStep(R.id.step4, getString(R.string.deal_step_inspection), getString(R.string.deal_step_inspection_details), false);
        setStep(R.id.step5, getString(R.string.deal_step_payment_confirmed), getString(R.string.deal_step_payment_details), false);
        setStep(R.id.step6, getString(R.string.deal_step_deal_closed), getString(R.string.deal_step_deal_closed_details), false);
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
                Toast.makeText(DealTrackerActivity.this, R.string.toast_inspection_time_ended, Toast.LENGTH_SHORT).show();
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }
}
