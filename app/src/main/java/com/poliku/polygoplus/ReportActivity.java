package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ReportActivity extends AppCompatActivity {
    @Inject PolyGoRepository polyGoRepository;
    public static final String EXTRA_TARGET_TYPE = "target_type";
    public static final String EXTRA_TARGET_ID = "target_id";
    public static final String EXTRA_TARGET_NAME = "target_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        String type = getIntent().getStringExtra(EXTRA_TARGET_TYPE);
        String id = getIntent().getStringExtra(EXTRA_TARGET_ID);
        String name = getIntent().getStringExtra(EXTRA_TARGET_NAME);
        boolean listing = !"user".equals(type);
        ((TextView) findViewById(R.id.tvReportTitle)).setText(listing ? "Report this listing" : "Report this user");
        ((TextView) findViewById(R.id.tvReportTarget)).setText("Help keep PKS safe. Reporting: " + (name == null ? "this account" : name));

        Spinner spinner = findViewById(R.id.spinnerReason);
        String[] reasons = listing
                ? new String[]{"Prohibited item", "Scam or misleading", "Wrong category", "Offensive content", "Other"}
                : new String[]{"Scam behaviour", "Harassment", "Impersonation", "Spam", "Other"};
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, reasons));

        findViewById(R.id.btnSubmitReport).setOnClickListener(v -> {
            String reason = spinner.getSelectedItem().toString();
            String details = ((EditText) findViewById(R.id.etReportDetails)).getText().toString().trim();
            AppDataStore.addReport(this, listing ? "listing" : "user", id == null ? "" : id, name, reason, details);
            polyGoRepository.submitReport(AppDataStore.userId(this), listing ? "listing" : "user", id, reason, details, new Callback<BaseResponse>() {
                @Override public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) { }
                @Override public void onFailure(Call<BaseResponse> call, Throwable t) { }
            });
            Toast.makeText(this, "Report submitted. Thank you.", Toast.LENGTH_LONG).show();
            finish();
        });
    }
}
