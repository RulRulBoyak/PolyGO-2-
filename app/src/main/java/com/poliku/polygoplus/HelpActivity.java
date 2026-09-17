package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;

import com.poliku.polygoplus.ui.BaseActivity;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class HelpActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_help);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnDataSafety).setOnClickListener(v -> {
            Intent i = new Intent(this, LegalActivity.class);
            i.putExtra(LegalActivity.EXTRA_PAGE, "data_safety");
            startActivity(i);
        });
        findViewById(R.id.btnReportBug).setOnClickListener(v ->
                startActivity(new Intent(this, BugReportActivity.class)));
    }
}
