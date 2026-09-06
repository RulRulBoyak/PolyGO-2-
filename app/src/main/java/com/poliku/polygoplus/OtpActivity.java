package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;
import com.poliku.polygoplus.ui.HapticManager;

import org.json.JSONObject;

public class OtpActivity extends AppCompatActivity {
    public static final String EXTRA_EMAIL = "email";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_STUDENT_ID = "student_id";
    public static final String EXTRA_PASSWORD = "password";

    private String email, name, studentId, password;
    private EditText etOtp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

        email = getIntent().getStringExtra(EXTRA_EMAIL);
        name = getIntent().getStringExtra(EXTRA_NAME);
        studentId = getIntent().getStringExtra(EXTRA_STUDENT_ID);
        password = getIntent().getStringExtra(EXTRA_PASSWORD);

        etOtp = findViewById(R.id.etOtp);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnVerify).setOnClickListener(v -> verify());
        findViewById(R.id.tvResend).setOnClickListener(v -> resend());

        ((TextView) findViewById(R.id.tvOtpSubtitle)).setText("We've sent a 6-digit code to " + email);
    }

    private void verify() {
        String otp = etOtp.getText().toString().trim();
        if (otp.length() < 6) {
            Toast.makeText(this, "Please enter a valid 6-digit code", Toast.LENGTH_SHORT).show();
            return;
        }

        HapticManager.mediumTap(etOtp);
        NetworkApi.verifyOtp(email, otp, new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                completeRegistration();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(OtpActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void resend() {
        HapticManager.lightTap(etOtp);
        NetworkApi.sendOtp(email, new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                Toast.makeText(OtpActivity.this, "Code resent to " + email, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(OtpActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void completeRegistration() {
        NetworkApi.register(name, studentId, email, password, new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                HapticManager.success(OtpActivity.this);
                // Store local login state
                AppDataStore.saveRemoteSession(OtpActivity.this, response.optJSONObject("user"), response.optString("token"));
                
                Intent intent = new Intent(OtpActivity.this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(OtpActivity.this, "Final registration failed: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
