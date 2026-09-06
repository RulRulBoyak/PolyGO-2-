package com.poliku.polygoplus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.ui.HapticManager;

public class VerificationActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    
    private TextView tvStatus;
    private ImageView ivStatusIcon, ivMatrixPreview;
    private View layoutForm, layoutStudent, layoutAlumni;
    private TextInputEditText etMatrixNo, etAlumniQ1, etAlumniQ2;
    private MaterialButton btnSubmit, btnSimulate;
    private ChipGroup chipGroupRole;
    
    private Uri selectedImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verification);
        
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
        
        findViewById(R.id.toolbar).setOnClickListener(v -> finish());
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
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Matrix Card"), PICK_IMAGE_REQUEST);
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

        AppDataStore.submitVerification(this);
        HapticManager.success(this);
        Toast.makeText(this, "Verification submitted for review", Toast.LENGTH_LONG).show();
        render();
    }

    private void render() {
        String state = AppDataStore.verificationStatus(this);
        
        if ("approved".equals(state)) {
            ivStatusIcon.setImageResource(android.R.drawable.checkbox_on_background);
            ivStatusIcon.setColorFilter(getResources().getColor(R.color.pks_green));
            tvStatus.setText(R.string.verified_member);
            layoutForm.setVisibility(View.GONE);
            btnSimulate.setVisibility(View.GONE);
        } else if ("pending".equals(state)) {
            ivStatusIcon.setImageResource(android.R.drawable.ic_menu_recent_history);
            ivStatusIcon.setColorFilter(getResources().getColor(R.color.pks_blue));
            tvStatus.setText(R.string.review_in_progress);
            layoutForm.setVisibility(View.GONE);
            btnSimulate.setVisibility(View.VISIBLE);
        } else {
            ivStatusIcon.setImageResource(android.R.drawable.ic_lock_lock);
            ivStatusIcon.setColorFilter(getResources().getColor(R.color.airbnb_muted));
            tvStatus.setText(R.string.campus_verification);
            layoutForm.setVisibility(View.VISIBLE);
            btnSimulate.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            ivMatrixPreview.setImageURI(selectedImageUri);
            ivMatrixPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivMatrixPreview.setColorFilter(null);
        }
    }
}
