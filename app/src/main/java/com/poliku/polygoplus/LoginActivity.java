package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;
import com.poliku.polygoplus.ui.HapticManager;
import androidx.appcompat.app.AppCompatActivity;

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

public class LoginActivity extends BaseActivity {
    private AuthViewModel viewModel;
    private TextInputEditText etMatrix, etPassword;
    private TextInputLayout tilMatrix, tilPassword;
    private android.widget.Button btnLogin;

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

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        setupValidation();

        btnLogin.setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            String studentId = etMatrix.getText().toString().trim();
            String password = etPassword.getText().toString();
            btnLogin.setEnabled(false);
            viewModel.login(studentId, password, new retrofit2.Callback<PolyGoApi.LoginResponse>() {
                @Override
                public void onResponse(retrofit2.Call<PolyGoApi.LoginResponse> call, retrofit2.Response<PolyGoApi.LoginResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        HapticManager.success(LoginActivity.this);
                        PolyGoApi.LoginResponse body = response.body();
                        
                        // Convert Model to JSONObject for AppDataStore compatibility
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
                    } else {
                        onError("Login failed");
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<PolyGoApi.LoginResponse> call, Throwable t) {
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
                NetworkApi.updateFcmToken(userId, token, new NetworkApi.Callback() {
                    @Override public void onSuccess(JSONObject response) {}
                    @Override public void onError(String message) {}
                });
            }
        });
    }

    // Common animations handled by BaseActivity
}
