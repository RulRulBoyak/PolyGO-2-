package com.poliku.polygoplus;

import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Html;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.text.method.LinkMovementMethod;
import android.view.ViewGroup;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.poliku.polygoplus.ui.BaseActivity;
import dagger.hilt.android.AndroidEntryPoint;

import java.util.Locale;

@AndroidEntryPoint
public class LegalActivity extends BaseActivity {
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
        renderDocument(getString(bodyRes));
    }

    private void renderDocument(String source) {
        LinearLayout content = findViewById(R.id.legalContent);
        content.removeAllViews();

        String normalized = source
                .replaceAll("(?i)<br\\s*/?>\\s*<br\\s*/?>", "\n\n")
                .replaceAll("(?i)<br\\s*/?>", "\n");
        String[] blocks = normalized.trim().split("\\n\\s*\\n");
        boolean hasDocumentHeader = blocks.length > 1 &&
                (plainText(blocks[1]).toLowerCase(Locale.ROOT).startsWith("last updated") ||
                 plainText(blocks[1]).toLowerCase(Locale.ROOT).startsWith("dikemas kini"));
        int firstContent = hasDocumentHeader ? 2 : 0;
        TextView updated = findViewById(R.id.tvLegalUpdated);
        updated.setVisibility(hasDocumentHeader ? View.VISIBLE : View.GONE);
        if (hasDocumentHeader) updated.setText(plainText(blocks[1]));

        for (int i = firstContent; i < blocks.length; i++) {
            String block = blocks[i].trim();
            if (block.isEmpty()) continue;
            addSection(content, block, i == firstContent);
        }
    }

    private void addSection(LinearLayout parent, String html, boolean introduction) {
        int surface = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurface, getColor(R.color.white));
        int surfaceVariant = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceVariant, getColor(R.color.surface_variant_light));
        int onSurface = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurface, getColor(R.color.airbnb_ink));
        int primary = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorPrimary, getColor(R.color.pks_blue));

        MaterialCardView card = new MaterialCardView(this);
        card.setCardElevation(0f);
        card.setRadius(dp(18));
        card.setStrokeWidth(introduction ? 0 : dp(1));
        card.setStrokeColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOutlineVariant, getColor(R.color.grey_200)));
        card.setCardBackgroundColor(introduction ? surfaceVariant : surface);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);

        TextView text = new TextView(this);
        text.setPadding(dp(18), dp(16), dp(18), dp(16));
        text.setTextColor(onSurface);
        text.setTextSize(introduction ? 15f : 14.5f);
        text.setLineSpacing(dp(3), 1.12f);
        text.setLinkTextColor(primary);

        String withBullets = html.replaceAll("(?m)^\\s*-\\s+", "•  ");
        SpannableStringBuilder styled = new SpannableStringBuilder(
                Html.fromHtml(withBullets.replace("\n", "<br>"), Html.FROM_HTML_MODE_LEGACY));
        StyleSpan[] boldSpans = styled.getSpans(0, styled.length(), StyleSpan.class);
        for (StyleSpan span : boldSpans) {
            int start = styled.getSpanStart(span);
            int end = styled.getSpanEnd(span);
            styled.setSpan(new RelativeSizeSpan(1.12f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new ForegroundColorSpan(onSurface), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        text.setText(styled);
        Linkify.addLinks(text, Linkify.EMAIL_ADDRESSES | Linkify.WEB_URLS);
        text.setMovementMethod(LinkMovementMethod.getInstance());
        card.addView(text);
        parent.addView(card);
    }

    private String plainText(String html) {
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
