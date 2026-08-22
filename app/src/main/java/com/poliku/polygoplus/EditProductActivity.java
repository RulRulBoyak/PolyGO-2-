package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;

import org.json.JSONObject;

public class EditProductActivity extends AppCompatActivity {

    private TextInputEditText etPrice, etCustomCategory;
    private TextInputLayout tilCustomCategory;
    private double currentPrice = 0.0;
    private String selectedImageUri = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_product);
        AppDataStore.initialize(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topBar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, v.getPaddingTop() + systemBars.top, 0, 0);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomBar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, v.getPaddingBottom() + systemBars.bottom);
            return insets;
        });

        setupToolbar();
        setupPriceAdjuster();
        setupCategoryDropdown();
        findViewById(R.id.btnAddPhoto).setOnClickListener(v -> { Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("image/*"); intent.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(intent, 41); });
        setupPublishAction();
    }

    private void setupToolbar() {
        findViewById(R.id.toolbar).setOnClickListener(v -> finish());
    }

    private void setupPriceAdjuster() {
        etPrice = findViewById(R.id.etPrice);
        
        findViewById(R.id.btnPricePlus).setOnClickListener(v -> {
            currentPrice += 1.0;
            updatePriceDisplay();
        });

        findViewById(R.id.btnPriceMinus).setOnClickListener(v -> {
            if (currentPrice >= 1.0) {
                currentPrice -= 1.0;
                updatePriceDisplay();
            }
        });
    }

    private void updatePriceDisplay() {
        etPrice.setText(String.format("%.2f", currentPrice));
    }

    private void setupCategoryDropdown() {
        String[] categories = {"Electronics", "Fashion", "Home", "Books", "Services", "Others"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categories);
        AutoCompleteTextView autoComplete = findViewById(R.id.autoCompleteCategory);
        autoComplete.setAdapter(adapter);

        tilCustomCategory = findViewById(R.id.tilCustomCategory);
        etCustomCategory = findViewById(R.id.etCustomCategory);

        autoComplete.setOnItemClickListener((parent, view, position, id) -> {
            String selected = categories[position];
            if ("Others".equalsIgnoreCase(selected)) {
                tilCustomCategory.setVisibility(View.VISIBLE);
            } else {
                tilCustomCategory.setVisibility(View.GONE);
                etCustomCategory.setText("");
            }
        });
    }

    private void setupPublishAction() {
        findViewById(R.id.btnSaveProduct).setOnClickListener(v -> {
            String title = value(R.id.etProductName);
            String category = ((AutoCompleteTextView)findViewById(R.id.autoCompleteCategory)).getText().toString().trim();
            if ("Others".equalsIgnoreCase(category)) {
                category = etCustomCategory.getText() == null ? "" : etCustomCategory.getText().toString().trim();
            }
            String price = value(R.id.etPrice);
            String description = value(R.id.etDescription);

            if (selectedImageUri.isEmpty()) { Toast.makeText(this, "Add at least one photo", Toast.LENGTH_SHORT).show(); return; }
            if (title.isEmpty() || category.isEmpty() || price.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, "Complete the listing details", Toast.LENGTH_SHORT).show();
                if (category.isEmpty() && tilCustomCategory.getVisibility() == View.VISIBLE) {
                    etCustomCategory.setError("Enter a category name");
                }
                return;
            }
            try { if (Double.parseDouble(price) <= 0) { Toast.makeText(this, "Price must be greater than zero", Toast.LENGTH_SHORT).show(); return; } } catch (NumberFormatException e) { Toast.makeText(this, "Enter a valid price", Toast.LENGTH_SHORT).show(); return; }
            
            String userId = AppDataStore.userId(this);
            if ("0".equals(userId)) {
                Toast.makeText(this, "Please log in again to publish", Toast.LENGTH_SHORT).show();
                return;
            }

            v.setEnabled(false);
            NetworkApi.addListing(userId, title, category, price, description, selectedImageUri, new NetworkApi.Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    Toast.makeText(EditProductActivity.this, "Listing published", Toast.LENGTH_LONG).show();
                    finish();
                }

                @Override
                public void onError(String message) {
                    v.setEnabled(true);
                    Toast.makeText(EditProductActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private String value(int id) { TextInputEditText input = findViewById(id); return input.getText() == null ? "" : input.getText().toString().trim(); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == 41 && resultCode == RESULT_OK && data != null && data.getData() != null) { selectedImageUri = data.getData().toString(); ImageView image = findViewById(R.id.imgSelectedPhoto); image.setImageURI(data.getData()); image.setVisibility(android.view.View.VISIBLE); findViewById(R.id.selectedPhotoCard).setVisibility(android.view.View.VISIBLE); } }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
