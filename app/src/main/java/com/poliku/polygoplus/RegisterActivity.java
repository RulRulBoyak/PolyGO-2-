package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import androidx.core.content.ContextCompat;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.ui.ExitGuard;
import com.poliku.polygoplus.ui.HapticManager;
import androidx.lifecycle.ViewModelProvider;
import com.poliku.polygoplus.viewmodel.AuthViewModel;
import com.google.android.material.textfield.TextInputLayout;
import com.poliku.polygoplus.ui.BaseActivity;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.text.Editable;
import android.text.TextWatcher;

import androidx.activity.OnBackPressedCallback;
import org.json.JSONObject;

import java.util.concurrent.Executor;

@AndroidEntryPoint
public class RegisterActivity extends BaseActivity {
    public static final String EXTRA_GOOGLE_ID_TOKEN = "google_id_token";
    public static final String EXTRA_GOOGLE_EMAIL = "google_email";
    public static final String EXTRA_GOOGLE_NAME = "google_name";
    private static final String TAG = "GoogleRegistration";
    private AuthViewModel viewModel;
    private TextInputEditText etName, etMatrix, etEmail, etPassword, etConfirmPassword;
    private TextInputLayout tilName, tilMatrix, tilEmail, tilPassword, tilConfirmPassword;
    private MaterialCheckBox cbTerms;
    private Button btnRegister, btnGoogle;
    private LinearProgressIndicator registerProgress;
    private CredentialManager credentialManager;
    private Executor executor;
    private CancellationSignal googleCancellationSignal;
    private String googleIdToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        AppDataStore.initialize(this);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        etName = findViewById(R.id.etFullName);
        etMatrix = findViewById(R.id.etMatrixNo);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        cbTerms = findViewById(R.id.cbTerms);
        
        tilName = findViewById(R.id.tilFullName);
        tilMatrix = findViewById(R.id.tilMatrix);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        btnRegister = findViewById(R.id.btnRegisterAction);
        btnGoogle = findViewById(R.id.btnGoogleRegister);
        registerProgress = findViewById(R.id.registerProgress);
        credentialManager = CredentialManager.create(this);
        executor = ContextCompat.getMainExecutor(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> confirmExit());

        setupValidation();
        setupGoogleRegistration();
        applyGoogleProfile(getIntent().getStringExtra(EXTRA_GOOGLE_ID_TOKEN),
                getIntent().getStringExtra(EXTRA_GOOGLE_EMAIL),
                getIntent().getStringExtra(EXTRA_GOOGLE_NAME));

