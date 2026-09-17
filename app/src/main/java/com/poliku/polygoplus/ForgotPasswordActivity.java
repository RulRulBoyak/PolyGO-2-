package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.poliku.polygoplus.ui.BaseActivity;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.PolyGoRepository;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ForgotPasswordActivity extends BaseActivity {
    @Inject PolyGoRepository polyGoRepository;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSendReset).setOnClickListener(v -> {
            String id = ((EditText) findViewById(R.id.etResetId)).getText().toString().trim();
            if (id.isEmpty()) {
                Toast.makeText(this, R.string.toast_enter_id_or_email, Toast.LENGTH_SHORT).show();
                return;
            }
            findViewById(R.id.btnSendReset).setEnabled(false);
            polyGoRepository.forgotPassword(id, new Callback<PolyGoApi.ForgotPasswordResponse>() {
                @Override public void onResponse(Call<PolyGoApi.ForgotPasswordResponse> call, Response<PolyGoApi.ForgotPasswordResponse> response) {
                    PolyGoApi.ForgotPasswordResponse body = response.isSuccessful() ? response.body() : null;
                    if (body != null && body.isSuccess()) {
                        String temp = body.temporaryPassword;
                        if (temp == null || temp.isEmpty()) {
                            temp = getString(R.string.forgot_temp_password_email);
                        }
                        new MaterialAlertDialogBuilder(ForgotPasswordActivity.this)
                                .setTitle(R.string.dialog_reset_issued)
                                .setMessage(getString(R.string.dialog_reset_issued_message, temp))
                                .setPositiveButton(android.R.string.ok, (d, w) -> {
                                    findViewById(R.id.btnSendReset).setEnabled(true);
                                    finish();
                                })
                                .setOnCancelListener(d -> findViewById(R.id.btnSendReset).setEnabled(true))
                                .show();
                    } else {
                        findViewById(R.id.btnSendReset).setEnabled(true);
                        Toast.makeText(ForgotPasswordActivity.this,
                                body != null && body.getMessage() != null ? body.getMessage() : getString(R.string.toast_reset_failed),
                                Toast.LENGTH_LONG).show();
                    }
                }
                @Override public void onFailure(Call<PolyGoApi.ForgotPasswordResponse> call, Throwable t) {
                    findViewById(R.id.btnSendReset).setEnabled(true);
                    Toast.makeText(ForgotPasswordActivity.this, R.string.toast_could_not_reach_server_try_again, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}