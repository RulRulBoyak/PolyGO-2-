package com.poliku.polygoplus;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;
import android.content.Intent;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.poliku.polygoplus.network.AiHelper;
import com.poliku.polygoplus.viewmodel.EditProductViewModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.PhotoPreviewAdapter;
import com.poliku.polygoplus.ui.VerificationGate;
import com.poliku.polygoplus.worker.PublishListingWorker;

import org.json.JSONObject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

@AndroidEntryPoint
public class EditProductActivity extends BaseActivity {

    public static final String EXTRA_PREFILL_TITLE = "prefill_title";
    public static final String EXTRA_PREFILL_PRICE = "prefill_price";
    public static final String EXTRA_PREFILL_DESCRIPTION = "prefill_description";
    public static final String EXTRA_PREFILL_CATEGORY = "prefill_category";

    @Inject PolyGoRepository polyGoRepository;
    private EditProductViewModel viewModel;
    private TextInputEditText etName, etPrice, etDescription, etCustomCategory, etCustomLocation, etTags, etFreeSlots;
    private AutoCompleteTextView autoCompleteCategory, autoCompleteLocation, autoCompleteMajor;
    private TextInputLayout tilCustomCategory, tilCustomLocation;
    private ChipGroup chipGroupFreeSlotsQuick;
    private final List<PolyGoApi.Major> majors = new ArrayList<>();
    private PhotoPreviewAdapter photoAdapter;
    private final List<Uri> selectedUris = new ArrayList<>();
    private UUID publishWorkId;
    private String lastAiTitle = "";
    private String lastAiPrice = "";
    private String lastAiDescription = "";

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMedia =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(5), uris -> {
                if (!uris.isEmpty()) {
                    for (Uri uri : uris) {
                        if (!selectedUris.contains(uri)) selectedUris.add(uri);
                        try {
                            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) {}
                    }
                    photoAdapter.updateData(selectedUris);
                    updateViewModelUris();
                } else {
                    Toast.makeText(this, R.string.no_photos_found, Toast.LENGTH_LONG).show();
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
        etCustomLocation = findViewById(R.id.etCustomLocation);
        etTags = findViewById(R.id.etTags);
        autoCompleteCategory = findViewById(R.id.autoCompleteCategory);
        autoCompleteLocation = findViewById(R.id.autoCompleteLocation);
        tilCustomCategory = findViewById(R.id.tilCustomCategory);
        tilCustomLocation = findViewById(R.id.tilCustomLocation);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topBar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, v.getPaddingTop() + systemBars.top, 0, 0);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomBar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(0, 0, 0, v.getPaddingBottom() + Math.max(systemBars.bottom, ime.bottom));
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
        RecyclerView rv = findViewById(R.id.rvPhotoPreviews);
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
            ((MaterialButton) v).setText(R.string.ai_thinking);

            AiHelper.suggestListingDetails(polyGoRepository, this, selectedUris.get(0), new AiHelper.AiCallback() {
                @Override
                public void onResult(String title, String price, String description) {
                    lastAiTitle = title == null ? "" : title;
                    lastAiPrice = price == null ? "" : price;
                    lastAiDescription = description == null ? "" : description;
                    etName.setText(title);
                    etPrice.setText(price);
                    etDescription.setText(description);
                    v.setEnabled(true);
                    ((MaterialButton) v).setText(R.string.ai_suggest_details);
                    findViewById(R.id.btnFlagAiSuggestion).setVisibility(View.VISIBLE);
                    HapticManager.success(EditProductActivity.this);
                    Toast.makeText(EditProductActivity.this, R.string.ai_suggestions_applied, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    v.setEnabled(true);
                    ((MaterialButton) v).setText(R.string.ai_suggest_details);
                    Toast.makeText(EditProductActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        });

        findViewById(R.id.btnFlagAiSuggestion).setOnClickListener(v -> {
            HapticManager.heavyTap(v);
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.flag_ai_suggestion)
                .setMessage(R.string.flag_ai_suggestion_prompt)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.flag_ai_suggestion_confirm, (d, w) -> {
                    String details = "";
                    try {
                        JSONObject aiContent = new JSONObject();
                        aiContent.put("title", lastAiTitle);
                        aiContent.put("price", lastAiPrice);
                        aiContent.put("description", lastAiDescription);
                        details = aiContent.toString();
                    } catch (Exception ignored) {}
                    polyGoRepository.submitReport(
                            AppDataStore.userId(EditProductActivity.this),
                            "ai_suggestion",
                            AppDataStore.userId(EditProductActivity.this),
                            getString(R.string.flag_ai_suggestion_reason),
                            details,
                            new Callback<BaseResponse>() {
                                @Override
                                public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                                    HapticManager.success(EditProductActivity.this);
                                    Toast.makeText(EditProductActivity.this, R.string.ai_flag_reported, Toast.LENGTH_SHORT).show();
                                    findViewById(R.id.btnFlagAiSuggestion).setVisibility(View.GONE);
                                }

                                @Override
                                public void onFailure(Call<BaseResponse> call, Throwable t) {
                                    Toast.makeText(EditProductActivity.this, R.string.ai_flag_failed, Toast.LENGTH_LONG).show();
                                }
                            });
                }).show();
        });

        setupPublishAction();
        setupLocationPicker();
        setupDraftAction();
        
        // Rule 3.3: Observe and restore state
        observeViewModel();
        
        loadDraftIfAny();
        applyPrefillExtras();
    }

