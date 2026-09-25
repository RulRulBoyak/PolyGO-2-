package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.TextView;

import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;

import com.poliku.polygoplus.network.ConnectivityHelper;
import com.poliku.polygoplus.network.NetworkErrorHandler;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ErrorStateActivity extends BaseActivity {
    @Inject PolyGoRepository polyGoRepository;
    public static final String EXTRA_MODE = "mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_error_state);
        boolean maintenance = "maintenance".equals(getIntent().getStringExtra(EXTRA_MODE));
        ((TextView) findViewById(R.id.tvErrorEmoji)).setText(maintenance ? "🛠️" : "📡");
        ((TextView) findViewById(R.id.tvErrorTitle)).setText(maintenance
                ? R.string.error_state_maintenance_title : R.string.error_state_offline_title);
        ((TextView) findViewById(R.id.tvErrorBody)).setText(maintenance
                ? getString(R.string.error_state_maintenance_body)
                : getString(R.string.error_state_offline_body));
        findViewById(R.id.btnRetry).setOnClickListener(v -> retry());
        findViewById(R.id.btnContinueOffline).setOnClickListener(v -> finish());
    }

    private void retry() {
        if (!ConnectivityHelper.isOnline(this)) {
            ((TextView) findViewById(R.id.tvErrorTitle)).setText(R.string.error_state_still_offline);
            return;
        }
        polyGoRepository.getStatus(new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().isMaintenance()) {
                        // The kill switch is still up — stay locked and show the
                        // admin's message verbatim (falls back to default copy).
                        String msg = response.body().getMaintenanceMessage();
                        AppDataStore.setMaintenanceMode(ErrorStateActivity.this, true);
                        ((TextView) findViewById(R.id.tvErrorTitle)).setText(R.string.error_state_still_maintenance);
                        ((TextView) findViewById(R.id.tvErrorBody)).setText(
                                msg != null && !msg.trim().isEmpty()
                                        ? msg
                                        : getString(R.string.error_state_maintenance_body));
                        return;
                    }
                    AppDataStore.setMaintenanceMode(ErrorStateActivity.this, false);
                    NetworkErrorHandler.notifyServerOk();
                    finish();
                } else {
                    showServerUnavailable();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                showServerUnavailable();
            }
        });
    }

    private void showServerUnavailable() {
        ((TextView) findViewById(R.id.tvErrorTitle)).setText(R.string.error_state_server_title);
        ((TextView) findViewById(R.id.tvErrorBody)).setText(R.string.error_state_server_body);
    }
}
