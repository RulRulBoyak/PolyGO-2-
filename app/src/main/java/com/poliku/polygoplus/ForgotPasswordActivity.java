package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;

public class ForgotPasswordActivity extends AppCompatActivity {
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
            String temp = AppDataStore.requestPasswordReset(this, id);
            NetworkApi.forgotPassword(id, new NetworkApi.Callback() {
                @Override public void onSuccess(org.json.JSONObject response) { }
                @Override public void onError(String message) { }
            });
            if (temp == null) {
                Toast.makeText(this, "No matching campus account found", Toast.LENGTH_LONG).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Temporary password")
                    .setMessage("Use this password to log in, then change it in Settings:\n\n" + temp)
                    .setPositiveButton("OK", (d, w) -> finish())
                    .show();
        });
    }
}
