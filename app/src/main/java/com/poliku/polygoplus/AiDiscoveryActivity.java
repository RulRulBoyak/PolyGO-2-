package com.poliku.polygoplus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.network.AiHelper;
import com.poliku.polygoplus.ui.HapticManager;

import java.util.ArrayList;
import java.util.List;

public class AiDiscoveryActivity extends AppCompatActivity {

    private ImageView ivPreview;
    private TextView tvLabel;
    private LinearProgressIndicator progress;
    private ProductCardAdapter adapter;
    private List<AppDataStore.ProductRecord> results = new ArrayList<>();

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
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
        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvAiResults);
        adapter = new ProductCardAdapter(results, (a, p, v) -> {
            Intent i = new Intent(this, ProductDetailActivity.class);
            i.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, p.id);
            startActivity(i);
        });
        rv.setAdapter(adapter);

        findViewById(R.id.btnCaptureSearch).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            pickImage.launch("image/*");
        });
    }

    private void processImage(Uri uri) {
        ivPreview.setImageURI(uri);
        ivPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        tvLabel.setText("Analyzing image...");
        progress.setVisibility(View.VISIBLE);
        HapticManager.swell(this);

        AiHelper.suggestListingDetails(this, uri, new AiHelper.Callback() {
            @Override
            public void onResult(String title, String price, String description) {
                progress.setVisibility(View.GONE);
                tvLabel.setText("Found items similar to: " + title);
                HapticManager.success(AiDiscoveryActivity.this);
                
                // Simulate finding items based on AI keywords
                mockResults(title);
            }

            @Override
            public void onError(String error) {
                progress.setVisibility(View.GONE);
                tvLabel.setText("Snap a photo to find items");
                Toast.makeText(AiDiscoveryActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void mockResults(String keyword) {
        results.clear();
        List<AppDataStore.ProductRecord> all = AppDataStore.getListings(this);
        for (AppDataStore.ProductRecord p : all) {
            if (p.title.toLowerCase().contains(keyword.toLowerCase()) || results.size() < 4) {
                results.add(p);
            }
        }
        adapter.updateData(results);
    }
}
