package com.poliku.polygoplus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.UiUtils;

import java.util.Locale;

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
    private View layoutForm, layoutStudent, layoutAlumni;
    private TextInputEditText etMatrixNo, etAlumniQ1, etAlumniQ2;
    private MaterialButton btnSubmit, btnSimulate;
    private ChipGroup chipGroupRole;
    
    private Uri selectedImageUri;
    private ActivityResultLauncher<PickVisualMediaRequest> imagePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verification);

        imagePicker = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                ivMatrixPreview.setImageURI(uri);
                ivMatrixPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ivMatrixPreview.setColorFilter(null);
            }
        });
        
        initViews();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                returnHome();
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
        layoutStudent = findViewById(R.id.layoutStudent);
        layoutAlumni = findViewById(R.id.layoutAlumni);
        etMatrixNo = findViewById(R.id.etMatrixNo);
        etAlumniQ1 = findViewById(R.id.etAlumniQ1);
        etAlumniQ2 = findViewById(R.id.etAlumniQ2);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnSimulate = findViewById(R.id.btnSimulate);
        chipGroupRole = findViewById(R.id.chipGroupRole);
        
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> returnHome());
    }

    private void returnHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void setupListeners() {
        chipGroupRole.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            HapticManager.lightTap(group);
            if (checkedId == R.id.chipStudent) {
                layoutStudent.setVisibility(View.VISIBLE);
                layoutAlumni.setVisibility(View.GONE);
            } else if (checkedId == R.id.chipAlumni) {
                layoutStudent.setVisibility(View.GONE);
                layoutAlumni.setVisibility(View.VISIBLE);
            }
        });

        findViewById(R.id.btnSnapMatrix).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            imagePicker.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        btnSubmit.setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            submitVerification();
        });

        btnSimulate.setOnClickListener(v -> {
            HapticManager.success(this);
            AppDataStore.approvePendingVerification(this);
            UiUtils.snackbar(findViewById(android.R.id.content), R.string.toast_verification_approved_dev);
            render();
        });
        if (!BuildConfig.DEBUG) {
            btnSimulate.setVisibility(View.GONE);
        }
    }

    private void submitVerification() {
        int checkedId = chipGroupRole.getCheckedChipId();
        if (checkedId == R.id.chipStudent) {
            String matrix = etMatrixNo.getText().toString().trim();
            if (selectedImageUri == null && matrix.isEmpty()) {
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_snap_card_or_enter_matrix);
                return;
            }
        } else if (checkedId == R.id.chipAlumni) {
            String q1 = etAlumniQ1.getText().toString().trim();
            String q2 = etAlumniQ2.getText().toString().trim();
            if (q1.isEmpty() || q2.isEmpty()) {
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_answer_alumni_questions);
                return;
            }
            // Basic verification for alumni (Futuristic: would check against alumni DB)
            if (!q1.toLowerCase(Locale.ROOT).contains("pks") && !q1.toLowerCase(Locale.ROOT).contains("poliku")) {
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_challenge_answer_incorrect);
                return;
            }
        }

        if (selectedImageUri == null) {
            // No card photo (e.g. alumni challenge flow): keep the local simulation.
            AppDataStore.submitVerification(this);
            HapticManager.success(this);
            UiUtils.snackbar(findViewById(android.R.id.content), getString(R.string.verification_submitted));
            render();
            return;
        }

        btnSubmit.setEnabled(false);
        UiUtils.snackbar(findViewById(android.R.id.content), getString(R.string.verification_uploading));
        polyGoRepository.uploadImage(this, selectedImageUri, new Callback<PolyGoApi.UploadResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.UploadResponse> call, Response<PolyGoApi.UploadResponse> response) {
                PolyGoApi.UploadResponse body = response.body();
                if (response.isSuccessful() && body != null && body.isSuccess()
                        && body.url != null && !body.url.isEmpty()) {
                    submitToServer(body.url);
                } else {
                    finishUploadFailure();
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.UploadResponse> call, Throwable t) {
                finishUploadFailure();
            }
        });
    }

    private void submitToServer(String photoUrl) {
        polyGoRepository.submitVerification(photoUrl, new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                boolean ok = response.isSuccessful() && response.body() != null && response.body().isSuccess();
                AppDataStore.submitVerification(VerificationActivity.this);
                HapticManager.success(VerificationActivity.this);
                if (ok) {
                    UiUtils.snackbar(VerificationActivity.this.findViewById(android.R.id.content),
                            R.string.verification_submitted);
                } else {
                    UiUtils.snackbarError(VerificationActivity.this.findViewById(android.R.id.content),
                            R.string.verification_server_failed);
                }
                btnSubmit.setEnabled(true);
                render();
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                AppDataStore.submitVerification(VerificationActivity.this);
                UiUtils.snackbarError(VerificationActivity.this.findViewById(android.R.id.content), R.string.verification_server_failed);
                btnSubmit.setEnabled(true);
                render();
            }
        });
    }

    private void finishUploadFailure() {
        btnSubmit.setEnabled(true);
        UiUtils.snackbarError(findViewById(android.R.id.content), R.string.verification_upload_failed);
    }

    private void render() {
        String state = AppDataStore.verificationStatus(this);
        
        if ("approved".equals(state)) {
            ivStatusIcon.setImageResource(R.drawable.ic_verified);
            ivStatusIcon.setColorFilter(getResources().getColor(R.color.pks_green));
            tvStatus.setText(getString(R.string.verified_member));
            layoutForm.setVisibility(View.GONE);
            btnSimulate.setVisibility(View.GONE);
        } else if ("pending".equals(state)) {
            ivStatusIcon.setImageResource(R.drawable.ic_history);
            ivStatusIcon.setColorFilter(getResources().getColor(R.color.polygo_amber));
            tvStatus.setText(R.string.review_in_progress);
            layoutForm.setVisibility(View.GONE);
            btnSimulate.setVisibility(View.VISIBLE);
        } else {
            ivStatusIcon.setImageResource(R.drawable.ic_shield_check);
            ivStatusIcon.setColorFilter(getResources().getColor(R.color.airbnb_muted));
            tvStatus.setText(R.string.verification_required);
            layoutForm.setVisibility(View.VISIBLE);
            btnSimulate.setVisibility(View.GONE);
        }
    }
}
