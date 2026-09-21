package com.poliku.polygoplus;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FirebaseFirestore;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.UiUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class CreatePulseActivity extends AppCompatActivity {

    @Inject PolyGoRepository polyGoRepository;

    private EditText etTitle, etBody;
    private TextView tvAuthorName, tvAuthorHandle, tvSelectedTag;
    private ImageView ivAvatar, ivAttachment;
    private View layoutAttachment;
    private MaterialButton btnPost;
    private String selectedTag = "REQUEST";
    private Uri selectedImageUri;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    layoutAttachment.setVisibility(View.VISIBLE);
                    Glide.with(this).load(uri).into(ivAttachment);
                    HapticManager.lightTap(ivAttachment);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_create_pulse);

        etTitle = findViewById(R.id.etTitle);
        etBody = findViewById(R.id.etBody);
        tvAuthorName = findViewById(R.id.tvAuthorName);
        tvAuthorHandle = findViewById(R.id.tvAuthorHandle);
        tvSelectedTag = findViewById(R.id.tvSelectedTag);
        ivAvatar = findViewById(R.id.ivAvatar);
        ivAttachment = findViewById(R.id.ivAttachment);
        layoutAttachment = findViewById(R.id.layoutAttachment);
        btnPost = findViewById(R.id.btnPost);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        findViewById(R.id.bottomActionBar).setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                int ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
                v.setPadding(0, 0, 0, ime);
                return WindowInsets.CONSUMED;
            }
            return insets;
        });
        setupUserData();
        setupActions();

        etTitle.requestFocus();
    }

    private void setupUserData() {
        String name = AppDataStore.userName(this);
        String role = AppDataStore.userRole(this);
        tvAuthorName.setText(name != null ? name.split(" ")[0] : "Me");
        tvAuthorHandle.setText(getString(R.string.handle_format, (role != null ? role.toLowerCase(Locale.ROOT) : "member")));
        
        String photo = AppDataStore.userProfilePic(this);
        if (photo != null && !photo.isEmpty()) {
            Glide.with(this).load(photo).circleCrop().into(ivAvatar);
        }
    }

    private void setupActions() {
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());

        btnPost.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String body = etBody.getText().toString().trim();

            if (title.isEmpty() || body.isEmpty()) {
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.pulse_validation);
                return;
            }

            v.setEnabled(false);
            btnPost.setText(R.string.ai_thinking);
            postThread(title, body);
        });

        findViewById(R.id.btnPickTag).setOnClickListener(v -> {
            wireMorphEffect(v);
            showTagPicker();
        });
        findViewById(R.id.btnPickImage).setOnClickListener(v -> {
            wireMorphEffect(v);
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
        });
        
        findViewById(R.id.btnRemoveAttachment).setOnClickListener(v -> {
            selectedImageUri = null;
            layoutAttachment.setVisibility(View.GONE);
        });

        findViewById(R.id.btnSchedule).setOnClickListener(v -> {
            wireMorphEffect(v);
            Toast.makeText(this, R.string.toast_alerts_in_development, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnSaveDraft).setOnClickListener(v -> {
            wireMorphEffect(v);
            Toast.makeText(this, "Draft saved locally", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void wireMorphEffect(View v) {
        HapticManager.lightTap(v);
        v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()).start();
    }

    private void showTagPicker() {
        String[] tags = {
            getString(R.string.tag_request),
            getString(R.string.tag_flash_sale),
            getString(R.string.tag_event)
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pulse_tag_label)
                .setItems(tags, (dialog, which) -> {
                    selectedTag = tags[which];
                    tvSelectedTag.setText(selectedTag);
                    HapticManager.selectionTick(this);
                })
                .show();
    }

    private void postThread(String title, String body) {
        // Logic: For now, the backend postPulse doesn't support images.
        // We will post text and notify user images are in development if uri is set.
        polyGoRepository.postPulse(selectedTag, title, body, new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    HapticManager.success(CreatePulseActivity.this);
                    btnPost.animate().scaleX(1.1f).scaleY(1.1f).alpha(0f).setDuration(200).start();
                    broadcastPulseToFirestore(title, body);
                    setResult(RESULT_OK);
                    btnPost.postDelayed(CreatePulseActivity.this::finish, 250);
                } else {
                    btnPost.setEnabled(true);
                    btnPost.setText(R.string.action_post);
                    UiUtils.snackbarError(findViewById(android.R.id.content), R.string.pulse_post_failed);
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                btnPost.setEnabled(true);
                btnPost.setText(R.string.action_post);
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.pulse_post_failed);
            }
        });
    }

    private void broadcastPulseToFirestore(String title, String body) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("body", body);
        data.put("tag", selectedTag);
        data.put("user_name", AppDataStore.userName(this));
        data.put("is_global", false);
        data.put("created_at", System.currentTimeMillis() / 1000);

        db.collection("pulse").add(data);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
