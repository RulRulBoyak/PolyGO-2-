package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.UiUtils;
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
    public static final String EXTRA_CONSENT_AGREED = "consent_agreed";

    @Inject PolyGoApi api;

    private String email, name, studentId, password;
    private boolean consentAgreed = true;
    private boolean submitting;
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

    }

    private void verify() {
        if (submitting) return;
        String otp = etOtp.getText().toString().trim();
        if (otp.length() < 6) {
            UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_enter_valid_otp);
            return;
        }

        HapticManager.mediumTap(etOtp);
        setSubmitting(true);
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
                setSubmitting(false);
                UiUtils.snackbarError(OtpActivity.this.findViewById(android.R.id.content), msg);
            }
        });
    }

    private void resend() {
        HapticManager.lightTap(etOtp);
        api.sendOtp(new PolyGoApi.OtpRequest(email)).enqueue(new retrofit2.Callback<PolyGoApi.OtpSendResponse>() {
            @Override
            public void onResponse(retrofit2.Call<PolyGoApi.OtpSendResponse> call, retrofit2.Response<PolyGoApi.OtpSendResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    UiUtils.snackbar(OtpActivity.this.findViewById(android.R.id.content), getString(R.string.toast_code_resent_to, email));
                } else {
                    UiUtils.snackbarError(OtpActivity.this.findViewById(android.R.id.content), R.string.toast_failed_to_resend);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<PolyGoApi.OtpSendResponse> call, Throwable t) {
                UiUtils.snackbarError(OtpActivity.this.findViewById(android.R.id.content), t.getMessage());
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
                        userJson.put("is_banned", body.user.banned);
                        
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
                    setSubmitting(false);
                    UiUtils.snackbarError(OtpActivity.this.findViewById(android.R.id.content), R.string.toast_registration_failed);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<PolyGoApi.LoginResponse> call, Throwable t) {
                setSubmitting(false);
                UiUtils.snackbarError(OtpActivity.this.findViewById(android.R.id.content), t.getMessage());
            }
        });
    }

    private void setSubmitting(boolean value) {
        submitting = value;
        findViewById(R.id.btnVerify).setEnabled(!value);
        findViewById(R.id.tvResend).setEnabled(!value);
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
