package com.poliku.polygoplus;

import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class LegalActivity extends AppCompatActivity {
    public static final String EXTRA_PAGE = "page";

    private String page;
    private boolean bm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_legal);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        page = getIntent().getStringExtra(EXTRA_PAGE);
        if (page == null) page = "privacy";
        bm = Locale.getDefault().getLanguage().startsWith("ms");
        findViewById(R.id.btnLangToggle).setOnClickListener(v -> {
            bm = !bm;
            render();
        });
        render();
    }

    private void render() {
        TextView title = findViewById(R.id.tvLegalTitle);
        boolean ms = bm;
        switch (page) {
            case "terms":
                title.setText(getString(ms ? R.string.terms_title_ms : R.string.terms_title));
                break;
            case "data_safety":
                title.setText(getString(ms ? R.string.data_safety_title_ms : R.string.data_safety_title));
                break;
            default:
                title.setText(getString(ms ? R.string.privacy_title_ms : R.string.privacy_title));
                break;
        }
        ((TextView) findViewById(R.id.btnLangToggle)).setText(getString(ms ? R.string.lang_toggle_to_en : R.string.lang_toggle_to_bm));

        int bodyRes;
        switch (page) {
            case "terms":
                bodyRes = ms ? R.string.terms_of_service_text_ms : R.string.terms_of_service_text;
                break;
            case "data_safety":
                bodyRes = ms ? R.string.data_safety_text_ms : R.string.data_safety_text;
                break;
            default:
                bodyRes = ms ? R.string.privacy_policy_text_ms : R.string.privacy_policy_text;
                break;
        }
        ((TextView) findViewById(R.id.tvLegalBody)).setText(Html.fromHtml(getString(bodyRes), Html.FROM_HTML_MODE_LEGACY));
    }
}
