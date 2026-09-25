package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.ExitGuard;
import com.poliku.polygoplus.ui.HapticManager;

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
import androidx.activity.OnBackPressedCallback;

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
    private static final String TAG = "GoogleSignIn";
    @Inject PolyGoRepository polyGoRepository;
    private AuthViewModel viewModel;
    private TextInputEditText etMatrix, etPassword;
    private TextInputLayout tilMatrix, tilPassword;
    private Button btnLogin, btnGoogle;
    private CredentialManager credentialManager;
    private Executor executor;
    private CancellationSignal googleCancellationSignal;
    private boolean googleSignInInProgress;

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

        findViewById(R.id.btnBack).setOnClickListener(v -> confirmExit());
        
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
                    } else if (isSuspendedResponse(response)) {
                        onError(getString(R.string.toast_account_suspended));
                    } else {
                        onError(getString(R.string.toast_login_failed));
                    }
                }

                @Override
                public void onFailure(Call<PolyGoApi.LoginResponse> call, Throwable t) {
                    onError(getString(R.string.toast_could_not_reach_server_try_again));
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

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });
    }

    private void confirmExit() {
        if (!ExitGuard.anyText(etMatrix.getText(), etPassword.getText())) {
            finish();
            return;
        }
        ExitGuard.show(this, this::finish);
    }

    private void setupGoogleSignIn() {
        btnGoogle.setOnClickListener(v -> {
            if (googleSignInInProgress) return;
            HapticManager.lightTap(v);
            String webClientId = getString(R.string.default_web_client_id);
            if (webClientId == null || webClientId.isEmpty()) {
                Toast.makeText(this, R.string.toast_google_sign_in_unavailable, Toast.LENGTH_LONG).show();
                return;
            }
            setGoogleSignInInProgress(true);
            googleSignIn();
        });
    }

    private void googleSignIn() {
        String webClientId = getString(R.string.default_web_client_id);
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();
        googleCancellationSignal = new CancellationSignal();

        credentialManager.getCredentialAsync(this, request, googleCancellationSignal, executor,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        Credential credential = result.getCredential();
                        if (credential instanceof GoogleIdTokenCredential) {
                            String idToken = ((GoogleIdTokenCredential) credential).getIdToken();
                            loginWithGoogleIdToken(idToken);
                        } else if (credential instanceof androidx.credentials.CustomCredential) {
                            androidx.credentials.CustomCredential customCredential =
                                    (androidx.credentials.CustomCredential) credential;
                            if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                    .equals(customCredential.getType())) {
                                Log.w(TAG, "Unexpected credential type: " + customCredential.getType());
                                onGoogleError(false, null);
                                return;
                            }
                            try {
                                GoogleIdTokenCredential googleCredential =
                                        GoogleIdTokenCredential.createFrom(customCredential.getData());
                                loginWithGoogleIdToken(googleCredential.getIdToken());
                            } catch (Exception error) {
                                Log.w(TAG, "Could not parse Google credential", error);
                                onGoogleError(false, null);
                            }
                        } else {
                            Log.w(TAG, "Unexpected credential class: " + credential.getClass().getName());
                            onGoogleError(false, null);
                        }
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        Log.w(TAG, "Credential request failed: " + e.getClass().getSimpleName()
                                + ": " + e.getMessage());
                        boolean cancelled = e instanceof androidx.credentials.exceptions.GetCredentialCancellationException;
                        String message = e instanceof NoCredentialException
                                ? getString(R.string.toast_google_sign_in_unavailable)
                                : e.getMessage();
                        onGoogleError(cancelled, message);
                    }
                });
    }

    private void loginWithGoogleIdToken(String idToken) {
        btnGoogle.setEnabled(false);
        polyGoRepository.googleLogin(idToken, false, new Callback<PolyGoApi.LoginResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.LoginResponse> call, Response<PolyGoApi.LoginResponse> response) {
                btnGoogle.setEnabled(true);
                PolyGoApi.LoginResponse body = response.body();
                if (response.isSuccessful() && body != null && body.isSuccess()
                        && body.requiresProfile) {
                    Intent registration = new Intent(LoginActivity.this, RegisterActivity.class);
                    registration.putExtra(RegisterActivity.EXTRA_GOOGLE_ID_TOKEN, idToken);
                    registration.putExtra(RegisterActivity.EXTRA_GOOGLE_EMAIL, body.googleEmail);
                    registration.putExtra(RegisterActivity.EXTRA_GOOGLE_NAME, body.googleName);
                    startActivity(registration);
                    setGoogleSignInInProgress(false);
                } else if (response.isSuccessful() && body != null && body.isSuccess()) {
                    handleLoginResponse(body);
                } else {
                    String message = body == null ? googleErrorMessage(response) : body.getMessage();
                    onGoogleError(false, message);
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.LoginResponse> call, Throwable t) {
                btnGoogle.setEnabled(true);
                onGoogleError(false, t.getMessage());
            }
        });
    }

    /** Preserve the API's safe, user-facing error rather than hiding every failed Google login. */
    private String googleErrorMessage(Response<?> response) {
        try {
            if (response.errorBody() == null) return null;
            BaseResponse error = new com.google.gson.Gson().fromJson(
                    response.errorBody().charStream(), BaseResponse.class);
            return error == null ? null : error.getMessage();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void onGoogleError(boolean cancelled, String message) {
        setGoogleSignInInProgress(false);
        if (!cancelled) {
            HapticManager.error(LoginActivity.this);
            Toast.makeText(this, message == null ? getString(R.string.toast_google_sign_in_failed) : message, Toast.LENGTH_LONG).show();
        }
    }

    private void setGoogleSignInInProgress(boolean inProgress) {
        googleSignInInProgress = inProgress;
        if (btnGoogle != null) btnGoogle.setEnabled(!inProgress);
        if (!inProgress) googleCancellationSignal = null;
    }

    @Override
    protected void onDestroy() {
        if (googleCancellationSignal != null) googleCancellationSignal.cancel();
        super.onDestroy();
    }

    private boolean isSuspendedResponse(Response<?> response) {
        try {
            if (response.errorBody() == null) return false;
            return response.errorBody().string().contains("suspended");
        } catch (Exception ignored) {
            return false;
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
            userJson.put("profile_pic_url", body.user.profile_pic_url);
            userJson.put("bio", body.user.bio);
            userJson.put("is_private", body.user.isPrivate);
            userJson.put("is_verified", body.user.verified);
            userJson.put("verification_status", body.user.verificationStatus);
            userJson.put("is_banned", body.user.banned);

            AppDataStore.saveRemoteSession(LoginActivity.this, userJson, body.getToken());
        } catch (Exception ignored) {}

        syncFcmToken();
        Intent home = new Intent(LoginActivity.this, HomeActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(home);
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
            String token = task.isSuccessful() && task.getResult() != null
                    ? task.getResult()
                    : AppDataStore.pendingFcmToken(this);
            if (token == null || token.isEmpty()) return;
            AppDataStore.saveFcmToken(this, token);
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
