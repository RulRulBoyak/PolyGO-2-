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

import androidx.lifecycle.ViewModelProvider;
import com.poliku.polygoplus.viewmodel.EditProductViewModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;

import org.json.JSONObject;

public class EditProductActivity extends AppCompatActivity {

    private EditProductViewModel viewModel;
    private TextInputEditText etName, etPrice, etDescription, etCustomCategory;
    private AutoCompleteTextView autoCompleteCategory, autoCompleteLocation;
    private TextInputLayout tilCustomCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_product);
        AppDataStore.initialize(this);
        viewModel = new ViewModelProvider(this).get(EditProductViewModel.class);

        etName = findViewById(R.id.etProductName);
        etPrice = findViewById(R.id.etPrice);
        etDescription = findViewById(R.id.etDescription);
        etCustomCategory = findViewById(R.id.etCustomCategory);
        autoCompleteCategory = findViewById(R.id.autoCompleteCategory);
        autoCompleteLocation = findViewById(R.id.autoCompleteLocation);
        tilCustomCategory = findViewById(R.id.tilCustomCategory);

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
        findViewById(R.id.btnAddPhoto).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 41);
        });
        setupPublishAction();
        setupLocationPicker();
        setupDraftAction();
        
        // Rule 3.3: Observe and restore state
        observeViewModel();
        
        loadDraftIfAny();
    }

    private void observeViewModel() {
        viewModel.price.observe(this, value -> etPrice.setText(String.format("%.2f", value)));
        viewModel.imageUri.observe(this, uri -> {
            if (uri != null && !uri.isEmpty()) {
                android.widget.ImageView image = findViewById(R.id.imgSelectedPhoto);
                image.setImageURI(android.net.Uri.parse(uri.split("\\|")[0]));
                image.setVisibility(View.VISIBLE);
                findViewById(R.id.selectedPhotoCard).setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save current input to ViewModel
        viewModel.setTitle(etName.getText().toString());
        viewModel.setCategory(autoCompleteCategory.getText().toString());
        viewModel.setDescription(etDescription.getText().toString());
        viewModel.setLocation(autoCompleteLocation.getText().toString());
    }

    private void setupLocationPicker() {
        autoCompleteLocation.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, AppDataStore.PKS_LANDMARKS));
        com.google.android.material.chip.ChipGroup chips = findViewById(R.id.chipGroupMeetup);
        chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            android.view.View chip = group.findViewById(checkedIds.get(0));
            if (chip instanceof com.google.android.material.chip.Chip) {
                autoCompleteLocation.setText(((com.google.android.material.chip.Chip) chip).getText());
            }
        });
    }

    private String selectedLocation() {
        String value = autoCompleteLocation.getText() == null ? "" : autoCompleteLocation.getText().toString().trim();
        return value.isEmpty() ? "Near campus" : value;
    }

    private void setupDraftAction() {
        findViewById(R.id.btnSaveDraft).setOnClickListener(v -> {
            AppDataStore.saveDraft(this, etName.getText().toString(),
                    autoCompleteCategory.getText().toString().trim(),
                    etPrice.getText().toString(), etDescription.getText().toString(), 
                    viewModel.imageUri.getValue(), selectedLocation());
            Toast.makeText(this, "Draft saved", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void loadDraftIfAny() {
        String draftId = getIntent().getStringExtra("draft_id");
        if (draftId == null) {
            // Restore from ViewModel if not a draft
            etName.setText(viewModel.getTitle());
            autoCompleteCategory.setText(viewModel.getCategory(), false);
            etDescription.setText(viewModel.getDescription());
            autoCompleteLocation.setText(viewModel.getLocation(), false);
            return;
        }
        org.json.JSONObject draft = AppDataStore.getDraft(this, draftId);
        if (draft == null) return;
        etName.setText(draft.optString("title"));
        autoCompleteCategory.setText(draft.optString("category"), false);
        etPrice.setText(draft.optString("price"));
        etDescription.setText(draft.optString("description"));
        autoCompleteLocation.setText(draft.optString("location", "Near campus"), false);
        viewModel.setImageUri(draft.optString("imageUri"));
    }

    private void setupToolbar() {
        findViewById(R.id.toolbar).setOnClickListener(v -> finish());
    }

    private void setupPriceAdjuster() {
        findViewById(R.id.btnPricePlus).setOnClickListener(v -> {
            viewModel.adjustPrice(1.0);
        });

        findViewById(R.id.btnPriceMinus).setOnClickListener(v -> {
            viewModel.adjustPrice(-1.0);
        });
    }

    private void setupCategoryDropdown() {
        String[] categories = {"Electronics", "Fashion", "Home", "Books", "Services", "Others"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categories);
        autoCompleteCategory.setAdapter(adapter);

        autoCompleteCategory.setOnItemClickListener((parent, view, position, id) -> {
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
            String title = etName.getText().toString().trim();
            String category = autoCompleteCategory.getText().toString().trim();
            if ("Others".equalsIgnoreCase(category)) {
                category = etCustomCategory.getText() == null ? "" : etCustomCategory.getText().toString().trim();
            }
            String price = etPrice.getText().toString();
            String description = etDescription.getText().toString();

            String currentImage = viewModel.imageUri.getValue();
            if (currentImage == null || currentImage.isEmpty()) {
                Toast.makeText(this, "Add at least one photo", Toast.LENGTH_SHORT).show();
                return;
            }
            if (title.isEmpty() || category.isEmpty() || price.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, "Complete the listing details", Toast.LENGTH_SHORT).show();
                return;
            }

            v.setEnabled(false);
            Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();

            // 1. Get the first image URI
            android.net.Uri uri = android.net.Uri.parse(currentImage.split("\\|")[0]);
            String finalCategory = category;

            // 2. Upload to server FIRST
            NetworkApi.uploadImage(this, uri, new NetworkApi.Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    String serverImageUrl = response.optString("url");
                    
                    // 3. Now add the listing with the REAL server URL
                    NetworkApi.addListing(AppDataStore.userId(EditProductActivity.this), title, finalCategory, price, description, serverImageUrl, selectedLocation(), new NetworkApi.Callback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            AppDataStore.addUserListing(EditProductActivity.this, title, finalCategory, price, description, serverImageUrl, selectedLocation());
                            Toast.makeText(EditProductActivity.this, "Listing published!", Toast.LENGTH_LONG).show();
                            finish();
                        }

                        @Override
                        public void onError(String message) {
                            v.setEnabled(true);
                            Toast.makeText(EditProductActivity.this, "Listing error: " + message, Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    v.setEnabled(true);
                    Toast.makeText(EditProductActivity.this, "Upload failed: " + message, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 41 || resultCode != RESULT_OK || data == null) return;
        java.util.ArrayList<String> uris = new java.util.ArrayList<>();
        if (data.getClipData() != null) {
            int count = Math.min(5, data.getClipData().getItemCount());
            for (int i = 0; i < count; i++) uris.add(data.getClipData().getItemAt(i).getUri().toString());
        } else if (data.getData() != null) {
            uris.add(data.getData().toString());
        }
        if (uris.isEmpty()) return;
        for (String uri : uris) {
            try {
                getContentResolver().takePersistableUriPermission(android.net.Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
        }
        viewModel.setImageUri(String.join("|", uris));
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
