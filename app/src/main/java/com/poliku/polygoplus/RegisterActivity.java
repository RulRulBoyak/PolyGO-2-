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
import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        AppDataStore.initialize(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        String[] roles = {"Student", "Lecturer", "Staff", "Visitor"};
        AutoCompleteTextView autoCompleteRole = findViewById(R.id.autoCompleteRole);
        autoCompleteRole.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, roles));

        findViewById(R.id.btnRegisterAction).setOnClickListener(v -> {
            String name = text(R.id.etFullName); 
            String studentId = text(R.id.etMatrixNo); 
            String email = text(R.id.etEmail); 
            String password = text(R.id.etPassword);
            String role = autoCompleteRole.getText().toString();

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(studentId) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) { Toast.makeText(this, "Complete all fields", Toast.LENGTH_SHORT).show(); return; }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { ((TextInputEditText)findViewById(R.id.etEmail)).setError("Enter a valid email"); return; }
            if (password.length() < 6) { ((TextInputEditText)findViewById(R.id.etPassword)).setError("Use at least 6 characters"); return; }
            findViewById(R.id.btnRegisterAction).setEnabled(false);
            NetworkApi.register(name, studentId, email, password, new NetworkApi.Callback() {
                @Override public void onSuccess(org.json.JSONObject response) {
                    try {
                        org.json.JSONObject userObj = response.optJSONObject("user");
                        if (userObj != null) userObj.put("role", role); // Inject role into user session
                        AppDataStore.saveRemoteSession(RegisterActivity.this, userObj);
                    } catch (Exception ignored) {}
                    Toast.makeText(RegisterActivity.this, "Account created", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, HomeActivity.class));
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    finish();
                }
                @Override public void onError(String message) {
                    findViewById(R.id.btnRegisterAction).setEnabled(true);
                    String userFriendlyMessage = message;
                    if (message.toLowerCase().contains("already exists")) {
                        userFriendlyMessage = "An account with this Student ID or Email already exists.";
                    } else if (message.toLowerCase().contains("password")) {
                        userFriendlyMessage = "Invalid password format. Please use a stronger password.";
                    }
                    Toast.makeText(RegisterActivity.this, userFriendlyMessage, Toast.LENGTH_LONG).show();
                }
            });
        });

        findViewById(R.id.tvLoginLink).setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private String text(int id) { TextInputEditText input = findViewById(id); return input.getText() == null ? "" : input.getText().toString().trim(); }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
