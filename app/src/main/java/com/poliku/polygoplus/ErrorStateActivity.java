package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.network.ConnectivityHelper;
import com.poliku.polygoplus.network.NetworkErrorHandler;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ErrorStateActivity extends AppCompatActivity {
    @Inject PolyGoRepository polyGoRepository;
    public static final String EXTRA_MODE = "mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_error_state);
        boolean maintenance = "maintenance".equals(getIntent().getStringExtra(EXTRA_MODE));
        ((TextView) findViewById(R.id.tvErrorEmoji)).setText(maintenance ? "🛠️" : "📡");
        ((TextView) findViewById(R.id.tvErrorTitle)).setText(maintenance ? "We'll be back soon" : "No internet connection");
        ((TextView) findViewById(R.id.tvErrorBody)).setText(maintenance
                ? "PolyGo+ is in maintenance while the campus database is updated. Please try again shortly."
                : "Check your internet connection and try again. You can also continue with listings saved on this phone.");
        findViewById(R.id.btnRetry).setOnClickListener(v -> retry());
        findViewById(R.id.btnContinueOffline).setOnClickListener(v -> finish());
    }

    private void retry() {
        if (!ConnectivityHelper.isOnline(this)) {
            ((TextView) findViewById(R.id.tvErrorTitle)).setText("Still offline");
            return;
        }
        polyGoRepository.getStatus(new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Assuming maintenance mode is handled globally or we check a custom field
                    // Since BaseResponse doesn't have it, let's assume if it returns successfully, we can finish
                    NetworkErrorHandler.notifyServerOk();
                    finish();
                } else {
                    ((TextView) findViewById(R.id.tvErrorTitle)).setText("Server unreachable");
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                ((TextView) findViewById(R.id.tvErrorTitle)).setText("Server unreachable");
                ((TextView) findViewById(R.id.tvErrorBody)).setText(t.getMessage());
            }
        });
    }
}
