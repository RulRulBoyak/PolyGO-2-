package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        AppDataStore.initialize(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnLoginNormal).setOnClickListener(v -> {
            String studentId = ((com.google.android.material.textfield.TextInputEditText)findViewById(R.id.etMatrixNo)).getText().toString().trim();
            String password = ((com.google.android.material.textfield.TextInputEditText)findViewById(R.id.etPassword)).getText().toString();
            if (studentId.isEmpty() || password.isEmpty()) { Toast.makeText(this, "Enter your ID and password", Toast.LENGTH_SHORT).show(); return; }
            findViewById(R.id.btnLoginNormal).setEnabled(false);
            NetworkApi.login(studentId, password, new NetworkApi.Callback() {
                @Override public void onSuccess(org.json.JSONObject response) {
                    AppDataStore.saveRemoteSession(LoginActivity.this, response.optJSONObject("user"));
                    startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    finish();
                }
                @Override public void onError(String message) {
                    findViewById(R.id.btnLoginNormal).setEnabled(true);
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

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
