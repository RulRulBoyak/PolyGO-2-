package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.ConnectivityHelper;
import com.poliku.polygoplus.network.NetworkApi;

public class ErrorStateActivity extends AppCompatActivity {
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
                : "Wi-Fi looks offline or the XAMPP server is down. Turn on internet, start Apache and MySQL, then retry. You can also continue with listings saved on this phone.");
        findViewById(R.id.btnRetry).setOnClickListener(v -> retry());
        findViewById(R.id.btnContinueOffline).setOnClickListener(v -> finish());
    }

    private void retry() {
        if (!ConnectivityHelper.isOnline(this)) {
            ((TextView) findViewById(R.id.tvErrorTitle)).setText("Still offline");
            return;
        }
        NetworkApi.getStatus(new NetworkApi.Callback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                boolean maintenance = response.optBoolean("maintenance", false);
                AppDataStore.setMaintenanceMode(ErrorStateActivity.this, maintenance);
                if (maintenance) {
                    ((TextView) findViewById(R.id.tvErrorTitle)).setText("We'll be back soon");
                    ((TextView) findViewById(R.id.tvErrorBody)).setText("Maintenance is still on.");
                    return;
                }
                finish();
            }

            @Override
            public void onError(String message) {
                ((TextView) findViewById(R.id.tvErrorTitle)).setText("Server unreachable");
                ((TextView) findViewById(R.id.tvErrorBody)).setText(message);
            }
        });
    }
}
