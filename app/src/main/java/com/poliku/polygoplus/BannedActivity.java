package com.poliku.polygoplus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.ui.BaseActivity;

/**
 * Full-screen, back-proof screen shown when an account is suspended by an
 * administrator (HTTP 401 whose body says "suspended", or a cold start with
 * the cached ban flag set). The only exits are the explicit actions on screen
 * - there is nothing the user can do to get around the ban client-side.
 */
public class BannedActivity extends BaseActivity {

    public static final String SUPPORT_EMAIL = "support@poliku.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_banned);

        // Android back / predictive back must not pop the banned screen or
        // let the user re-enter the app underneath it.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Swallowed deliberately: a suspended account has nowhere to go
                // back to inside the app.
            }
        });

        findViewById(R.id.btnBannedLogout).setOnClickListener(v -> {
            AppDataStore.markBanned(this);
            AppDataStore.logout(this);
            Intent intent = new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btnBannedSupport).setOnClickListener(v -> {
            Intent mail = new Intent(Intent.ACTION_SENDTO,
                    Uri.parse("mailto:" + SUPPORT_EMAIL));
            mail.putExtra(Intent.EXTRA_SUBJECT, "[PolyGo+] Account suspended");
            try {
                startActivity(mail);
            } catch (Exception ignored) {
                // No mail app installed - the on-screen support text covers it.
            }
        });
    }
}