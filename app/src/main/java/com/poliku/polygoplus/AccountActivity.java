package com.poliku.polygoplus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.HapticManager;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class AccountActivity extends AppCompatActivity {

    @Inject PolyGoRepository polyGoRepository;
    private String selectedPhotoUri = "";
    private ShapeableImageView imgProfile;
    private boolean updatingPrivacy;
    private String pendingExportJson;
    private String pendingExportCsv;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPicker;
    private ActivityResultLauncher<Intent> documentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        AppDataStore.initialize(this);

        documentLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                writeExportFile(result.getData().getData());
            }
        });

        photoPicker = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                try {
                    selectedPhotoUri = uri.toString();
                    imgProfile.setImageURI(uri);
                } catch (Exception ignored) {}
            }
        });

        imgProfile = findViewById(R.id.imgProfilePhoto);
        String currentPhoto = AppDataStore.userProfilePic(this);
        if (!currentPhoto.isEmpty()) {
            try {
                imgProfile.setImageURI(Uri.parse(currentPhoto));
                if (imgProfile.getDrawable() == null) {
                    imgProfile.setImageResource(R.drawable.ic_user_line);
                }
                selectedPhotoUri = currentPhoto;
            } catch (Exception e) {
                imgProfile.setImageResource(R.drawable.ic_user_line);
            }
        }

        ((TextInputEditText)findViewById(R.id.etFirstName)).setText(firstName(AppDataStore.userName(this)));
        ((TextInputEditText)findViewById(R.id.etLastName)).setText(lastName(AppDataStore.userName(this)));
        ((TextInputEditText)findViewById(R.id.etEmail)).setText(AppDataStore.userEmail(this));
        ((TextInputEditText)findViewById(R.id.etMobileNo)).setText(AppDataStore.userMobile(this));
        ((TextInputEditText)findViewById(R.id.etBio)).setText(AppDataStore.userBio(this));

        MaterialSwitch privacySwitch = findViewById(R.id.switchPrivateAccount);
        privacySwitch.setChecked(AppDataStore.isPrivateAccount(this));
        privacySwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            if (updatingPrivacy) return;
            HapticManager.lightTap(btn);
            AppDataStore.setAccountPrivacy(this, isChecked);
            String userId = AppDataStore.userId(this);
            polyGoRepository.updateAccountPrivacy(userId, isChecked, new Callback<BaseResponse>() {
                @Override
                public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                    if (!response.isSuccessful() || response.body() == null || !response.body().isSuccess()) {
                        revertPrivacy();
                    }
                }

                @Override
                public void onFailure(Call<BaseResponse> call, Throwable t) {
                    revertPrivacy();
                }
            });
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        
        findViewById(R.id.btnChangePhoto).setOnClickListener(v -> {
            photoPicker.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        findViewById(R.id.btnUpdateProfile).setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            String first = value(R.id.etFirstName);
            String last = value(R.id.etLastName);
            String email = value(R.id.etEmail);
            String mobile = value(R.id.etMobileNo);
            String bio = value(R.id.etBio);

            if (first.isEmpty() || last.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Complete your profile", Toast.LENGTH_SHORT).show();
                return;
            }

            v.setEnabled(false);
            String userId = AppDataStore.userId(this);
            String fullName = first + " " + last;

            // If user picked a new LOCAL photo, upload it FIRST
            if (!selectedPhotoUri.isEmpty() && selectedPhotoUri.startsWith("content://")) {
                Toast.makeText(this, "Uploading new profile picture...", Toast.LENGTH_SHORT).show();
                polyGoRepository.uploadImage(this, Uri.parse(selectedPhotoUri), new Callback<PolyGoApi.UploadResponse>() {
                    @Override
                    public void onResponse(Call<PolyGoApi.UploadResponse> call, Response<PolyGoApi.UploadResponse> response) {
                        PolyGoApi.UploadResponse body = response.body();
                        if (body != null && body.isSuccess()) {
                            saveProfile(v, userId, fullName, email, mobile, bio, body.url);
                        } else {
                            v.setEnabled(true);
                            Toast.makeText(AccountActivity.this, "Upload failed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<PolyGoApi.UploadResponse> call, Throwable t) {
                        v.setEnabled(true);
                        Toast.makeText(AccountActivity.this, "Upload failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                // No new photo, just update text data
                saveProfile(v, userId, fullName, email, mobile, bio, selectedPhotoUri);
            }
        });
        findViewById(R.id.btnDeleteAccount).setOnClickListener(v -> {
            HapticManager.heavyTap(v);
            new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_warning)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_account_confirm, (d, w) -> {
                    HapticManager.error(this);
                    deleteAccountNow();
                }).show();
        });

        findViewById(R.id.btnDownloadMyData).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            downloadMyData(v);
        });
    }

    private void deleteAccountNow() {
        Toast.makeText(this, R.string.deleting_account, Toast.LENGTH_SHORT).show();
        polyGoRepository.deleteAccount(new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                boolean ok = response.isSuccessful() && response.body() != null && response.body().isSuccess();
                if (ok) {
                    AppDataStore.deleteAccount(AccountActivity.this);
                    Intent intent = new Intent(AccountActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                } else {
                    HapticManager.error(AccountActivity.this);
                    Toast.makeText(AccountActivity.this, R.string.delete_account_failed, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                HapticManager.error(AccountActivity.this);
                Toast.makeText(AccountActivity.this, R.string.delete_account_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void downloadMyData(View trigger) {
        Toast.makeText(this, R.string.preparing_data_export, Toast.LENGTH_SHORT).show();
        trigger.setEnabled(false);
        polyGoRepository.exportData(new Callback<PolyGoApi.ExportDataResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.ExportDataResponse> call, Response<PolyGoApi.ExportDataResponse> response) {
                trigger.setEnabled(true);
                PolyGoApi.ExportDataResponse body = response.body();
                if (response.isSuccessful() && body != null && body.isSuccess() && body.data != null) {
                    JsonObject export = new JsonObject();
                    export.addProperty("polygo_user_id", AppDataStore.userId(AccountActivity.this));
                    export.add("data", body.data);
                    pendingExportJson = export.toString();
                    pendingExportCsv = exportToCsv(body.data);
                    new AlertDialog.Builder(AccountActivity.this)
                        .setTitle(R.string.export_format_title)
                        .setItems(new CharSequence[]{ getString(R.string.export_as_json), getString(R.string.export_as_csv) }, (d, which) -> {
                            boolean csv = which == 1;
                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType(csv ? "text/csv" : "application/json");
                            intent.putExtra(Intent.EXTRA_TITLE, csv ? "polygo_my_data.csv" : "polygo_my_data.json");
                            documentLauncher.launch(intent);
                        }).show();
                } else {
                    Toast.makeText(AccountActivity.this, R.string.export_failed, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.ExportDataResponse> call, Throwable t) {
                trigger.setEnabled(true);
                Toast.makeText(AccountActivity.this, R.string.export_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void writeExportFile(Uri destination) {
        String content = pendingExportCsv != null ? pendingExportCsv : pendingExportJson;
        if (content == null) return;
        try {
            getContentResolver().openOutputStream(destination, "wt").write(
                    content.getBytes("UTF-8"));
            Toast.makeText(this, R.string.export_saved, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show();
        }
    }

    private String exportToCsv(JsonObject data) {
        StringBuilder sb = new StringBuilder();
        sb.append("# PolyGo+ data export - generated for user ").append(AppDataStore.userId(this)).append('\n');
        JsonObject profile = data.has("profile") && data.get("profile").isJsonObject() ? data.getAsJsonObject("profile") : null;
        if (profile != null && !profile.entrySet().isEmpty()) {
            sb.append("#\n# profile\n");
            appendCsvRow(sb, profile);
        }
        String[] sections = {"listings", "favorites", "threads", "messages", "transactions",
            "reviews_given", "reviews_received", "notifications", "reports_submitted",
            "security_alerts", "green_impact"};
        for (String section : sections) {
            JsonElement el = data.get(section);
            if (el == null || !el.isJsonArray()) continue;
            JsonArray arr = el.getAsJsonArray();
            if (arr.size() == 0) continue;
            sb.append("#\n# ").append(section).append('\n');
            appendCsvRows(sb, arr);
        }
        return sb.toString();
    }

    private void appendCsvRows(StringBuilder sb, JsonArray arr) {
        if (arr.size() == 0) return;
        JsonElement first = arr.get(0);
        if (!first.isJsonObject()) {
            for (JsonElement el : arr) sb.append(csvEscape(el.getAsString())).append('\n');
            return;
        }
        java.util.Set<String> headers = first.getAsJsonObject().keySet();
        boolean firstHeader = true;
        for (String h : headers) {
            if (!firstHeader) sb.append(',');
            sb.append(csvEscape(h));
            firstHeader = false;
        }
        sb.append('\n');
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            boolean firstCell = true;
            for (String h : headers) {
                if (!firstCell) sb.append(',');
                JsonElement value = obj.get(h);
                sb.append(csvEscape(value == null || value.isJsonNull() ? "" : value.getAsString()));
                firstCell = false;
            }
            sb.append('\n');
        }
    }

    private void appendCsvRow(StringBuilder sb, JsonObject obj) {
        boolean firstCell = true;
        for (java.util.Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (!firstCell) sb.append(',');
            sb.append(csvEscape(e.getValue() == null || e.getValue().isJsonNull() ? "" : e.getValue().getAsString()));
            firstCell = false;
        }
        sb.append('\n');
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void saveProfile(View btn, String userId, String name, String email, String mobile, String bio, String photoUrl) {
        polyGoRepository.updateProfile(userId, name, email, mobile, photoUrl, bio, new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    HapticManager.success(AccountActivity.this);
                    AppDataStore.updateProfile(AccountActivity.this, name, email, mobile, photoUrl, bio);
                    Toast.makeText(AccountActivity.this, "Profile saved", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    HapticManager.error(AccountActivity.this);
                    btn.setEnabled(true);
                    Toast.makeText(AccountActivity.this, "Save error", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                HapticManager.error(AccountActivity.this);
                btn.setEnabled(true);
                Toast.makeText(AccountActivity.this, "Save error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void revertPrivacy() {
        updatingPrivacy = true;
        ((MaterialSwitch) findViewById(R.id.switchPrivateAccount)).setChecked(false);
        AppDataStore.setAccountPrivacy(this, false);
        updatingPrivacy = false;
        HapticManager.error(this);
        Toast.makeText(this, getString(R.string.account_private_error), Toast.LENGTH_SHORT).show();
    }

    private String value(int id) {
        TextInputEditText input = findViewById(id);
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String firstName(String name) {
        String[] parts = name.trim().split("\\s+");
        return parts.length == 0 ? "" : parts[0];
    }

    private String lastName(String name) {
        String[] parts = name.trim().split("\\s+");
        return parts.length < 2 ? "" : parts[parts.length - 1];
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
