package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.ExitGuard;
import com.poliku.polygoplus.viewmodel.AuthViewModel;

import androidx.activity.OnBackPressedCallback;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ForgotPasswordActivity extends BaseActivity {
    @Inject PolyGoRepository polyGoRepository;

    private EditText identifierInput;
    private EditText codeInput;
    private EditText passwordInput;
    private EditText confirmInput;
    private MaterialButton actionButton;
    private View resetFields;
    private boolean codeSent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        identifierInput = findViewById(R.id.etResetId);
        codeInput = findViewById(R.id.etResetCode);
        passwordInput = findViewById(R.id.etResetPassword);
        confirmInput = findViewById(R.id.etResetConfirm);
        actionButton = findViewById(R.id.btnSendReset);
        resetFields = findViewById(R.id.resetFields);

        findViewById(R.id.btnBack).setOnClickListener(v -> confirmExit());
        actionButton.setOnClickListener(v -> {
            if (codeSent) resetPassword(); else requestCode();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });
    }

    private void confirmExit() {
        if (!ExitGuard.anyText(identifierInput.getText(), codeInput.getText(),
                passwordInput.getText(), confirmInput.getText())) {
            finish();
            return;
        }
        ExitGuard.show(this, this::finish);
    }

    private void requestCode() {
        String identifier = identifierInput.getText().toString().trim();
        if (identifier.isEmpty()) {
            Toast.makeText(this, R.string.toast_enter_id_or_email, Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);
        polyGoRepository.requestPasswordReset(identifier, new Callback<PolyGoApi.ForgotPasswordResponse>() {
            @Override public void onResponse(Call<PolyGoApi.ForgotPasswordResponse> call,
                                             Response<PolyGoApi.ForgotPasswordResponse> response) {
                setLoading(false);
                PolyGoApi.ForgotPasswordResponse body = response.isSuccessful() ? response.body() : null;
                if (body == null || !body.isSuccess()) {
                    showMessage(body != null ? body.getMessage() : null, R.string.toast_reset_failed);
                    return;
                }
                codeSent = true;
                identifierInput.setEnabled(false);
                resetFields.setVisibility(View.VISIBLE);
                actionButton.setText(R.string.reset_password);
                if (body.resetCode != null) codeInput.setText(body.resetCode);
                Toast.makeText(ForgotPasswordActivity.this,
                        R.string.reset_code_sent, Toast.LENGTH_LONG).show();
            }

            @Override public void onFailure(Call<PolyGoApi.ForgotPasswordResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(ForgotPasswordActivity.this,
                        R.string.toast_could_not_reach_server_try_again, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void resetPassword() {
        String code = codeInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirmation = confirmInput.getText().toString();
        if (!code.matches("\\d{6}")) {
            codeInput.setError(getString(R.string.error_reset_code));
            return;
        }
        if (password.length() < AuthViewModel.MIN_PASSWORD_LENGTH
                || password.length() > AuthViewModel.MAX_PASSWORD_LENGTH) {
            passwordInput.setError(getString(R.string.error_password_min_length));
            return;
        }
        if (!password.equals(confirmation)) {
            confirmInput.setError(getString(R.string.error_passwords_do_not_match));
            return;
        }
        setLoading(true);
        polyGoRepository.resetPassword(identifierInput.getText().toString().trim(), code, password,
                new Callback<PolyGoApi.ForgotPasswordResponse>() {
                    @Override public void onResponse(Call<PolyGoApi.ForgotPasswordResponse> call,
                                                     Response<PolyGoApi.ForgotPasswordResponse> response) {
                        setLoading(false);
                        PolyGoApi.ForgotPasswordResponse body = response.isSuccessful() ? response.body() : null;
                        if (body != null && body.isSuccess()) {
                            Toast.makeText(ForgotPasswordActivity.this,
                                    R.string.password_reset_success, Toast.LENGTH_LONG).show();
                            finish();
                        } else {
                            showMessage(body != null ? body.getMessage() : null, R.string.toast_reset_failed);
                        }
                    }

                    @Override public void onFailure(Call<PolyGoApi.ForgotPasswordResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(ForgotPasswordActivity.this,
                                R.string.toast_could_not_reach_server_try_again, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        actionButton.setEnabled(!loading);
    }

    private void showMessage(String message, int fallback) {
        Toast.makeText(this, message == null || message.isEmpty() ? getString(fallback) : message,
                Toast.LENGTH_LONG).show();
    }
}
