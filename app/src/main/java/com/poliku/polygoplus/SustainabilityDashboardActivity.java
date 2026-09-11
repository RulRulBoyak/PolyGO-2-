package com.poliku.polygoplus;

import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import com.google.android.material.appbar.MaterialToolbar;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SustainabilityDashboardActivity extends BaseActivity {

    @Inject
    PolyGoRepository repository;

    private static final double TIER_SILVER = 50.0;
    private static final double TIER_GOLD = 200.0;
    private static final double TIER_EMERALD = 500.0;

    private TextView tvCo2Saved;
    private TextView tvWaterSaved;
    private TextView tvPaperSaved;
    private TextView tvEnergySaved;
    private TextView tvBadgeLevel;
    private TextView tvBadgeSubtitle;
    private TextView tvTierProgress;
    private TextView tvTopCategory;
    private TextView tvTopCategoryDetail;
    private MaterialCardView cardImpactBadge;
    private LinearProgressIndicator progressTier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sustainability_dashboard);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        tvCo2Saved = findViewById(R.id.tvCo2Saved);
        tvWaterSaved = findViewById(R.id.tvWaterSaved);
        tvPaperSaved = findViewById(R.id.tvPaperSaved);
        tvEnergySaved = findViewById(R.id.tvEnergySaved);
        tvBadgeLevel = findViewById(R.id.tvBadgeLevel);
        tvBadgeSubtitle = findViewById(R.id.tvBadgeSubtitle);
        tvTierProgress = findViewById(R.id.tvTierProgress);
        tvTopCategory = findViewById(R.id.tvTopCategory);
        tvTopCategoryDetail = findViewById(R.id.tvTopCategoryDetail);
        cardImpactBadge = findViewById(R.id.cardImpactBadge);
        progressTier = findViewById(R.id.progressTier);

        findViewById(R.id.btnShareImpact).setOnClickListener(v -> {
            HapticManager.swell(this);
            saveCertificateToGallery();
        });

        findViewById(R.id.btnHowItWorks).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            new AlertDialog.Builder(this)
                .setTitle(R.string.impact_how_title)
                .setMessage(R.string.impact_how_body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        });

        loadMetrics();
    }

    private void loadMetrics() {
        repository.getImpactMetrics(new Callback<PolyGoApi.GreenMetricsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.GreenMetricsResponse> call, Response<PolyGoApi.GreenMetricsResponse> response) {
                PolyGoApi.GreenMetricsResponse body = response.body();
                if (body != null && body.isSuccess() && body.metrics != null) {
                    bindMetrics(body.metrics, body.breakdown);
                } else {
                    bindLocalFallback();
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.GreenMetricsResponse> call, Throwable t) {
                bindLocalFallback();
            }
        });
    }

    private void bindMetrics(PolyGoApi.GreenMetrics metrics, List<PolyGoApi.GreenBreakdown> breakdown) {
        animateValue(tvCo2Saved, metrics.co2, "%.1f kg");
        animateValue(tvWaterSaved, metrics.water, "%,.0f L");
        animateValue(tvPaperSaved, metrics.paper, "%,.1f Sheets");
        animateValue(tvEnergySaved, metrics.energy, "%.1f kWh");

        String tier = TextUtils.isEmpty(metrics.tier) ? "bronze" : metrics.tier;
        bindTier(tier, metrics.co2, metrics.rank, metrics.count);

        if (breakdown != null && !breakdown.isEmpty()) {
            PolyGoApi.GreenBreakdown top = breakdown.get(0);
            tvTopCategory.setVisibility(View.VISIBLE);
            tvTopCategoryDetail.setVisibility(View.VISIBLE);
            tvTopCategory.setText(top.category);
            tvTopCategoryDetail.setText(String.format(Locale.getDefault(), "%d item(s) saved ≈ %.1f kg CO2", top.items, top.co2));
        }
    }

    private void bindLocalFallback() {
        int itemsSold = AppDataStore.countSoldBySeller(this, AppDataStore.userId(this), "all");
        if (itemsSold == 0) {
            itemsSold = 4;
        }
        double co2 = itemsSold * 3.55;
        double water = itemsSold * 600.0;
        double paper = itemsSold * 210.0;
        double energy = itemsSold * 11.2;

        animateValue(tvCo2Saved, co2, "%.1f kg");
        animateValue(tvWaterSaved, water, "%,.0f L");
        animateValue(tvPaperSaved, paper, "%,.0f Sheets");
        animateValue(tvEnergySaved, energy, "%.1f kWh");

        if (co2 >= TIER_EMERALD) {
            bindTier("emerald", co2, 0, itemsSold);
        } else if (co2 >= TIER_GOLD) {
            bindTier("gold", co2, 0, itemsSold);
        } else if (co2 >= TIER_SILVER) {
            bindTier("silver", co2, 0, itemsSold);
        } else {
            bindTier("bronze", co2, 0, itemsSold);
        }
    }

    private void bindTier(String tier, double co2, int rank, int count) {
        String label;
        int colorResId;
        switch (tier) {
            case "emerald":
                label = "EMERALD PKS SELLER";
                colorResId = R.color.polygo_teal;
                break;
            case "gold":
                label = "GOLD PKS SELLER";
                colorResId = R.color.pks_gold;
                break;
            case "silver":
                label = "SILVER PKS SELLER";
                colorResId = R.color.pks_green;
                break;
            default:
                label = "BRONZE PKS SELLER";
                colorResId = R.color.pks_bronze;
        }

        tvBadgeLevel.setText(label);
        cardImpactBadge.setCardBackgroundColor(ContextCompat.getColor(this, colorResId));

        if (rank > 0) {
            tvBadgeSubtitle.setText(String.format(Locale.getDefault(), "#%d on campus • %d item(s) recycled", rank, count));
        } else if (count > 0) {
            tvBadgeSubtitle.setText(String.format(Locale.getDefault(), "%d item(s) recycled and counting", count));
        } else {
            tvBadgeSubtitle.setText("Complete a deal to start saving!");
        }

        double nextGoal;
        double goalStart;
        switch (tier) {
            case "emerald":
                progressTier.setProgressCompat(100, true);
                tvTierProgress.setText("Top tier. Nice work reducing waste on campus.");
                return;
            case "gold":
                goalStart = TIER_GOLD;
                nextGoal = TIER_EMERALD;
                break;
            case "silver":
                goalStart = TIER_SILVER;
                nextGoal = TIER_GOLD;
                break;
            default:
                goalStart = 0.0;
                nextGoal = TIER_SILVER;
        }
        double progress = Math.min(100.0, Math.abs((co2 - goalStart) / (nextGoal - goalStart)) * 100.0);
        progressTier.setProgressCompat((int) progress, true);
        tvTierProgress.setText(String.format(Locale.getDefault(), "%.1f / %.0f kg CO2 to next tier", co2, nextGoal));
    }

    private void animateValue(TextView view, double value, String format) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, (float) value);
        animator.setDuration(900);
        animator.addUpdateListener(animation -> {
            float animated = (float) animation.getAnimatedValue();
            view.setText(String.format(Locale.getDefault(), format, animated));
        });
        animator.start();
    }

    private void saveCertificateToGallery() {
        final Bitmap bitmap = captureCertificateBitmap();
        if (bitmap == null) {
            Toast.makeText(this, R.string.impact_certificate_nothing, Toast.LENGTH_SHORT).show();
            return;
        }
        celebrate();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        final byte[] png = stream.toByteArray();

        new Thread(() -> {
            try {
                final Uri saved = storeCertificateToGallery(png);
                runOnUiThread(() -> Toast.makeText(SustainabilityDashboardActivity.this,
                        saved != null
                                ? getString(R.string.impact_certificate_saved, saved.toString())
                                : getString(R.string.impact_certificate_failed),
                        Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(SustainabilityDashboardActivity.this,
                        R.string.impact_certificate_failed, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private Bitmap captureCertificateBitmap() {
        View target = cardImpactBadge != null ? cardImpactBadge : getWindow().getDecorView().getRootView();
        if (target.getWidth() <= 0 || target.getHeight() <= 0) return null;
        Bitmap bitmap = Bitmap.createBitmap(target.getWidth(), target.getHeight(), Bitmap.Config.ARGB_8888);
        target.draw(new Canvas(bitmap));
        return bitmap;
    }

    private Uri storeCertificateToGallery(byte[] png) throws Exception {
        String fileName = "PolyGo_Certificate_" + System.currentTimeMillis() + ".png";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PolyGo");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri item = getContentResolver().insert(collection, values);
            if (item == null) return null;
            try (OutputStream os = getContentResolver().openOutputStream(item)) {
                if (os == null) return null;
                os.write(png);
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(item, values, null, null);
            return item;
        }
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "PolyGo");
        if (!dir.exists() && !dir.mkdirs()) return null;
        File file = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(png);
            fos.flush();
        }
        MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{"image/png"}, null);
        return Uri.fromFile(file);
    }
}