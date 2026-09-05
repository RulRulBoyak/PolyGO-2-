package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;
import androidx.appcompat.app.AppCompatActivity;

import androidx.lifecycle.ViewModelProvider;
import com.poliku.polygoplus.viewmodel.AuthViewModel;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import android.text.Editable;
import android.text.TextWatcher;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
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
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            String studentId = etMatrix.getText().toString().trim();
            String password = etPassword.getText().toString();
            btnLogin.setEnabled(false);
            NetworkApi.login(studentId, password, new NetworkApi.Callback() {
                @Override public void onSuccess(org.json.JSONObject response) {
                    AppDataStore.saveRemoteSession(LoginActivity.this, 
                            response.optJSONObject("user"), 
                            response.optString("token"));
                    
                    syncFcmToken();

                    startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    finish();
                }
                @Override public void onError(String message) {
                    btnLogin.setEnabled(true);
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });
        findViewById(R.id.btnCreateAccount).setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        findViewById(R.id.tvForgotPassword).setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));
    }

    private void setupValidation() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
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

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
