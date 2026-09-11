package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.PolyGoRepository;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ForgotPasswordActivity extends AppCompatActivity {
    @Inject PolyGoRepository polyGoRepository;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSendReset).setOnClickListener(v -> {
            String id = ((EditText) findViewById(R.id.etResetId)).getText().toString().trim();
            if (id.isEmpty()) {
                Toast.makeText(this, "Enter your ID or email", Toast.LENGTH_SHORT).show();
                return;
            }
            findViewById(R.id.btnSendReset).setEnabled(false);
            polyGoRepository.forgotPassword(id, new Callback<PolyGoApi.ForgotPasswordResponse>() {
                @Override public void onResponse(Call<PolyGoApi.ForgotPasswordResponse> call, Response<PolyGoApi.ForgotPasswordResponse> response) {
                    PolyGoApi.ForgotPasswordResponse body = response.isSuccessful() ? response.body() : null;
                    if (body != null && body.isSuccess()) {
                        String temp = body.temporaryPassword;
                        if (temp == null || temp.isEmpty()) {
                            temp = "Use the temporary password sent to your email";
                        }
                        new AlertDialog.Builder(ForgotPasswordActivity.this)
                                .setTitle("Reset issued")
                                .setMessage("Use this temporary password to log in, then change it in Profile:\n\n" + temp)
                                .setPositiveButton("OK", (d, w) -> {
                                    findViewById(R.id.btnSendReset).setEnabled(true);
                                    finish();
                                })
                                .setOnCancelListener(d -> findViewById(R.id.btnSendReset).setEnabled(true))
                                .show();
                    } else {
                        findViewById(R.id.btnSendReset).setEnabled(true);
                        Toast.makeText(ForgotPasswordActivity.this,
                                body != null && body.getMessage() != null ? body.getMessage() : "Reset failed, please try again",
                                Toast.LENGTH_LONG).show();
                    }
                }
                @Override public void onFailure(Call<PolyGoApi.ForgotPasswordResponse> call, Throwable t) {
                    findViewById(R.id.btnSendReset).setEnabled(true);
                    Toast.makeText(ForgotPasswordActivity.this, "Could not reach server, please try again", Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}