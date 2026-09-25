package com.poliku.polygoplus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.network.AiHelper;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class AiDiscoveryActivity extends BaseActivity {

    @Inject PolyGoRepository polyGoRepository;
    private ImageView ivPreview;
    private TextView tvLabel;
    private LinearProgressIndicator progress;
    private ProductCardAdapter adapter;
    private List<ListingEntity> results = new ArrayList<>();

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    processImage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_discovery);

        ivPreview = findViewById(R.id.ivSearchPreview);
        tvLabel = findViewById(R.id.tvSearchLabel);
        progress = findViewById(R.id.aiProgress);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvAiResults);
        adapter = new ProductCardAdapter(results, (a, p, v) -> {
            Intent i = new Intent(this, ProductDetailActivity.class);
            i.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, p.id);
            startActivity(i);
        });
        rv.setAdapter(adapter);

        findViewById(R.id.btnCaptureSearch).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            pickImage.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });
    }

    private void processImage(Uri uri) {
        ivPreview.setImageURI(uri);
        ivPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        tvLabel.setText(R.string.ai_analyzing);
        progress.setVisibility(View.VISIBLE);
        HapticManager.swell(this);

        AiHelper.suggestListingDetails(polyGoRepository, this, uri, new AiHelper.AiCallback() {
            @Override
            public void onResult(String title, String price, String description, String category,
                                 List<String> tags, String condition, double confidence) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                tvLabel.setText(getString(R.string.ai_suggestion_ready, title));
                HapticManager.success(AiDiscoveryActivity.this);
                matchListings(title);
                showSuggestion(title, price, description, category, tags, condition);
            }

            @Override
            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                tvLabel.setText(R.string.ai_idle_hint);
                Toast.makeText(AiDiscoveryActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSuggestion(String title, String price, String description, String category,
                                List<String> tags, String condition) {
        String tagText = tags == null ? "" : android.text.TextUtils.join(", ", tags);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ai_suggestion_title)
                .setMessage(getString(R.string.ai_suggestion_body_full,
                        title, price, category, condition, tagText, description))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ai_suggestion_create, (dialog, which) -> {
                    Intent create = new Intent(this, EditProductActivity.class);
                    create.putExtra(EditProductActivity.EXTRA_PREFILL_TITLE, title);
                    create.putExtra(EditProductActivity.EXTRA_PREFILL_PRICE, price);
                    create.putExtra(EditProductActivity.EXTRA_PREFILL_DESCRIPTION, description);
                    create.putExtra(EditProductActivity.EXTRA_PREFILL_CATEGORY, category);
                    create.putExtra(EditProductActivity.EXTRA_PREFILL_TAGS, tagText);
                    create.putExtra(EditProductActivity.EXTRA_PREFILL_CONDITION, condition);
                    startActivity(create);
                })
                .show();
    }

    private void matchListings(String keyword) {
        results.clear();
        String needle = keyword.toLowerCase(Locale.ROOT);
        List<ListingEntity> all = AppDataStore.listingsToEntities(AppDataStore.getActiveListings(this));
        for (ListingEntity p : all) {
            if (p.title != null && p.title.toLowerCase(Locale.ROOT).contains(needle)) {
                results.add(p);
            }
        }
        adapter.updateData(results);
    }
}
