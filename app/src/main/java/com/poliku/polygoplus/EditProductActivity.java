package com.poliku.polygoplus;

import android.net.Uri;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.content.Intent;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.widget.NestedScrollView;
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
import com.poliku.polygoplus.ui.LandmarkPickerSheet;
import com.poliku.polygoplus.ui.PhotoPreviewAdapter;
import com.poliku.polygoplus.ui.UiUtils;
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
                    UiUtils.snackbarError(findViewById(android.R.id.content), R.string.no_photos_found);
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
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_add_photo_for_ai);
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
                    UiUtils.snackbar(EditProductActivity.this.findViewById(android.R.id.content), R.string.ai_suggestions_applied);
                }

                @Override
                public void onError(String error) {
                    v.setEnabled(true);
                    ((MaterialButton) v).setText(R.string.ai_suggest_details);
                    UiUtils.snackbarError(EditProductActivity.this.findViewById(android.R.id.content), error);
                }
            });
        });

        findViewById(R.id.btnFlagAiSuggestion).setOnClickListener(v -> {
            HapticManager.heavyTap(v);
            new MaterialAlertDialogBuilder(this)
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
                                    UiUtils.snackbar(EditProductActivity.this.findViewById(android.R.id.content), R.string.ai_flag_reported);
                                    findViewById(R.id.btnFlagAiSuggestion).setVisibility(View.GONE);
                                }

                                @Override
                                public void onFailure(Call<BaseResponse> call, Throwable t) {
                                    UiUtils.snackbarError(EditProductActivity.this.findViewById(android.R.id.content), R.string.ai_flag_failed);
                                }
                            });
                }).show();
        });

        setupPublishAction();
        setupLocationPicker();
        setupDraftAction();

        wireFocusScroll();

        // Rule 3.3: Observe and restore state
        observeViewModel();
        
        loadDraftIfAny();
        applyPrefillExtras();
    }

    private void wireFocusScroll() {
        NestedScrollView scroll = findViewById(R.id.formScroll);
        if (scroll == null) return;
        scrollFocusedInputIntoView(etName);
        scrollFocusedInputIntoView(etPrice);
        scrollFocusedInputIntoView(etDescription);
        scrollFocusedInputIntoView(etTags);
        scrollFocusedInputIntoView(etFreeSlots);
        scrollFocusedInputIntoView(etCustomCategory);
        scrollFocusedInputIntoView(etCustomLocation);
        scrollFocusedInputIntoView(autoCompleteCategory);
        scrollFocusedInputIntoView(autoCompleteLocation);
        scrollFocusedInputIntoView(autoCompleteMajor);
    }

    private void scrollFocusedInputIntoView(final View child) {
        child.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) return;
            NestedScrollView scroll = findViewById(R.id.formScroll);
            if (scroll == null || scroll.getChildCount() == 0) return;
            View bar = findViewById(R.id.bottomBar);
            int bottomClearance = bar != null ? bar.getHeight() : dp(80);
            int third = (int) (scroll.getHeight() * 0.2f);
            scroll.post(() -> {
                int[] childLoc = new int[2];
                int[] scrollLoc = new int[2];
                child.getLocationInWindow(childLoc);
                scroll.getLocationInWindow(scrollLoc);
                int fieldTop = childTopWithinContent(childLoc, scrollLoc, scroll);
                int fieldBottom = fieldTop + child.getHeight();
                int visibleTop = scroll.getScrollY() + third;
                int visibleBottom = scroll.getScrollY() + scroll.getHeight() - bottomClearance;
                int target = scroll.getScrollY();
                if (fieldBottom > visibleBottom) {
                    target = target + (fieldBottom - visibleBottom);
                } else if (fieldTop < visibleTop) {
                    target = target - third;
                }
                int maxScroll = scroll.getChildAt(0).getHeight() - scroll.getHeight();
                target = Math.max(0, Math.min(target, maxScroll));
                scroll.smoothScrollTo(0, target);
            });
        });
    }

    private int childTopWithinContent(int[] childLoc, int[] scrollLoc, NestedScrollView scroll) {
        return childLoc[1] - scrollLoc[1] + scroll.getScrollY();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
        syncCategoryChipsFromField();
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
        autoCompleteLocation.setOnClickListener(v -> LandmarkPickerSheet.show(
                EditProductActivity.this,
                autoCompleteLocation.getText() == null ? "Near campus" : autoCompleteLocation.getText().toString(),
                name -> {
                    autoCompleteLocation.setText(name, false);
                    toggleCustomLocation("Other".equalsIgnoreCase(name));
                }));

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
            UiUtils.snackbar(findViewById(android.R.id.content), R.string.toast_draft_saved);
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
            syncCategoryChipsFromField();
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
        syncCategoryChipsFromField();
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
                populateCategoryChips(names);
            }

            @Override
            public void onFailure(Call<PolyGoApi.CategoryResponse> call, Throwable t) {
                populateCategoryChips(Arrays.asList("Electronics", "Fashion", "Home", "Books", "Services", "Others"));
            }
        });
    }

    private void populateCategoryChips(List<String> names) {
        ChipGroup group = findViewById(R.id.chipGroupProductCategory);
        if (group == null) return;
        group.removeAllViews();
        for (String name : names) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_category_chip, group, false);
            chip.setId(View.generateViewId());
            chip.setText(name);
            chip.setTag(name);
            if (!"Others".equalsIgnoreCase(name)) {
                chip.setChipIcon(getDrawable(UiUtils.categoryIcon(name)));
                chip.setChipIconTint(ColorStateList.valueOf(getColor(UiUtils.categoryColor(name))));
            }
            chip.setOnCheckedChangeListener((c, checked) -> {
                if (!checked || c.getTag() == null) return;
                autoCompleteCategory.setText(c.getTag().toString(), false);
                if ("Others".equalsIgnoreCase(c.getTag().toString())) {
                    tilCustomCategory.setVisibility(View.VISIBLE);
                } else {
                    tilCustomCategory.setVisibility(View.GONE);
                    etCustomCategory.setText("");
                }
            });
            group.addView(chip);
        }
        syncCategoryChipsFromField();
    }

    private void syncCategoryChipsFromField() {
        ChipGroup group = findViewById(R.id.chipGroupProductCategory);
        if (group == null || group.getChildCount() == 0) return;
        String current = autoCompleteCategory.getText() == null ? "" : autoCompleteCategory.getText().toString().trim();
        boolean matched = false;
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            boolean isMatch = chip.getTag() != null && chip.getTag().toString().equalsIgnoreCase(current);
            if (isMatch) {
                chip.setChecked(true);
                matched = true;
            }
        }
        if (!matched && !current.isEmpty()) {
            group.clearCheck();
            tilCustomCategory.setVisibility(View.VISIBLE);
        } else if (!matched) {
            ((Chip) group.getChildAt(0)).setChecked(true);
        }
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
                    populateMajorChips(names);
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.MajorsResponse> call, Throwable t) {
                populateMajorChips(Arrays.asList("Accountancy", "Business Studies", "Civil Engineering", "Computer Science",
                        "Electrical Engineering", "Graphic Design", "Hospitality & Tourism",
                        "Information Technology", "Mechanical Engineering", "Software Engineering"));
            }
        });
    }

    private void populateMajorChips(List<String> names) {
        ChipGroup group = findViewById(R.id.chipGroupProductMajor);
        if (group == null) return;
        group.removeAllViews();
        for (String name : names) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_category_chip, group, false);
            chip.setId(View.generateViewId());
            chip.setText(name);
            chip.setTag(name);
            chip.setOnCheckedChangeListener((c, checked) -> {
                if (!checked || c.getTag() == null) return;
                autoCompleteMajor.setText(c.getTag().toString(), false);
            });
            group.addView(chip);
        }
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
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_add_at_least_one_photo);
                return;
            }
            if (title.isEmpty() || category.isEmpty() || price.isEmpty() || description.isEmpty()) {
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_complete_listing_details);
                return;
            }
            double parsedPrice;
            try {
                parsedPrice = Double.parseDouble(price.trim());
            } catch (NumberFormatException e) {
                parsedPrice = -1;
            }
            if (parsedPrice < 0) {
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_enter_valid_price);
                return;
            }

            if (!VerificationGate.requireApproved(this)) {
                return;
            }

            v.setEnabled(false);
            UiUtils.snackbar(findViewById(android.R.id.content), R.string.toast_uploading_images);

            final String finalCategory = category;
            final String finalTags = tags;

            String ownerId = AppDataStore.userId(this);
            if (ownerId == null) {
                v.setEnabled(true);
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_please_sign_in_first);
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
            observePublishResult(v, ownerId, title, finalCategory, price, description);
        });
    }

    private void observePublishResult(View btn, String ownerId, String title, String category, String price, String description) {
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
                        
                        // Sync with Room to ensure HomeFragment sees it
                        polyGoRepository.refreshListings(ownerId);
                        
                        UiUtils.snackbar(EditProductActivity.this.findViewById(android.R.id.content), R.string.toast_listing_published);
                        new Handler(Looper.getMainLooper()).postDelayed(EditProductActivity.this::finish, 1500);
                    } else {
                        btn.setEnabled(true);
                        UiUtils.snackbarError(EditProductActivity.this.findViewById(android.R.id.content), R.string.toast_listing_error);
                    }
                } else if (workInfo.getState() == WorkInfo.State.FAILED || workInfo.getState() == WorkInfo.State.CANCELLED) {
                    btn.setEnabled(true);
                    String error = workInfo.getOutputData().getString("error");
                    UiUtils.snackbarError(EditProductActivity.this.findViewById(android.R.id.content), error != null ? error : getString(R.string.toast_upload_failed));
                }
            });
        }
    }

    // Common animations handled by BaseActivity
}
