package com.poliku.polygoplus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;

import org.json.JSONObject;

public class AddServiceActivity extends AppCompatActivity {

    private String selectedImageUri = "";
    private TextInputLayout tilCustomCategory;
    private TextInputEditText etCustomCategory, etTitle, etPrice, etAvailability, etDescription, etTime;
    private AutoCompleteTextView autoCategory;
    private ChipGroup chipGroupPriceType, chipGroupFulfillment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_service);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topBar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        setupUI();
        setupCategory();
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

        findViewById(R.id.btnAddPortfolio).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 42);
        });

        findViewById(R.id.btnPublishService).setOnClickListener(v -> publishService());
        
        findViewById(R.id.btnSaveServiceDraft).setOnClickListener(v -> {
            AppDataStore.saveDraft(this, etTitle.getText().toString(), 
                    autoCategory.getText().toString(), etPrice.getText().toString(), 
                    etDescription.getText().toString(), selectedImageUri, "Campus Wide");
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

        if (title.isEmpty() || category.isEmpty() || price.isEmpty() || description.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Pricing Type Prefix
        int checkedPriceId = chipGroupPriceType.getCheckedChipId();
        String pricePrefix = "";
        if (checkedPriceId != View.NO_ID) {
            Chip chip = findViewById(checkedPriceId);
            String type = chip.getText().toString();
            if (type.contains("Starts")) pricePrefix = "Starts at ";
            else if (type.contains("Hourly")) pricePrefix = "RM " + price + "/hr";
        }

        // Fulfillment Type
        int checkedFulfillId = chipGroupFulfillment.getCheckedChipId();
        String fulfillment = "In-Person";
        if (checkedFulfillId != View.NO_ID) {
            Chip chip = findViewById(checkedFulfillId);
            fulfillment = chip.getText().toString();
        }

        String finalPriceDisplay = pricePrefix.isEmpty() ? "RM " + price : (pricePrefix.contains("/") ? pricePrefix : pricePrefix + "RM " + price);
        String finalDescription = description + "\n\n⏱️ Delivery: " + time + "\n📍 Mode: " + fulfillment + "\n📅 Availability: " + availability;

        findViewById(R.id.btnPublishService).setEnabled(false);
        String userId = AppDataStore.userId(this);

        // DEMO HYBRID SYNC: Same logic as products
        AppDataStore.addUserListing(this, title, category, finalPriceDisplay, finalDescription, selectedImageUri, "Campus Wide (Service)");

        NetworkApi.addListing(userId, title, category, finalPriceDisplay, finalDescription, selectedImageUri, new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                Toast.makeText(AddServiceActivity.this, "Service posted successfully!", Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AddServiceActivity.this, "Service posted (Local Mode)", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 42 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                selectedImageUri = uri.toString();
                findViewById(R.id.layoutPortfolioPlaceholder).setVisibility(View.GONE);
                ImageView img = findViewById(R.id.imgPortfolio);
                img.setImageURI(uri);
                img.setVisibility(View.VISIBLE);
            } catch (Exception ignored) {}
        }
    }
}
