package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.poliku.polygoplus.data.AppDataStore;

public class VerificationActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_verification);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        render();
    }

    private void render() {
        TextView status = findViewById(R.id.tvVerificationStatus);
        MaterialButton action = findViewById(R.id.btnVerify);
        MaterialButton simulate = findViewById(R.id.btnSimulateApproval);
        String state = AppDataStore.verificationStatus(this);
        if ("approved".equals(state)) {
            status.setText("Verification success. Your PKS student identity is confirmed. Buyers and sellers can trust this account.");
            action.setVisibility(View.GONE);
            simulate.setVisibility(View.GONE);
            findViewById(R.id.etVerificationId).setVisibility(View.GONE);
            findViewById(R.id.etVerificationEmail).setVisibility(View.GONE);
            return;
        }
        if ("pending".equals(state)) {
            status.setText("Waiting for admin approval. PKS staff still need to confirm your student ID. You will be notified when this is complete.");
            action.setVisibility(View.GONE);
            simulate.setVisibility(View.VISIBLE);
            findViewById(R.id.etVerificationId).setVisibility(View.GONE);
            findViewById(R.id.etVerificationEmail).setVisibility(View.GONE);
            simulate.setOnClickListener(v -> {
                AppDataStore.approvePendingVerification(this);
                Toast.makeText(this, "Admin approved your verification", Toast.LENGTH_SHORT).show();
                render();
            });
            return;
        }
        status.setText("Submit the same student ID and campus email from your account. A campus admin must approve this before you are marked as verified.");
        action.setOnClickListener(v -> {
            String id = ((EditText) findViewById(R.id.etVerificationId)).getText().toString().trim();
            String email = ((EditText) findViewById(R.id.etVerificationEmail)).getText().toString().trim();
            if (id.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Enter both verification details", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!AppDataStore.verifyAccount(this, id, email)) {
                Toast.makeText(this, "Details do not match your account", Toast.LENGTH_SHORT).show();
                return;
            }
            // verifyAccount currently auto-approves; keep pending as the intended flow.
            AppDataStore.submitVerification(this);
            Toast.makeText(this, "Submitted. Waiting for admin approval.", Toast.LENGTH_LONG).show();
            render();
        });
    }
}
