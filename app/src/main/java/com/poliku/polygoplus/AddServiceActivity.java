package com.poliku.polygoplus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.lifecycle.ViewModelProvider;
import com.poliku.polygoplus.viewmodel.AddServiceViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.PhotoPreviewAdapter;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AddServiceActivity extends AppCompatActivity {

    private AddServiceViewModel viewModel;
    private TextInputLayout tilCustomCategory;
    private TextInputEditText etCustomCategory, etTitle, etPrice, etAvailability, etDescription, etTime;
    private AutoCompleteTextView autoCategory;
    private ChipGroup chipGroupPriceType, chipGroupFulfillment;
    private PhotoPreviewAdapter photoAdapter;
    private final List<android.net.Uri> selectedUris = new ArrayList<>();

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMedia =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(5), uris -> {
                if (!uris.isEmpty()) {
                    for (android.net.Uri uri : uris) {
                        if (!selectedUris.contains(uri)) selectedUris.add(uri);
                        try {
                            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) {}
                    }
                    photoAdapter.updateData(selectedUris);
                    updateViewModelUris();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_service);
        viewModel = new ViewModelProvider(this).get(AddServiceViewModel.class);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topBar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        setupUI();
        setupCategory();
        
        // Rule 3.3: Observe and restore state
        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.imageUri.observe(this, uriString -> {
            if (uriString != null && !uriString.isEmpty()) {
                String[] parts = uriString.split("\\|");
                if (selectedUris.isEmpty()) {
                    for (String p : parts) selectedUris.add(android.net.Uri.parse(p));
                    photoAdapter.updateData(selectedUris);
                }
            }
        });
        
        // Restore non-observed simple strings
        etTitle.setText(viewModel.getTitle());
        autoCategory.setText(viewModel.getCategory(), false);
        etPrice.setText(viewModel.getPrice());
        etAvailability.setText(viewModel.getAvailability());
        etTime.setText(viewModel.getDeliveryTime());
        etDescription.setText(viewModel.getDescription());
    }

    private void updateViewModelUris() {
        java.util.StringJoiner joiner = new java.util.StringJoiner("|");
        for (android.net.Uri u : selectedUris) joiner.add(u.toString());
        viewModel.setImageUri(joiner.toString());
    }

    @Override
    protected void onPause() {
        super.onPause();
        viewModel.setTitle(etTitle.getText().toString());
        viewModel.setCategory(autoCategory.getText().toString());
        viewModel.setPrice(etPrice.getText().toString());
        viewModel.setAvailability(etAvailability.getText().toString());
        viewModel.setDeliveryTime(etTime.getText().toString());
        viewModel.setDescription(etDescription.getText().toString());
    }

    private void setupUI() {
        findViewById(R.id.toolbar).setOnClickListener(v -> finish());
        
        etTitle = findViewById(R.id.etServiceTitle);
        etPrice = findViewById(R.id.etServicePrice);
        etTime = findViewById(R.id.etServiceTime);
        etAvailability = findViewById(R.id.etServiceAvailability);
        etDescription = findViewById(R.id.etServiceDescription);
        autoCategory = findViewById(R.id.autoCompleteServiceCategory);
        tilCustomCategory = findViewById(R.id.tilCustomServiceCategory);
        etCustomCategory = findViewById(R.id.etCustomServiceCategory);
        chipGroupPriceType = findViewById(R.id.chipGroupPriceType);
        chipGroupFulfillment = findViewById(R.id.chipGroupFulfillment);

        photoAdapter = new PhotoPreviewAdapter(position -> {
            selectedUris.remove(position);
            photoAdapter.updateData(selectedUris);
            updateViewModelUris();
        });
        androidx.recyclerview.widget.RecyclerView rv = findViewById(R.id.rvPortfolioPreviews);
        rv.setAdapter(photoAdapter);

        findViewById(R.id.btnAddPortfolio).setOnClickListener(v -> {
            pickMultipleMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        findViewById(R.id.btnAiSuggest).setOnClickListener(v -> {
            if (selectedUris.isEmpty()) {
                Toast.makeText(this, "Add a portfolio photo first", Toast.LENGTH_SHORT).show();
                return;
            }
            HapticManager.swell(this);
            v.setEnabled(false);
            ((com.google.android.material.button.MaterialButton) v).setText("✨ AI is analyzing...");

            com.poliku.polygoplus.network.AiHelper.suggestListingDetails(this, selectedUris.get(0), new com.poliku.polygoplus.network.AiHelper.Callback() {
                @Override
                public void onResult(String title, String price, String description) {
                    etTitle.setText(title);
                    etPrice.setText(price);
                    etDescription.setText(description);
                    v.setEnabled(true);
                    ((com.google.android.material.button.MaterialButton) v).setText("✨ AI: Suggest Service Details");
                    HapticManager.success(AddServiceActivity.this);
                }

                @Override
                public void onError(String error) {
                    v.setEnabled(true);
                    ((com.google.android.material.button.MaterialButton) v).setText("✨ AI: Suggest Service Details");
                    Toast.makeText(AddServiceActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        });

        findViewById(R.id.btnPublishService).setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            publishService();
        });
        
        findViewById(R.id.btnSaveServiceDraft).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            AppDataStore.saveDraft(this, etTitle.getText().toString(), 
                    autoCategory.getText().toString(), etPrice.getText().toString(), 
                    etDescription.getText().toString(), viewModel.imageUri.getValue(), "Campus Wide");
            Toast.makeText(this, "Service draft saved", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void setupCategory() {
        String[] categories = {"Repair", "Printing", "Delivery", "Cleaning", "Lessons", "Laundry", "Others"};
        autoCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categories));
        autoCategory.setOnItemClickListener((parent, view, position, id) -> {
            if ("Others".equals(categories[position])) {
                tilCustomCategory.setVisibility(View.VISIBLE);
            } else {
                tilCustomCategory.setVisibility(View.GONE);
                etCustomCategory.setText("");
            }
        });
    }

    private void publishService() {
        String title = etTitle.getText().toString().trim();
        String price = etPrice.getText().toString().trim();
        String time = etTime.getText().toString().trim();
        String availability = etAvailability.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String category = autoCategory.getText().toString();

        if ("Others".equals(category)) {
            category = etCustomCategory.getText().toString().trim();
        }

        if (title.isEmpty() || category.isEmpty() || price.isEmpty() || description.isEmpty() || selectedUris.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields including photos", Toast.LENGTH_SHORT).show();
            return;
        }

        findViewById(R.id.btnPublishService).setEnabled(false);
        Toast.makeText(this, "Uploading images...", Toast.LENGTH_SHORT).show();

        final String finalCategory = category;
        
        new Thread(() -> {
            List<String> serverUrls = new java.util.concurrent.CopyOnWriteArrayList<>();
            java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
            
            for (android.net.Uri rawUri : selectedUris) {
                android.net.Uri compressed = com.poliku.polygoplus.network.ImageUtils.compressImage(this, rawUri);
                
                NetworkApi.uploadImage(this, compressed, new NetworkApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        serverUrls.add(response.optString("url"));
                        checkCompletion();
                    }

                    @Override
                    public void onError(String message) {
                        checkCompletion();
                    }

                    private void checkCompletion() {
                        if (count.incrementAndGet() == selectedUris.size()) {
                            finalizeServicePublish(title, finalCategory, price, time, availability, description, serverUrls);
                        }
                    }
                });
            }
        }).start();
    }

    private void finalizeServicePublish(String title, String category, String price, String time, String availability, String desc, List<String> urls) {
        if (urls.isEmpty()) {
            runOnUiThread(() -> {
                findViewById(R.id.btnPublishService).setEnabled(true);
                Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        String finalImageString = String.join("|", urls);

        runOnUiThread(() -> {
            // Pricing logic
            int checkedPriceId = chipGroupPriceType.getCheckedChipId();
            String pricePrefix = "";
            if (checkedPriceId != View.NO_ID) {
                com.google.android.material.chip.Chip chip = findViewById(checkedPriceId);
                String type = chip.getText().toString();
                if (type.contains("Starts")) pricePrefix = "Starts at ";
                else if (type.contains("Hourly")) pricePrefix = "RM " + price + "/hr";
            }

            // Fulfillment Type
            int checkedFulfillId = chipGroupFulfillment.getCheckedChipId();
            String fulfillment = "In-Person";
            if (checkedFulfillId != View.NO_ID) {
                com.google.android.material.chip.Chip chip = findViewById(checkedFulfillId);
                fulfillment = chip.getText().toString();
            }

            String finalPriceDisplay = pricePrefix.isEmpty() ? "RM " + price : (pricePrefix.contains("/") ? pricePrefix : pricePrefix + "RM " + price);
            String finalDescription = desc + "\n\n⏱️ Delivery: " + time + "\n📍 Mode: " + fulfillment + "\n📅 Availability: " + availability;

            NetworkApi.addListing(AppDataStore.userId(this), title, category, finalPriceDisplay, finalDescription, finalImageString, "Campus Wide (Service)", new NetworkApi.Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    AppDataStore.addUserListing(AddServiceActivity.this, title, category, finalPriceDisplay, finalDescription, finalImageString, "Campus Wide (Service)");
                    Toast.makeText(AddServiceActivity.this, "Service posted successfully!", Toast.LENGTH_LONG).show();
                    finish();
                }

                @Override
                public void onError(String message) {
                    findViewById(R.id.btnPublishService).setEnabled(true);
                    Toast.makeText(AddServiceActivity.this, "Post error: " + message, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