    private void applyPrefillExtras() {
        Intent intent = getIntent();
        String title = intent.getStringExtra(EXTRA_PREFILL_TITLE);
        String price = intent.getStringExtra(EXTRA_PREFILL_PRICE);
        String description = intent.getStringExtra(EXTRA_PREFILL_DESCRIPTION);
        String category = intent.getStringExtra(EXTRA_PREFILL_CATEGORY);

        if (title != null && !title.isEmpty()) {
            etName.setText(title);
        }
        if (price != null && !price.isEmpty()) {
            etPrice.setText(price);
            try {
                viewModel.setPrice(Double.parseDouble(price));
            } catch (NumberFormatException ignored) {
                // Keep the typed text as-is
            }
        }
        if (description != null && !description.isEmpty()) {
            etDescription.setText(description);
        }
        if (category != null && !category.isEmpty()) {
            autoCompleteCategory.setText(category, false);
        }
    }

    private void observeViewModel() {
        viewModel.price.observe(this, value -> etPrice.setText(String.format("%.2f", value)));
        viewModel.imageUri.observe(this, uriString -> {
            if (uriString != null && !uriString.isEmpty()) {
                String[] parts = uriString.split("\\|");
                if (selectedUris.isEmpty()) { // Only auto-load if list is empty
                    for (String p : parts) selectedUris.add(Uri.parse(p));
                    photoAdapter.updateData(selectedUris);
                }
            }
        });
    }

    private void updateViewModelUris() {
        StringJoiner joiner = new StringJoiner("|");
        for (Uri u : selectedUris) joiner.add(u.toString());
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
        viewModel.setCustomLocation(etCustomLocation.getText() == null ? "" : etCustomLocation.getText().toString());
        viewModel.setTags(etTags.getText() == null ? "" : etTags.getText().toString());
    }

