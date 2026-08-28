package com.poliku.polygoplus;

import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class LegalActivity extends AppCompatActivity {
    public static final String EXTRA_PAGE = "page";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_legal);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        boolean terms = "terms".equals(getIntent().getStringExtra(EXTRA_PAGE));
        ((TextView) findViewById(R.id.tvLegalTitle)).setText(terms ? "Terms of Service" : "Privacy Policy");
        int res = terms ? R.string.terms_of_service_text : R.string.privacy_policy_text;
        ((TextView) findViewById(R.id.tvLegalBody)).setText(Html.fromHtml(getString(res), Html.FROM_HTML_MODE_LEGACY));
    }
}
