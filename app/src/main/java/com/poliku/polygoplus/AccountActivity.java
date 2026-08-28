package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.poliku.polygoplus.data.AppDataStore;

public class AccountActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        AppDataStore.initialize(this);
        ((TextInputEditText)findViewById(R.id.etFirstName)).setText(firstName(AppDataStore.userName(this)));
        ((TextInputEditText)findViewById(R.id.etLastName)).setText(lastName(AppDataStore.userName(this)));
        ((TextInputEditText)findViewById(R.id.etEmail)).setText(AppDataStore.userEmail(this));
        ((TextInputEditText)findViewById(R.id.etMobileNo)).setText(AppDataStore.userMobile(this));

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        
        findViewById(R.id.btnUpdateProfile).setOnClickListener(v -> {
            String first = value(R.id.etFirstName);
            String last = value(R.id.etLastName); String email = value(R.id.etEmail); String mobile = value(R.id.etMobileNo);
            if (first.isEmpty() || last.isEmpty() || email.isEmpty()) { Toast.makeText(this, "Complete your profile", Toast.LENGTH_SHORT).show(); return; }
            AppDataStore.updateProfile(this, first + " " + last, email, mobile);
            Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
            finish();
        });
        findViewById(R.id.btnDeleteAccount).setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete account")
                .setMessage("This removes your local PolyGo+ session and profile from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    AppDataStore.deleteAccount(this);
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }).show());
    }

    private String value(int id) { TextInputEditText input=findViewById(id); return input.getText()==null?"":input.getText().toString().trim(); }
    private String firstName(String name) { String[] parts=name.trim().split("\\s+"); return parts.length == 0 ? "" : parts[0]; }
    private String lastName(String name) { String[] parts=name.trim().split("\\s+"); return parts.length < 2 ? "" : parts[parts.length - 1]; }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
