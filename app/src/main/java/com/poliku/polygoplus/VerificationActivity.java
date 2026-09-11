package com.poliku.polygoplus;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
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
public class VerificationActivity extends AppCompatActivity {

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
        setupListeners();
        render();
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
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());
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
            Toast.makeText(this, "Verification Approved (Dev)", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Please snap your card or enter Matrix No", Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (checkedId == R.id.chipAlumni) {
            String q1 = etAlumniQ1.getText().toString().trim();
            String q2 = etAlumniQ2.getText().toString().trim();
            if (q1.isEmpty() || q2.isEmpty()) {
                Toast.makeText(this, "Please answer the alumni challenge questions", Toast.LENGTH_SHORT).show();
                return;
            }
            // Basic verification for alumni (Futuristic: would check against alumni DB)
            if (!q1.toLowerCase().contains("pks") && !q1.toLowerCase().contains("poliku")) {
                Toast.makeText(this, "Challenge answer 1 seems incorrect", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (selectedImageUri == null) {
            // No card photo (e.g. alumni challenge flow): keep the local simulation.
            AppDataStore.submitVerification(this);
            HapticManager.success(this);
            Toast.makeText(this, getString(R.string.verification_submitted), Toast.LENGTH_LONG).show();
            render();
            return;
        }

        btnSubmit.setEnabled(false);
        Toast.makeText(this, getString(R.string.verification_uploading), Toast.LENGTH_SHORT).show();
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
                Toast.makeText(VerificationActivity.this,
                        ok ? R.string.verification_submitted : R.string.verification_server_failed,
                        Toast.LENGTH_LONG).show();
                btnSubmit.setEnabled(true);
                render();
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                AppDataStore.submitVerification(VerificationActivity.this);
                Toast.makeText(VerificationActivity.this, R.string.verification_server_failed, Toast.LENGTH_LONG).show();
                btnSubmit.setEnabled(true);
                render();
            }
        });
    }

    private void finishUploadFailure() {
        btnSubmit.setEnabled(true);
        Toast.makeText(this, R.string.verification_upload_failed, Toast.LENGTH_LONG).show();
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
            ivStatusIcon.setColorFilter(getResources().getColor(R.color.pks_blue));
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
