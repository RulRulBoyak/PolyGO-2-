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
import com.poliku.polygoplus.ui.BaseActivity;

import android.text.Editable;
import android.text.TextWatcher;

public class RegisterActivity extends BaseActivity {
    private AuthViewModel viewModel;
    private TextInputEditText etName, etMatrix, etEmail, etPassword;
    private TextInputLayout tilName, tilMatrix, tilEmail, tilPassword;
    private com.google.android.material.checkbox.MaterialCheckBox cbTerms;
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
        cbTerms = findViewById(R.id.cbTerms);
        
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

        cbTerms.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setTermsAccepted(isChecked);
        });

        btnRegister.setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            String name = etName.getText().toString().trim();
            String studentId = etMatrix.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();
            String role = autoCompleteRole.getText().toString();

            btnRegister.setEnabled(false);
            viewModel.sendOtp(email, new retrofit2.Callback<com.poliku.polygoplus.api.model.BaseResponse>() {
                @Override
                public void onResponse(retrofit2.Call<com.poliku.polygoplus.api.model.BaseResponse> call, retrofit2.Response<com.poliku.polygoplus.api.model.BaseResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        HapticManager.success(RegisterActivity.this);
                        Intent intent = new Intent(RegisterActivity.this, OtpActivity.class);
                        intent.putExtra(OtpActivity.EXTRA_EMAIL, email);
                        intent.putExtra(OtpActivity.EXTRA_NAME, name);
                        intent.putExtra(OtpActivity.EXTRA_STUDENT_ID, studentId);
                        intent.putExtra(OtpActivity.EXTRA_PASSWORD, password);
                        startActivity(intent);
                    } else {
                        onError("Could not send code");
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<com.poliku.polygoplus.api.model.BaseResponse> call, Throwable t) {
                    onError(t.getMessage());
                }

                private void onError(String msg) {
                    HapticManager.error(RegisterActivity.this);
                    btnRegister.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, msg, Toast.LENGTH_LONG).show();
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
                if (count > 0) HapticManager.selectionTick(RegisterActivity.this);
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

    // Common animations handled by BaseActivity
}
