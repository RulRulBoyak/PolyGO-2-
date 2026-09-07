package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.api.PolyGoApi;

import org.json.JSONObject;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class OtpActivity extends BaseActivity {
    public static final String EXTRA_EMAIL = "email";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_STUDENT_ID = "student_id";
    public static final String EXTRA_PASSWORD = "password";

    @Inject PolyGoApi api;

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
        api.verifyOtp(new PolyGoApi.OtpRequest(email, otp)).enqueue(new retrofit2.Callback<com.poliku.polygoplus.api.model.BaseResponse>() {
            @Override
            public void onResponse(retrofit2.Call<com.poliku.polygoplus.api.model.BaseResponse> call, retrofit2.Response<com.poliku.polygoplus.api.model.BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    completeRegistration();
                } else {
                    onError("Invalid code");
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.poliku.polygoplus.api.model.BaseResponse> call, Throwable t) {
                onError(t.getMessage());
            }

            private void onError(String msg) {
                Toast.makeText(OtpActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void resend() {
        HapticManager.lightTap(etOtp);
        api.sendOtp(new PolyGoApi.OtpRequest(email)).enqueue(new retrofit2.Callback<com.poliku.polygoplus.api.model.BaseResponse>() {
            @Override
            public void onResponse(retrofit2.Call<com.poliku.polygoplus.api.model.BaseResponse> call, retrofit2.Response<com.poliku.polygoplus.api.model.BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(OtpActivity.this, "Code resent to " + email, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(OtpActivity.this, "Failed to resend", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.poliku.polygoplus.api.model.BaseResponse> call, Throwable t) {
                Toast.makeText(OtpActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void completeRegistration() {
        api.register(new PolyGoApi.RegisterRequest(name, studentId, email, password)).enqueue(new retrofit2.Callback<PolyGoApi.LoginResponse>() {
            @Override
            public void onResponse(retrofit2.Call<PolyGoApi.LoginResponse> call, retrofit2.Response<PolyGoApi.LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    HapticManager.success(OtpActivity.this);
                    celebrate();
                    
                    PolyGoApi.LoginResponse body = response.body();
                    try {
                        JSONObject userJson = new JSONObject();
                        userJson.put("id", body.user.id);
                        userJson.put("full_name", body.user.name);
                        userJson.put("student_id", body.user.studentId);
                        userJson.put("email", body.user.email);
                        userJson.put("mobile", body.user.mobile);
                        userJson.put("role", body.user.role);
                        
                        AppDataStore.saveRemoteSession(OtpActivity.this, userJson, body.token);
                    } catch (Exception ignored) {}
                    
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        Intent intent = new Intent(OtpActivity.this, HomeActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }, 2000);
                } else {
                    Toast.makeText(OtpActivity.this, "Registration failed", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<PolyGoApi.LoginResponse> call, Throwable t) {
                Toast.makeText(OtpActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
