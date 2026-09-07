package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.PhotoPreviewAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class EditProductActivity extends BaseActivity {

    private EditProductViewModel viewModel;
    private TextInputEditText etName, etPrice, etDescription, etCustomCategory;
    private AutoCompleteTextView autoCompleteCategory, autoCompleteLocation;
    private TextInputLayout tilCustomCategory;
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
        
        photoAdapter = new PhotoPreviewAdapter(position -> {
            selectedUris.remove(position);
            photoAdapter.updateData(selectedUris);
            updateViewModelUris();
        });
        androidx.recyclerview.widget.RecyclerView rv = findViewById(R.id.rvPhotoPreviews);
        rv.setAdapter(photoAdapter);

        findViewById(R.id.btnAddPhoto).setOnClickListener(v -> {
            pickMultipleMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        findViewById(R.id.btnAiSuggest).setOnClickListener(v -> {
            if (selectedUris.isEmpty()) {
                Toast.makeText(this, "Add a photo first for AI to analyze", Toast.LENGTH_SHORT).show();
                return;
            }
            HapticManager.swell(this);
            v.setEnabled(false);
            ((com.google.android.material.button.MaterialButton) v).setText(R.string.ai_thinking);

            com.poliku.polygoplus.network.AiHelper.suggestListingDetails(this, selectedUris.get(0), new com.poliku.polygoplus.network.AiHelper.Callback() {
                @Override
                public void onResult(String title, String price, String description) {
                    etName.setText(title);
                    etPrice.setText(price);
                    etDescription.setText(description);
                    v.setEnabled(true);
                    ((com.google.android.material.button.MaterialButton) v).setText(R.string.ai_suggest_details);
                    HapticManager.success(EditProductActivity.this);
                    Toast.makeText(EditProductActivity.this, "AI suggestions applied!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    v.setEnabled(true);
                    ((com.google.android.material.button.MaterialButton) v).setText(R.string.ai_suggest_details);
                    Toast.makeText(EditProductActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
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
        viewModel.imageUri.observe(this, uriString -> {
            if (uriString != null && !uriString.isEmpty()) {
                String[] parts = uriString.split("\\|");
                if (selectedUris.isEmpty()) { // Only auto-load if list is empty
                    for (String p : parts) selectedUris.add(android.net.Uri.parse(p));
                    photoAdapter.updateData(selectedUris);
                }
            }
        });
    }

    private void updateViewModelUris() {
        java.util.StringJoiner joiner = new java.util.StringJoiner("|");
        for (android.net.Uri u : selectedUris) joiner.add(u.toString());
        viewModel.setImageUri(joiner.toString());
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
            HapticManager.lightTap(v);
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
            HapticManager.lightTap(v);
            viewModel.adjustPrice(1.0);
        });

        findViewById(R.id.btnPriceMinus).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            viewModel.adjustPrice(-1.0);
        });
    }

    private void setupCategoryDropdown() {
        NetworkApi.getCategories(new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONArray list = response.optJSONArray("categories");
                List<String> names = new ArrayList<>();
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject o = list.optJSONObject(i);
                        if (o != null) names.add(o.optString("name"));
                    }
                }
                names.add("Others");

                ArrayAdapter<String> adapter = new ArrayAdapter<>(EditProductActivity.this, android.R.layout.simple_list_item_1, names);
                autoCompleteCategory.setAdapter(adapter);

                autoCompleteCategory.setOnItemClickListener((parent, view, position, id) -> {
                    String selected = names.get(position);
                    if ("Others".equalsIgnoreCase(selected)) {
                        tilCustomCategory.setVisibility(View.VISIBLE);
                    } else {
                        tilCustomCategory.setVisibility(View.GONE);
                        etCustomCategory.setText("");
                    }
                });
            }

            @Override
            public void onError(String message) {
                String[] fallback = {"Electronics", "Fashion", "Home", "Books", "Services", "Others"};
                autoCompleteCategory.setAdapter(new ArrayAdapter<>(EditProductActivity.this, android.R.layout.simple_list_item_1, fallback));
            }
        });
    }

    private void setupPublishAction() {
        findViewById(R.id.btnSaveProduct).setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            String title = etName.getText().toString().trim();
            String category = autoCompleteCategory.getText().toString().trim();
            if ("Others".equalsIgnoreCase(category)) {
                category = etCustomCategory.getText() == null ? "" : etCustomCategory.getText().toString().trim();
                // Propose new category
                if (!category.isEmpty()) {
                    NetworkApi.proposeCategory(category, new NetworkApi.Callback() {
                        @Override public void onSuccess(JSONObject response) {}
                        @Override public void onError(String message) {}
                    });
                }
            }
            String price = etPrice.getText().toString();
            String description = etDescription.getText().toString();

            if (selectedUris.isEmpty()) {
                Toast.makeText(this, "Add at least one photo", Toast.LENGTH_SHORT).show();
                return;
            }
            if (title.isEmpty() || category.isEmpty() || price.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, "Complete the listing details", Toast.LENGTH_SHORT).show();
                return;
            }

            v.setEnabled(false);
            Toast.makeText(this, "Uploading images...", Toast.LENGTH_SHORT).show();

            final String finalCategory = category;
            
            // Rule 3.3: Background processing for multi-upload
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
                                finalizePublish(v, title, finalCategory, price, description, serverUrls);
                            }
                        }
                    });
                }
            }).start();
        });
    }

    private void finalizePublish(View btn, String title, String category, String price, String desc, List<String> urls) {
        if (urls.isEmpty()) {
            runOnUiThread(() -> {
                btn.setEnabled(true);
                Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        String finalImageString = String.join("|", urls);
        
        runOnUiThread(() -> {
            NetworkApi.addListing(AppDataStore.userId(this), title, category, price, desc, finalImageString, selectedLocation(), new NetworkApi.Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    HapticManager.success(EditProductActivity.this);
                    celebrate();
                    AppDataStore.addUserListing(EditProductActivity.this, title, category, price, desc, finalImageString, selectedLocation());
                    Toast.makeText(EditProductActivity.this, "Listing published!", Toast.LENGTH_LONG).show();
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(EditProductActivity.this::finish, 1500);
                }

                @Override
                public void onError(String message) {
                    btn.setEnabled(true);
                    Toast.makeText(EditProductActivity.this, "Listing error: " + message, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // Common animations handled by BaseActivity
}
