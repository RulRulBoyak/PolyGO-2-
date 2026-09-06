package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;
import android.text.TextUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;
import com.poliku.polygoplus.ui.HapticManager;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;
import com.google.android.material.textfield.TextInputEditText;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;
import com.poliku.polygoplus.ui.HapticManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.poliku.polygoplus.viewmodel.AuthViewModel;
import com.google.android.material.textfield.TextInputLayout;
import android.text.Editable;
import android.text.TextWatcher;

public class RegisterActivity extends AppCompatActivity {
    private AuthViewModel viewModel;
    private TextInputEditText etName, etMatrix, etEmail, etPassword;
    private TextInputLayout tilName, tilMatrix, tilEmail, tilPassword;
    private android.widget.Button btnRegister;

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
        
        tilName = findViewById(R.id.tilFullName);
        tilMatrix = findViewById(R.id.tilMatrix);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        btnRegister = findViewById(R.id.btnRegisterAction);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        String[] roles = {"Student", "Lecturer", "Staff", "Visitor"};
        AutoCompleteTextView autoCompleteRole = findViewById(R.id.autoCompleteRole);
        autoCompleteRole.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, roles));

        setupValidation();

        btnRegister.setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            String name = etName.getText().toString().trim();
            String studentId = etMatrix.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();
            String role = autoCompleteRole.getText().toString();

            btnRegister.setEnabled(false);
            NetworkApi.register(name, studentId, email, password, new NetworkApi.Callback() {
                @Override public void onSuccess(org.json.JSONObject response) {
                    HapticManager.success(RegisterActivity.this);
                    try {
                        org.json.JSONObject userObj = response.optJSONObject("user");
                        String token = response.optString("token");
                        if (userObj != null) userObj.put("role", role); // Inject role into user session
                        AppDataStore.saveRemoteSession(RegisterActivity.this, userObj, token);
                    } catch (Exception ignored) {}
                    Toast.makeText(RegisterActivity.this, "Account created", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, HomeActivity.class));
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    finish();
                }
                @Override public void onError(String message) {
                    HapticManager.error(RegisterActivity.this);
                    btnRegister.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });

        findViewById(R.id.tvLoginLink).setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private String text(int id) { TextInputEditText input = findViewById(id); return input.getText() == null ? "" : input.getText().toString().trim(); }

    private void setupValidation() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.validateRegister(
                        etName.getText().toString(),
                        etMatrix.getText().toString(),
                        etEmail.getText().toString(),
                        etPassword.getText().toString()
                );
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        etName.addTextChangedListener(watcher);
        etMatrix.addTextChangedListener(watcher);
        etEmail.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);

        viewModel.nameError.observe(this, error -> tilName.setError(error));
        viewModel.matrixError.observe(this, error -> tilMatrix.setError(error));
        viewModel.emailError.observe(this, error -> tilEmail.setError(error));
        viewModel.passwordError.observe(this, error -> tilPassword.setError(error));
        viewModel.isRegisterFormValid.observe(this, isValid -> btnRegister.setEnabled(isValid));
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