        cbTerms.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setTermsAccepted(isChecked);
        });

        btnRegister.setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            String name = etName.getText().toString().trim();
            String studentId = etMatrix.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();
            String confirmPassword = etConfirmPassword.getText().toString();

            if (!viewModel.validateRegisterForSubmit(name, studentId, email, password,
                    confirmPassword)) {
                if (!cbTerms.isChecked()) {
                    Toast.makeText(this, R.string.please_agree_to_terms, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.please_fix_registration_fields,
                            Toast.LENGTH_SHORT).show();
                }
                return;
            }

            if (googleIdToken != null) {
                completeGoogleRegistration(name, studentId, password);
                return;
            }

            setLoading(true, R.string.sending_verification_code);
            viewModel.sendOtp(email, new Callback<PolyGoApi.OtpSendResponse>() {
                @Override
                public void onResponse(Call<PolyGoApi.OtpSendResponse> call, Response<PolyGoApi.OtpSendResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        setLoading(false, R.string.action_continue);
                        HapticManager.success(RegisterActivity.this);
                        Intent intent = new Intent(RegisterActivity.this, OtpActivity.class);
                        intent.putExtra(OtpActivity.EXTRA_EMAIL, email);
                        intent.putExtra(OtpActivity.EXTRA_NAME, name);
                        intent.putExtra(OtpActivity.EXTRA_STUDENT_ID, studentId);
                        intent.putExtra(OtpActivity.EXTRA_PASSWORD, password);
                        intent.putExtra(OtpActivity.EXTRA_CONSENT_AGREED, cbTerms.isChecked());
                        startActivity(intent);
                    } else {
                        PolyGoApi.OtpSendResponse body = response.body();
                        String message = body == null ? null : body.getMessage();
                        onError(message == null || message.trim().isEmpty()
                                ? getString(R.string.toast_code_send_failed) : message);
                    }
                }

                @Override
                public void onFailure(Call<PolyGoApi.OtpSendResponse> call, Throwable t) {
                    onError(getString(R.string.toast_could_not_reach_server_try_again));
                }

                private void onError(String msg) {
                    HapticManager.error(RegisterActivity.this);
                    setLoading(false, R.string.action_continue);
                    Toast.makeText(RegisterActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
        });

        findViewById(R.id.tvLoginLink).setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });
    }

    private void confirmExit() {
        if (!ExitGuard.anyText(etName.getText(), etMatrix.getText(), etEmail.getText(),
                etPassword.getText(), etConfirmPassword.getText())) {
            finish();
            return;
        }
        ExitGuard.show(this, this::finish);
    }

    private String text(int id) {
        TextInputEditText input = findViewById(id);
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void setupValidation() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > 0) HapticManager.selectionTick(RegisterActivity.this);
                viewModel.validateRegister(
                        etName.getText().toString(),
                        etMatrix.getText().toString(),
                        etEmail.getText().toString(),
                        etPassword.getText().toString(),
                        etConfirmPassword.getText().toString()
                );
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        etName.addTextChangedListener(watcher);
        etMatrix.addTextChangedListener(watcher);
        etEmail.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);
        etConfirmPassword.addTextChangedListener(watcher);

        viewModel.nameError.observe(this, error -> tilName.setError(error));
        viewModel.matrixError.observe(this, error -> tilMatrix.setError(error));
        viewModel.emailError.observe(this, error -> tilEmail.setError(error));
        viewModel.passwordError.observe(this, error -> tilPassword.setError(error));
        viewModel.confirmPasswordError.observe(this, error -> tilConfirmPassword.setError(error));
    }

    private void setupGoogleRegistration() {
        btnGoogle.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            String webClientId = getString(R.string.default_web_client_id);
            if (webClientId.isEmpty()) {
                Toast.makeText(this, R.string.toast_google_sign_in_unavailable,
                        Toast.LENGTH_LONG).show();
                return;
            }
            btnGoogle.setEnabled(false);
            GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .build();
            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build();
            googleCancellationSignal = new CancellationSignal();
            credentialManager.getCredentialAsync(this, request, googleCancellationSignal,
                    executor, new CredentialManagerCallback<GetCredentialResponse,
                            GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse result) {
                            handleGoogleCredential(result.getCredential());
                        }

                        @Override
                        public void onError(GetCredentialException error) {
                            btnGoogle.setEnabled(true);
                            boolean cancelled = error instanceof androidx.credentials.exceptions
                                    .GetCredentialCancellationException;
                            if (!cancelled) {
                                int message = error instanceof NoCredentialException
                                        ? R.string.toast_google_sign_in_unavailable
                                        : R.string.toast_google_sign_in_failed;
                                Toast.makeText(RegisterActivity.this, message,
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
        });
    }

    private void handleGoogleCredential(Credential credential) {
        try {
            String token;
            if (credential instanceof GoogleIdTokenCredential) {
                token = ((GoogleIdTokenCredential) credential).getIdToken();
            } else if (credential instanceof androidx.credentials.CustomCredential
                    && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(
                    ((androidx.credentials.CustomCredential) credential).getType())) {
                token = GoogleIdTokenCredential.createFrom(
                        ((androidx.credentials.CustomCredential) credential).getData())
                        .getIdToken();
            } else {
                throw new IllegalArgumentException("Unexpected credential type");
            }
            viewModel.googleLogin(token, new Callback<PolyGoApi.LoginResponse>() {
                @Override
                public void onResponse(Call<PolyGoApi.LoginResponse> call,
                                       Response<PolyGoApi.LoginResponse> response) {
                    btnGoogle.setEnabled(true);
                    PolyGoApi.LoginResponse body = response.body();
                    if (response.isSuccessful() && body != null && body.isSuccess()
                            && body.requiresProfile) {
                        applyGoogleProfile(token, body.googleEmail, body.googleName);
                    } else if (response.isSuccessful() && body != null && body.isSuccess()) {
                        saveSessionAndOpen(body, HomeActivity.class);
                    } else {
                        showGoogleError(body == null ? null : body.getMessage());
                    }
                }

                @Override
                public void onFailure(Call<PolyGoApi.LoginResponse> call, Throwable error) {
                    btnGoogle.setEnabled(true);
                    showGoogleError(null);
                }
            });
        } catch (Exception error) {
            Log.w(TAG, "Could not parse Google credential", error);
            btnGoogle.setEnabled(true);
            showGoogleError(null);
        }
    }

    private void applyGoogleProfile(String token, String email, String name) {
        if (token == null || email == null || email.trim().isEmpty()) return;
        googleIdToken = token;
        getIntent().putExtra(EXTRA_GOOGLE_ID_TOKEN, token);
        getIntent().putExtra(EXTRA_GOOGLE_EMAIL, email);
        getIntent().putExtra(EXTRA_GOOGLE_NAME, name);
        etEmail.setText(email);
        etEmail.setEnabled(false);
        if (etName.getText() == null || etName.getText().toString().trim().isEmpty()) {
            etName.setText(name == null ? "" : name);
        }
        btnGoogle.setText(R.string.google_email_verified);
        btnGoogle.setEnabled(false);
        etMatrix.requestFocus();
    }

    private void completeGoogleRegistration(String name, String studentId, String password) {
        setLoading(true, R.string.creating_account);
        viewModel.completeGoogleRegistration(googleIdToken, name, studentId, password,
                cbTerms.isChecked(), new Callback<PolyGoApi.LoginResponse>() {
                    @Override
                    public void onResponse(Call<PolyGoApi.LoginResponse> call,
                                           Response<PolyGoApi.LoginResponse> response) {
                        PolyGoApi.LoginResponse body = response.body();
                        if (response.isSuccessful() && body != null && body.isSuccess()
                                && body.user != null) {
                            saveSessionAndOpen(body, VerificationActivity.class);
                        } else {
                            registrationError(body == null ? null : body.getMessage());
                        }
                    }

                    @Override
                    public void onFailure(Call<PolyGoApi.LoginResponse> call, Throwable error) {
                        registrationError(getString(
                                R.string.toast_could_not_reach_server_try_again));
                    }
                });
    }

    private void registrationError(String message) {
        setLoading(false, R.string.action_continue);
        HapticManager.error(this);
        Toast.makeText(this, message == null || message.trim().isEmpty()
                ? getString(R.string.toast_registration_failed) : message,
                Toast.LENGTH_LONG).show();
    }

    private void showGoogleError(String message) {
        HapticManager.error(this);
        Toast.makeText(this, message == null || message.trim().isEmpty()
                ? getString(R.string.toast_google_sign_in_failed) : message,
                Toast.LENGTH_LONG).show();
    }

    private void saveSessionAndOpen(PolyGoApi.LoginResponse body, Class<?> destination) {
        try {
            JSONObject user = new JSONObject();
            user.put("id", body.user.id);
            user.put("full_name", body.user.name);
            user.put("student_id", body.user.studentId);
            user.put("email", body.user.email);
            user.put("mobile", body.user.mobile);
            user.put("role", body.user.role);
            user.put("is_verified", body.user.verified);
            user.put("verification_status", body.user.verificationStatus);
            user.put("is_banned", body.user.banned);
            AppDataStore.saveRemoteSession(this, user, body.getToken());
        } catch (Exception error) {
            registrationError(getString(R.string.toast_registration_failed));
            return;
        }
        HapticManager.success(this);
        Intent intent = new Intent(this, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading, int loadingText) {
        btnRegister.setEnabled(!loading);
        btnRegister.setText(loading ? loadingText : R.string.action_continue);
        registerProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        registerProgress.setContentDescription(loading
                ? getString(loadingText) : null);
    }

    @Override
    protected void onDestroy() {
        if (googleCancellationSignal != null) googleCancellationSignal.cancel();
        super.onDestroy();
    }

    // Common animations handled by BaseActivity
}
