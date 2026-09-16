package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.data.AppDataStore;
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
    public static final String EXTRA_DEV_OTP = "dev_otp";
    public static final String EXTRA_CONSENT_AGREED = "consent_agreed";

    @Inject PolyGoApi api;

    private String email, name, studentId, password;
    private boolean consentAgreed = true;
    private EditText etOtp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

        email = getIntent().getStringExtra(EXTRA_EMAIL);
        name = getIntent().getStringExtra(EXTRA_NAME);
        studentId = getIntent().getStringExtra(EXTRA_STUDENT_ID);
        password = getIntent().getStringExtra(EXTRA_PASSWORD);
        consentAgreed = getIntent().getBooleanExtra(EXTRA_CONSENT_AGREED, false);

        etOtp = findViewById(R.id.etOtp);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnVerify).setOnClickListener(v -> verify());
        findViewById(R.id.tvResend).setOnClickListener(v -> resend());
        etOtp.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                verify();
                return true;
            }
            return false;
        });

        ((TextView) findViewById(R.id.tvOtpSubtitle)).setText(getString(R.string.otp_sent_subtitle, email));

        // Dev-only: the backend may echo the OTP back on debug builds; never auto-fill in release.
        if (BuildConfig.DEBUG) {
            String devOtp = getIntent().getStringExtra(EXTRA_DEV_OTP);
            if (devOtp != null && !devOtp.isEmpty()) {
                etOtp.setText(devOtp);
            }
        }
    }

    private void verify() {
        String otp = etOtp.getText().toString().trim();
        if (otp.length() < 6) {
            Toast.makeText(this, R.string.toast_enter_valid_otp, Toast.LENGTH_SHORT).show();
            return;
        }

        HapticManager.mediumTap(etOtp);
        api.verifyOtp(new PolyGoApi.OtpRequest(email, otp)).enqueue(new retrofit2.Callback<com.poliku.polygoplus.api.model.BaseResponse>() {
            @Override
            public void onResponse(retrofit2.Call<com.poliku.polygoplus.api.model.BaseResponse> call, retrofit2.Response<com.poliku.polygoplus.api.model.BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    completeRegistration();
                } else {
                    onError(getString(R.string.toast_invalid_code));
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
        api.sendOtp(new PolyGoApi.OtpRequest(email)).enqueue(new retrofit2.Callback<PolyGoApi.OtpSendResponse>() {
            @Override
            public void onResponse(retrofit2.Call<PolyGoApi.OtpSendResponse> call, retrofit2.Response<PolyGoApi.OtpSendResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    if (BuildConfig.DEBUG && response.body().otp != null && !response.body().otp.isEmpty()) {
                        etOtp.setText(response.body().otp);
                    }
                    Toast.makeText(OtpActivity.this, getString(R.string.toast_code_resent_to, email), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(OtpActivity.this, R.string.toast_failed_to_resend, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<PolyGoApi.OtpSendResponse> call, Throwable t) {
                Toast.makeText(OtpActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void completeRegistration() {
        api.register(new PolyGoApi.RegisterRequest(name, studentId, email, password, consentAgreed)).enqueue(new retrofit2.Callback<PolyGoApi.LoginResponse>() {
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

                    syncFcmToken();

                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        Intent intent = new Intent(OtpActivity.this, VerificationActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }, 2000);
                } else {
                    Toast.makeText(OtpActivity.this, R.string.toast_registration_failed, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<PolyGoApi.LoginResponse> call, Throwable t) {
                Toast.makeText(OtpActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void syncFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            String token = task.isSuccessful() && task.getResult() != null
                    ? task.getResult()
                    : AppDataStore.pendingFcmToken(this);
            if (token == null || token.isEmpty()) return;
            AppDataStore.saveFcmToken(this, token);
            String userId = AppDataStore.userId(this);
            if (userId == null || userId.equals("0")) return;

            com.poliku.polygoplus.api.PolyGoApi.UpdateProfileRequest req =
                    new com.poliku.polygoplus.api.PolyGoApi.UpdateProfileRequest();
            req.user_id = userId;
            req.fcm_token = token;
            api.updateProfile(req).enqueue(new retrofit2.Callback<com.poliku.polygoplus.api.model.BaseResponse>() {
                @Override public void onResponse(retrofit2.Call<com.poliku.polygoplus.api.model.BaseResponse> call,
                                                 retrofit2.Response<com.poliku.polygoplus.api.model.BaseResponse> response) {}
                @Override public void onFailure(retrofit2.Call<com.poliku.polygoplus.api.model.BaseResponse> call,
                                                Throwable t) {}
            });
        });
    }
}
