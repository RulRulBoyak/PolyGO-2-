package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.ExitGuard;
import com.poliku.polygoplus.ui.UiUtils;

import androidx.activity.OnBackPressedCallback;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ReportActivity extends BaseActivity {
    @Inject PolyGoRepository polyGoRepository;
    public static final String EXTRA_TARGET_TYPE = "target_type";
    public static final String EXTRA_TARGET_ID = "target_id";
    public static final String EXTRA_TARGET_NAME = "target_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);
        findViewById(R.id.btnBack).setOnClickListener(v -> confirmExit());

        String type = getIntent().getStringExtra(EXTRA_TARGET_TYPE);
        String id = getIntent().getStringExtra(EXTRA_TARGET_ID);
        String name = getIntent().getStringExtra(EXTRA_TARGET_NAME);
        boolean listing = !"user".equals(type);
        ((TextView) findViewById(R.id.tvReportTitle)).setText(listing ? getString(R.string.report_title_listing) : getString(R.string.report_title_user));
        ((TextView) findViewById(R.id.tvReportTarget)).setText(getString(R.string.report_target_label, name == null ? getString(R.string.report_this_account) : name));

        ChipGroup chipGroupReason = findViewById(R.id.chipGroupReason);
        final String[] reasons = listing
                ? new String[]{"Prohibited item", "Scam or misleading", "Wrong category", "Offensive content", "Other"}
                : new String[]{"Scam behaviour", "Harassment", "Impersonation", "Spam", "Other"};
        for (String reason : reasons) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_category_chip, chipGroupReason, false);
            chip.setId(View.generateViewId());
            chip.setText(reason);
            chipGroupReason.addView(chip);
        }
        ((Chip) chipGroupReason.getChildAt(0)).setChecked(true);

        findViewById(R.id.btnSubmitReport).setOnClickListener(v -> {
            int checkedId = chipGroupReason.getCheckedChipId();
            Chip checked = checkedId == View.NO_ID ? null : chipGroupReason.findViewById(checkedId);
            String reason = checked == null ? reasons[0] : checked.getText().toString();
            String details = ((EditText) findViewById(R.id.etReportDetails)).getText().toString().trim();
            AppDataStore.addReport(this, listing ? "listing" : "user", id == null ? "" : id, name, reason, details);
            polyGoRepository.submitReport(AppDataStore.userId(this), listing ? "listing" : "user", id, reason, details, new Callback<BaseResponse>() {
                @Override public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) { }
                @Override public void onFailure(Call<BaseResponse> call, Throwable t) { }
            });
            UiUtils.snackbar(findViewById(android.R.id.content), R.string.toast_report_submitted);
            finish();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });
    }

    private void confirmExit() {
        if (!ExitGuard.anyText(((EditText) findViewById(R.id.etReportDetails)).getText())) {
            finish();
            return;
        }
        ExitGuard.show(this, this::finish);
    }
}
