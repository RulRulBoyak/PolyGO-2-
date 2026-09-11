package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.core.content.ContextCompat;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import androidx.lifecycle.ViewModelProvider;
import com.poliku.polygoplus.viewmodel.AuthViewModel;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import android.text.Editable;
import android.text.TextWatcher;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.PolyGoRepository;

import java.util.concurrent.Executor;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class LoginActivity extends BaseActivity {
    @Inject PolyGoRepository polyGoRepository;
    private AuthViewModel viewModel;
    private TextInputEditText etMatrix, etPassword;
    private TextInputLayout tilMatrix, tilPassword;
    private Button btnLogin, btnGoogle;
    private CredentialManager credentialManager;
    private Executor executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        AppDataStore.initialize(this);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        etMatrix = findViewById(R.id.etMatrixNo);
        etPassword = findViewById(R.id.etPassword);
        tilMatrix = findViewById(R.id.tilMatrix);
        tilPassword = findViewById(R.id.tilPassword);
        btnLogin = findViewById(R.id.btnLoginNormal);
        btnGoogle = findViewById(R.id.btnGoogleLogin);
        credentialManager = CredentialManager.create(this);
        executor = ContextCompat.getMainExecutor(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        setupValidation();
        setupGoogleSignIn();

        btnLogin.setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            hideKeyboard(v);
            String studentId = etMatrix.getText().toString().trim();
            String password = etPassword.getText().toString();
            btnLogin.setEnabled(false);
            viewModel.login(studentId, password, new Callback<PolyGoApi.LoginResponse>() {
                @Override
                public void onResponse(Call<PolyGoApi.LoginResponse> call, Response<PolyGoApi.LoginResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        handleLoginResponse(response.body());
                    } else {
                        onError("Login failed");
                    }
                }

                @Override
                public void onFailure(Call<PolyGoApi.LoginResponse> call, Throwable t) {
                    onError(t.getMessage());
                }

                private void onError(String msg) {
                    HapticManager.error(LoginActivity.this);
                    btnLogin.setEnabled(true);
                    Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
        });
        findViewById(R.id.btnCreateAccount).setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
        findViewById(R.id.tvForgotPassword).setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                btnLogin.performClick();
                return true;
            }
            return false;
        });
    }

    private void setupGoogleSignIn() {
        btnGoogle.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            String webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID;
            if (webClientId == null || webClientId.isEmpty()) {
                Toast.makeText(this, "Google sign-in is not available right now. Use your campus ID instead.", Toast.LENGTH_LONG).show();
                return;
            }
            googleSignIn();
        });
    }

    private void googleSignIn() {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();
        btnGoogle.setEnabled(false);

        credentialManager.getCredentialAsync(this, request, new CancellationSignal(), executor,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        btnGoogle.setEnabled(true);
                        Credential credential = result.getCredential();
                        if (credential instanceof GoogleIdTokenCredential) {
                            String idToken = ((GoogleIdTokenCredential) credential).getIdToken();
                            loginWithGoogleIdToken(idToken);
                        } else if (credential instanceof androidx.credentials.CustomCredential) {
                            try {
                                GoogleIdTokenCredential googleCredential =
                                        GoogleIdTokenCredential.createFrom(credential.getData());
                                loginWithGoogleIdToken(googleCredential.getIdToken());
                            } catch (Exception ignore) {
                                onGoogleError(false, null);
                            }
                        } else {
                            onGoogleError(false, null);
                        }
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        btnGoogle.setEnabled(true);
                        boolean cancelled = e instanceof androidx.credentials.exceptions.GetCredentialCancellationException;
                        onGoogleError(cancelled, e.getMessage());
                    }
                });
    }

    private void loginWithGoogleIdToken(String idToken) {
        btnGoogle.setEnabled(false);
        polyGoRepository.googleLogin(idToken, new Callback<PolyGoApi.LoginResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.LoginResponse> call, Response<PolyGoApi.LoginResponse> response) {
                btnGoogle.setEnabled(true);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    handleLoginResponse(response.body());
                } else {
                    onGoogleError(false, null);
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.LoginResponse> call, Throwable t) {
                btnGoogle.setEnabled(true);
                onGoogleError(false, t.getMessage());
            }
        });
    }

    private void onGoogleError(boolean cancelled, String message) {
        if (!cancelled) {
            HapticManager.error(LoginActivity.this);
            Toast.makeText(this, message == null ? "Google sign-in failed. Please try again." : message, Toast.LENGTH_LONG).show();
        }
    }

    private void handleLoginResponse(PolyGoApi.LoginResponse body) {
        HapticManager.success(LoginActivity.this);
        btnLogin.setEnabled(true);
        btnGoogle.setEnabled(true);

        try {
            JSONObject userJson = new JSONObject();
            userJson.put("id", body.user.id);
            userJson.put("full_name", body.user.name);
            userJson.put("student_id", body.user.studentId);
            userJson.put("email", body.user.email);
            userJson.put("mobile", body.user.mobile);
            userJson.put("role", body.user.role);

            AppDataStore.saveRemoteSession(LoginActivity.this, userJson, body.token);
        } catch (Exception ignored) {}

        syncFcmToken();
        startActivity(new Intent(LoginActivity.this, HomeActivity.class));
        finish();
    }

    private void setupValidation() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > 0) HapticManager.selectionTick(LoginActivity.this);
                viewModel.validateLogin(etMatrix.getText().toString(), etPassword.getText().toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        etMatrix.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);

        viewModel.matrixError.observe(this, error -> tilMatrix.setError(error));
        viewModel.passwordError.observe(this, error -> tilPassword.setError(error));
        viewModel.isLoginFormValid.observe(this, isValid -> btnLogin.setEnabled(isValid));
    }

    private void syncFcmToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;
            String token = task.getResult();
            String userId = AppDataStore.userId(this);
            if (!userId.equals("0")) {
                polyGoRepository.updateFcmToken(userId, token, new Callback<BaseResponse>() {
                    @Override public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {}
                    @Override public void onFailure(Call<BaseResponse> call, Throwable t) {}
                });
            }
        });
    }

    // Common animations handled by BaseActivity

    private void hideKeyboard(View view) {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