    private void setupLocationPicker() {
        List<String> sortedLandmarks = new ArrayList<>();
        Collections.addAll(sortedLandmarks, AppDataStore.PKS_LANDMARKS);
        Collections.sort(sortedLandmarks);

        autoCompleteLocation.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, sortedLandmarks));
        autoCompleteLocation.setOnItemClickListener((parent, view, position, id) -> {
            String selected = sortedLandmarks.get(position);
            toggleCustomLocation("Other".equalsIgnoreCase(selected));
        });

        ChipGroup chips = findViewById(R.id.chipGroupMeetup);
        chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            View chip = group.findViewById(checkedIds.get(0));
            if (chip instanceof Chip) {
                autoCompleteLocation.setText(((Chip) chip).getText());
                toggleCustomLocation(false);
            }
        });
    }

    private void toggleCustomLocation(boolean visible) {
        ViewGroup parent = (ViewGroup) tilCustomLocation.getParent();
        TransitionManager.beginDelayedTransition(parent, new AutoTransition());
        applyCustomLocationVisibility(visible);
        if (visible) {
            etCustomLocation.requestFocus();
        }
    }

    private void applyCustomLocationVisibility(boolean visible) {
        tilCustomLocation.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            etCustomLocation.setText("");
        }
    }

    private void restoreLocationUi(String base, String custom) {
        if ("Other".equalsIgnoreCase(base)) {
            applyCustomLocationVisibility(true);
            etCustomLocation.setText(custom == null ? "" : custom);
        } else {
            applyCustomLocationVisibility(false);
        }
    }

    private void restoreDraftLocation(String saved) {
        if (saved == null || saved.isEmpty()) {
            autoCompleteLocation.setText("Near campus", false);
            applyCustomLocationVisibility(false);
            return;
        }
        String trimmed = saved.trim();
        List<String> landmarks = Arrays.asList(AppDataStore.PKS_LANDMARKS);
        if ("Other".equalsIgnoreCase(trimmed) || "Other Campus Spot".equalsIgnoreCase(trimmed)) {
            autoCompleteLocation.setText("Other", false);
            applyCustomLocationVisibility(true);
            return;
        }
        if (landmarks.contains(trimmed)) {
            autoCompleteLocation.setText(trimmed, false);
            applyCustomLocationVisibility(false);
            return;
        }
        autoCompleteLocation.setText("Other", false);
        applyCustomLocationVisibility(true);
        etCustomLocation.setText(trimmed);
    }

    private String selectedLocation() {
        String base = autoCompleteLocation.getText() == null ? "" : autoCompleteLocation.getText().toString().trim();
        if ("Other".equalsIgnoreCase(base)) {
            String custom = etCustomLocation.getText() == null ? "" : etCustomLocation.getText().toString().trim();
            return custom.isEmpty() ? "Other Campus Spot" : custom;
        }
        return base.isEmpty() ? "Near campus" : base;
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
            etTags.setText(viewModel.getTags());
            restoreLocationUi(viewModel.getLocation(), viewModel.getCustomLocation());
            return;
        }
        JSONObject draft = AppDataStore.getDraft(this, draftId);
        if (draft == null) return;
        etName.setText(draft.optString("title"));
        autoCompleteCategory.setText(draft.optString("category"), false);
        etPrice.setText(draft.optString("price"));
        etDescription.setText(draft.optString("description"));
        restoreDraftLocation(draft.optString("location", "Near campus"));
        viewModel.setImageUri(draft.optString("imageUri"));
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());
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
        polyGoRepository.getCategories(new Callback<PolyGoApi.CategoryResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.CategoryResponse> call, Response<PolyGoApi.CategoryResponse> response) {
                PolyGoApi.CategoryResponse body = response.body();
                List<String> names = new ArrayList<>();
                if (body != null && body.categories != null) {
                    for (PolyGoApi.Category c : body.categories) {
                        names.add(c.name);
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
            public void onFailure(Call<PolyGoApi.CategoryResponse> call, Throwable t) {
                String[] fallback = {"Electronics", "Fashion", "Home", "Books", "Services", "Others"};
                autoCompleteCategory.setAdapter(new ArrayAdapter<>(EditProductActivity.this, android.R.layout.simple_list_item_1, fallback));
            }
        });
    }

    private void loadMajors() {
        polyGoRepository.getMajors(new Callback<PolyGoApi.MajorsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.MajorsResponse> call, Response<PolyGoApi.MajorsResponse> response) {
                PolyGoApi.MajorsResponse body = response.body();
                if (body != null && body.majors != null && !body.majors.isEmpty()) {
                    majors.clear();
                    majors.addAll(body.majors);
                    List<String> names = new ArrayList<>();
                    for (PolyGoApi.Major m : majors) {
                        names.add(m.name);
                    }
                    autoCompleteMajor.setAdapter(new ArrayAdapter<>(EditProductActivity.this, android.R.layout.simple_list_item_1, names));
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.MajorsResponse> call, Throwable t) {
            }
        });
    }

    private void setupPublishAction() {
        etFreeSlots = findViewById(R.id.etFreeSlots);
        autoCompleteMajor = findViewById(R.id.autoCompleteMajor);
        chipGroupFreeSlotsQuick = findViewById(R.id.chipGroupFreeSlotsQuick);
        chipGroupFreeSlotsQuick.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                Chip chip = group.findViewById(checkedIds.get(0));
                etFreeSlots.setText(chip.getText());
            }
        });
        loadMajors();

        findViewById(R.id.btnSaveProduct).setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            String title = etName.getText().toString().trim();
            String category = autoCompleteCategory.getText().toString().trim();
            if ("Others".equalsIgnoreCase(category)) {
                category = etCustomCategory.getText() == null ? "" : etCustomCategory.getText().toString().trim();
                // Propose new category
                if (!category.isEmpty()) {
                    polyGoRepository.proposeCategory(category, new Callback<BaseResponse>() {
                        @Override public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {}
                        @Override public void onFailure(Call<BaseResponse> call, Throwable t) {}
                    });
                }
            }
            String price = etPrice.getText().toString();
            String description = etDescription.getText().toString();
            String tags = etTags.getText() == null ? "" : etTags.getText().toString().trim();

            if (selectedUris.isEmpty()) {
                Toast.makeText(this, "Add at least one photo", Toast.LENGTH_SHORT).show();
                return;
            }
            if (title.isEmpty() || category.isEmpty() || price.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, "Complete the listing details", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!VerificationGate.requireApproved(this)) {
                return;
            }

            v.setEnabled(false);
            Toast.makeText(this, "Uploading images...", Toast.LENGTH_SHORT).show();

            final String finalCategory = category;
            final String finalTags = tags;

            String ownerId = AppDataStore.userId(this);
            if (ownerId == null) {
                v.setEnabled(true);
                Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] imageUris = new String[selectedUris.size()];
            for (int i = 0; i < selectedUris.size(); i++) {
                imageUris[i] = selectedUris.get(i).toString();
            }

            String freeSlots = etFreeSlots.getText() == null ? "" : etFreeSlots.getText().toString().trim();
            int majorId = 0;
            String selectedMajor = autoCompleteMajor.getText().toString().trim();
            for (PolyGoApi.Major m : majors) {
                if (m.name.equals(selectedMajor)) {
                    majorId = m.id;
                    break;
                }
            }

            Data.Builder dataBuilder = new Data.Builder()
                    .putString("owner_id", ownerId)
                    .putString("title", title)
                    .putString("category", finalCategory)
                    .putString("price", price)
                    .putString("description", description)
                    .putString("tags", finalTags)
                    .putString("location", selectedLocation())
                    .putString("free_slots", freeSlots)
                    .putStringArray("image_uris", imageUris);
            if (majorId > 0) {
                dataBuilder.putInt("major_id", majorId);
            }
            Data data = dataBuilder.build();

            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PublishListingWorker.class)
                    .setInputData(data)
                    .build();
            publishWorkId = request.getId();
            WorkManager.getInstance(this).enqueue(request);
            observePublishResult(v, title, finalCategory, price, description);
        });
    }

    private void observePublishResult(View btn, String title, String category, String price, String description) {
        WorkManager workManager = WorkManager.getInstance(this);
        if (publishWorkId != null) {
            workManager.getWorkInfoByIdLiveData(publishWorkId).observe(this, workInfo -> {
                if (workInfo == null) return;
                if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                    String listingId = workInfo.getOutputData().getString("listing_id");
                    String imageUrl = workInfo.getOutputData().getString("image_url");
                    if (listingId != null && imageUrl != null) {
                        HapticManager.success(EditProductActivity.this);
                        celebrate();
                        AppDataStore.addUserListing(EditProductActivity.this, listingId, title, category, price, description, imageUrl, selectedLocation());
                        Toast.makeText(EditProductActivity.this, "Listing published!", Toast.LENGTH_LONG).show();
                        new Handler(Looper.getMainLooper()).postDelayed(EditProductActivity.this::finish, 1500);
                    } else {
                        btn.setEnabled(true);
                        Toast.makeText(EditProductActivity.this, "Listing error", Toast.LENGTH_SHORT).show();
                    }
                } else if (workInfo.getState() == WorkInfo.State.FAILED || workInfo.getState() == WorkInfo.State.CANCELLED) {
                    btn.setEnabled(true);
                    String error = workInfo.getOutputData().getString("error");
                    Toast.makeText(EditProductActivity.this, error != null ? error : "Upload failed", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // Common animations handled by BaseActivity
}
