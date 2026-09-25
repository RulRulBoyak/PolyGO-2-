package com.poliku.polygoplus;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import androidx.core.content.FileProvider;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.ExitGuard;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.UiUtils;

import java.io.File;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class VerificationActivity extends BaseActivity {

    @Inject PolyGoRepository polyGoRepository;

    private TextView tvStatus;
    private ImageView ivStatusIcon, ivMatrixPreview;
    private View layoutForm;
    private TextInputEditText etMatrixNo;
    private MaterialButton btnSubmit;
    private LinearProgressIndicator verificationProgress;
    private boolean submitting;
    
    private Uri selectedImageUri;
    private Uri pendingCameraUri;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verification);

        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
            if (saved && pendingCameraUri != null) showSelectedImage(pendingCameraUri);
        });
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) openCamera();
                    else UiUtils.snackbarError(findViewById(android.R.id.content),
                            R.string.toast_camera_permission_required);
                });
        
        initViews();
        String savedImage = savedInstanceState == null ? null
                : savedInstanceState.getString("selected_image");
        String pendingImage = savedInstanceState == null ? null
                : savedInstanceState.getString("pending_camera_image");
        if (pendingImage != null && !pendingImage.isEmpty()) {
            pendingCameraUri = Uri.parse(pendingImage);
        }
        if (savedImage != null && !savedImage.isEmpty()) {
            showSelectedImage(Uri.parse(savedImage));
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });
        setupListeners();
        render();
        syncServerStatus();
    }

    private void syncServerStatus() {
        // The admin page is the source of truth; refresh the cached status so a
        // rejection/approval made in the browser shows up here on the next visit.
        polyGoRepository.getVerificationStatus(new Callback<PolyGoApi.VerificationStatusResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.VerificationStatusResponse> call,
                                   Response<PolyGoApi.VerificationStatusResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().verificationStatus != null) {
                    AppDataStore.saveVerificationStatus(VerificationActivity.this,
                            response.body().verificationStatus);
                    render();
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.VerificationStatusResponse> call, Throwable t) {
                // Offline: keep the cached status; nothing to show the user.
            }
        });
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvVerificationStatus);
        ivStatusIcon = findViewById(R.id.ivStatusIcon);
        ivMatrixPreview = findViewById(R.id.ivMatrixPreview);
        layoutForm = findViewById(R.id.layoutForm);
        etMatrixNo = findViewById(R.id.etMatrixNo);
        btnSubmit = findViewById(R.id.btnSubmit);
        verificationProgress = findViewById(R.id.verificationProgress);
        
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> confirmExit());
    }

    private void confirmExit() {
        if (submitting) {
            UiUtils.snackbar(findViewById(android.R.id.content),
                    R.string.verification_wait_for_submission);
            return;
        }
        if (ExitGuard.anyText(etMatrixNo.getText())
                || selectedImageUri != null) {
            ExitGuard.show(this, this::returnHome);
            return;
        }
        returnHome();
    }

    private void returnHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void setupListeners() {
        findViewById(R.id.btnSnapMatrix).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        });

        btnSubmit.setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            submitVerification();
        });

    }

    private void openCamera() {
        try {
            File folder = new File(getCacheDir(), "verification-captures");
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IllegalStateException("Could not create camera folder");
            }
            File photo = new File(folder, "student-card-" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", photo);
            cameraLauncher.launch(pendingCameraUri);
        } catch (Exception error) {
            UiUtils.snackbarError(findViewById(android.R.id.content),
                    R.string.toast_error_starting_camera);
        }
    }

    private void showSelectedImage(Uri uri) {
        selectedImageUri = uri;
        ivMatrixPreview.setImageURI(uri);
        ivMatrixPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivMatrixPreview.setColorFilter(null);
    }

    private void submitVerification() {
        if (submitting) return;
        // The server's verify.php submit requires the card/selfie photo — a
        // submission without one would be invisible to the admin panel, so it
        // must never complete locally.
        if (selectedImageUri == null) {
            UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_snap_card_required);
            return;
        }

        setSubmitting(true);
        UiUtils.snackbar(findViewById(android.R.id.content), getString(R.string.verification_uploading));
        polyGoRepository.uploadImage(this, selectedImageUri, new Callback<PolyGoApi.UploadResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.UploadResponse> call, Response<PolyGoApi.UploadResponse> response) {
                PolyGoApi.UploadResponse body = response.body();
                if (response.isSuccessful() && body != null && body.isSuccess()
                        && body.url != null && !body.url.isEmpty()) {
                    submitToServer(body.url);
                } else {
                    finishUploadFailure(body == null ? null : body.getMessage());
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.UploadResponse> call, Throwable t) {
                finishUploadFailure(null);
            }
        });
    }

    private void submitToServer(String photoUrl) {
        polyGoRepository.submitVerification(photoUrl, new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                boolean ok = response.isSuccessful() && response.body() != null && response.body().isSuccess();
                if (ok) {
                    AppDataStore.submitVerification(VerificationActivity.this);
                    HapticManager.success(VerificationActivity.this);
                    UiUtils.snackbar(VerificationActivity.this.findViewById(android.R.id.content),
                            R.string.verification_submitted);
                } else {
                    UiUtils.snackbarError(VerificationActivity.this.findViewById(android.R.id.content),
                            R.string.verification_server_failed);
                }
                setSubmitting(false);
                render();
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                UiUtils.snackbarError(VerificationActivity.this.findViewById(android.R.id.content), R.string.verification_server_failed);
                setSubmitting(false);
                render();
            }
        });
    }

    private void finishUploadFailure(String message) {
        setSubmitting(false);
        UiUtils.snackbarError(findViewById(android.R.id.content),
                message == null || message.trim().isEmpty()
                        ? getString(R.string.verification_upload_failed) : message);
    }

    private void setSubmitting(boolean value) {
        submitting = value;
        btnSubmit.setEnabled(!value);
        btnSubmit.setText(value ? R.string.verification_uploading : R.string.submit_review);
        verificationProgress.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (selectedImageUri != null) {
            outState.putString("selected_image", selectedImageUri.toString());
        }
        if (pendingCameraUri != null) {
            outState.putString("pending_camera_image", pendingCameraUri.toString());
        }
        super.onSaveInstanceState(outState);
    }

    private void render() {
        String state = AppDataStore.verificationStatus(this);
        
        if ("approved".equals(state)) {
            ivStatusIcon.setImageResource(R.drawable.ic_verified);
            ivStatusIcon.setColorFilter(getResources().getColor(R.color.pks_green));
            tvStatus.setText(getString(R.string.verified_member));
            layoutForm.setVisibility(View.GONE);
        } else if ("pending".equals(state)) {
            ivStatusIcon.setImageResource(R.drawable.ic_history);
            ivStatusIcon.setColorFilter(getResources().getColor(R.color.polygo_amber));
            tvStatus.setText(R.string.review_in_progress);
            layoutForm.setVisibility(View.GONE);
        } else {
            ivStatusIcon.setImageResource(R.drawable.ic_shield_check);
            ivStatusIcon.setColorFilter(getResources().getColor(R.color.airbnb_muted));
            tvStatus.setText(R.string.verification_required);
            layoutForm.setVisibility(View.VISIBLE);
        }
    }
}
