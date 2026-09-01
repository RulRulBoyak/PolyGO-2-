package com.poliku.polygoplus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;

import org.json.JSONObject;

public class AccountActivity extends AppCompatActivity {

    private String selectedPhotoUri = "";
    private ShapeableImageView imgProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        AppDataStore.initialize(this);

        imgProfile = findViewById(R.id.imgProfilePhoto);
        String currentPhoto = AppDataStore.userProfilePic(this);
        if (!currentPhoto.isEmpty()) {
            try {
                imgProfile.setImageURI(Uri.parse(currentPhoto));
                if (imgProfile.getDrawable() == null) {
                    imgProfile.setImageResource(android.R.drawable.ic_menu_report_image);
                }
                selectedPhotoUri = currentPhoto;
            } catch (Exception e) {
                imgProfile.setImageResource(android.R.drawable.ic_menu_report_image);
            }
        }

        ((TextInputEditText)findViewById(R.id.etFirstName)).setText(firstName(AppDataStore.userName(this)));
        ((TextInputEditText)findViewById(R.id.etLastName)).setText(lastName(AppDataStore.userName(this)));
        ((TextInputEditText)findViewById(R.id.etEmail)).setText(AppDataStore.userEmail(this));
        ((TextInputEditText)findViewById(R.id.etMobileNo)).setText(AppDataStore.userMobile(this));

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        
        findViewById(R.id.btnChangePhoto).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 43);
        });

        findViewById(R.id.btnUpdateProfile).setOnClickListener(v -> {
            String first = value(R.id.etFirstName);
            String last = value(R.id.etLastName); 
            String email = value(R.id.etEmail); 
            String mobile = value(R.id.etMobileNo);
            
            if (first.isEmpty() || last.isEmpty() || email.isEmpty()) { 
                Toast.makeText(this, "Complete your profile", Toast.LENGTH_SHORT).show(); 
                return; 
            }

            v.setEnabled(false);
            String userId = AppDataStore.userId(this);
            String fullName = first + " " + last;

            NetworkApi.updateProfile(userId, fullName, email, mobile, selectedPhotoUri, new NetworkApi.Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    AppDataStore.updateProfile(AccountActivity.this, fullName, email, mobile, selectedPhotoUri);
                    Toast.makeText(AccountActivity.this, "Profile saved", Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(String message) {
                    v.setEnabled(true);
                    Toast.makeText(AccountActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 43 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                selectedPhotoUri = uri.toString();
                imgProfile.setImageURI(uri);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
