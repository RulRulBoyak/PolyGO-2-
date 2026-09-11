package com.poliku.polygoplus;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.HapticManager;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class BugReportActivity extends AppCompatActivity {

    @Inject PolyGoRepository polyGoRepository;

    private TextInputLayout layoutDescription;
    private TextInputEditText etDescription;
    private TextView tvDeviceInfo;
    private ImageView ivScreenshotPreview;
    private MaterialButton btnSubmit;

    private Uri screenshotUri;

    private final androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest> pickScreenshot =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    screenshotUri = uri;
                    ivScreenshotPreview.setImageURI(uri);
                    ivScreenshotPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    ivScreenshotPreview.setColorFilter(null);
                    ivScreenshotPreview.setVisibility(android.view.View.VISIBLE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bug_report);

        layoutDescription = findViewById(R.id.layoutDescription);
        etDescription = findViewById(R.id.etBugDescription);
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo);
        ivScreenshotPreview = findViewById(R.id.ivScreenshotPreview);
        btnSubmit = findViewById(R.id.btnSubmitBug);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        tvDeviceInfo.setText(String.format("%s %s | v%s", Build.MODEL, Build.VERSION.RELEASE, BuildConfig.VERSION_NAME));

        findViewById(R.id.btnPickScreenshot).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            pickScreenshot.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        btnSubmit.setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            submitReport();
        });
    }

    private void submitReport() {
        String description = etDescription.getText().toString().trim();
        if (description.isEmpty()) {
            layoutDescription.setError(getString(R.string.bug_report_empty_desc));
            return;
        }
        layoutDescription.setError(null);
        btnSubmit.setEnabled(false);

        if (screenshotUri != null) {
            polyGoRepository.uploadImage(this, screenshotUri, new Callback<PolyGoApi.UploadResponse>() {
                @Override
                public void onResponse(Call<PolyGoApi.UploadResponse> call, Response<PolyGoApi.UploadResponse> response) {
                    String url = "";
                    if (response.isSuccessful() && response.body() != null && response.body().url != null) {
                        url = response.body().url;
                    }
                    sendBugReport(description, url);
                }

                @Override
                public void onFailure(Call<PolyGoApi.UploadResponse> call, Throwable t) {
                    sendBugReport(description, "");
                }
            });
        } else {
            sendBugReport(description, "");
        }
    }

    private void sendBugReport(String description, String screenshotUrl) {
        String screenName = getClass().getSimpleName();
        polyGoRepository.reportBug(
                description,
                Build.MODEL,
                Build.VERSION.RELEASE,
                BuildConfig.VERSION_NAME,
                screenName,
                screenshotUrl,
                new Callback<BaseResponse>() {
                    @Override
                    public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                        boolean ok = response.isSuccessful() && response.body() != null && response.body().isSuccess();
                        btnSubmit.setEnabled(true);
                        HapticManager.success(BugReportActivity.this);
                        Toast.makeText(BugReportActivity.this,
                                ok ? R.string.bug_report_success : R.string.bug_report_failed,
                                Toast.LENGTH_SHORT).show();
                        if (ok) {
                            finish();
                        }
                    }

                    @Override
                    public void onFailure(Call<BaseResponse> call, Throwable t) {
                        btnSubmit.setEnabled(true);
                        Toast.makeText(BugReportActivity.this, R.string.bug_report_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}