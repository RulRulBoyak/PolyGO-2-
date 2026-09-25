package com.poliku.polygoplus;

import android.net.Uri;
import android.text.TextUtils;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.widget.TextView;
import com.bumptech.glide.Glide;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.widget.NestedScrollView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.poliku.polygoplus.network.AiHelper;
import com.poliku.polygoplus.network.ImageUtils;
import com.poliku.polygoplus.viewmodel.EditProductViewModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.ExitGuard;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.LandmarkPickerSheet;
import com.poliku.polygoplus.ui.PhotoPreviewAdapter;
import com.poliku.polygoplus.ui.PickerOptionSheet;
import com.poliku.polygoplus.ui.UiUtils;
import com.poliku.polygoplus.ui.VerificationGate;
import com.poliku.polygoplus.worker.PublishListingWorker;

import org.json.JSONObject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@AndroidEntryPoint
public class EditProductActivity extends BaseActivity {

    public static final String EXTRA_PREFILL_TITLE = "prefill_title";
    public static final String EXTRA_PREFILL_PRICE = "prefill_price";
    public static final String EXTRA_PREFILL_DESCRIPTION = "prefill_description";
    public static final String EXTRA_PREFILL_CATEGORY = "prefill_category";
    public static final String EXTRA_PREFILL_TAGS = "prefill_tags";
    public static final String EXTRA_PREFILL_CONDITION = "prefill_condition";
    public static final String EXTRA_LISTING_ID = "listing_id";

    @Inject PolyGoRepository polyGoRepository;
    private EditProductViewModel viewModel;
    private TextInputEditText etName, etPrice, etDescription, etCustomCategory, etCustomLocation, etTags, etFreeSlots, etOriginalPrice;
    private AutoCompleteTextView autoCompleteCategory, autoCompleteMajor;
    private TextInputEditText autoCompleteLocation, autoCompleteCondition;
    private TextInputLayout tilCustomCategory, tilCustomLocation;
    private ChipGroup chipGroupFreeSlotsQuick;
    private final List<PolyGoApi.Major> majors = new ArrayList<>();
    private PhotoPreviewAdapter photoAdapter;
    private final List<Uri> selectedUris = new ArrayList<>();
    private TextView tvPhotoCount;
    private MaterialSwitch switchAutoReply, switchHideFromFriends;
    private UUID publishWorkId;
    private boolean editMode = false;
    private String editListingId;
    private String lastAiTitle = "";
    private String lastAiPrice = "";
    private String lastAiDescription = "";
    private String lastAiCategory = "";
    private String lastAiTags = "";
    private boolean isAcademic = false;
    private final List<String> categoryNames = new ArrayList<>();
    private String initialFormState = "";
    private boolean formLoaded;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMedia =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(10), uris -> {
                if (!uris.isEmpty()) {
                    for (Uri uri : uris) {
                        if (selectedUris.contains(uri)) {
                            continue;
                        }
                        if (selectedUris.size() >= 10) {
                            UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_max_photos);
                            break;
                        }
                        selectedUris.add(uri);
                        try {
                            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) {}
                    }
                    photoAdapter.updateData(selectedUris);
                    updateViewModelUris();
                    updatePhotoCount();
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
        etOriginalPrice = findViewById(R.id.etOriginalPrice);
        autoCompleteCategory = findViewById(R.id.autoCompleteCategory);
        autoCompleteLocation = findViewById(R.id.autoCompleteLocation);
        autoCompleteCondition = findViewById(R.id.autoCompleteCondition);
        tilCustomCategory = findViewById(R.id.tilCustomCategory);
        tilCustomLocation = findViewById(R.id.tilCustomLocation);

        setupConditionPicker();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topBar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomBar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(0, 0, 0, Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        setupToolbar();
        setupPriceAdjuster();
        setupPublishSeatByRole();
        setupRoleAwareListing();
        setupCategoryDropdown();
        setupCategoryBrowse();
        
        photoAdapter = new PhotoPreviewAdapter(position -> {
            selectedUris.remove(position);
            photoAdapter.updateData(selectedUris);
            updateViewModelUris();
            updatePhotoCount();
        });
        RecyclerView rv = findViewById(R.id.rvPhotoPreviews);
        rv.setAdapter(photoAdapter);

        tvPhotoCount = findViewById(R.id.tvPhotoCount);
        switchAutoReply = findViewById(R.id.switchAutoReply);
        switchHideFromFriends = findViewById(R.id.switchHideFromFriends);
        updatePhotoCount();

        findViewById(R.id.btnPhotoTips).setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.photo_tips)
                    .setMessage(R.string.photo_tips_body)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });

        findViewById(R.id.btnAddPhoto).setOnClickListener(v -> {
            if (selectedUris.size() >= 10) {
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_max_photos);
                return;
            }
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
                public void onResult(String title, String price, String description, String category,
                                     List<String> tags, String condition, double confidence) {
                    if (isFinishing() || isDestroyed()) return;
                    lastAiTitle = title == null ? "" : title;
                    lastAiPrice = price == null ? "" : price;
                    lastAiDescription = description == null ? "" : description;
                    lastAiCategory = category == null ? "" : category;
                    lastAiTags = joinAiTags(tags);
                    etName.setText(title);
                    etPrice.setText(price);
                    etDescription.setText(description);
                    etTags.setText(lastAiTags);
                    if (!lastAiCategory.isEmpty()) {
                        autoCompleteCategory.setText(lastAiCategory, false);
                        isAcademic = "Books".equalsIgnoreCase(lastAiCategory)
                                || "Services".equalsIgnoreCase(lastAiCategory);
                        tilCustomCategory.setVisibility(View.GONE);
                        etCustomCategory.setText("");
                        syncCategoryChipsFromField();
                        setupRoleAwareListing();
                    }
                    if (condition != null && !condition.trim().isEmpty()) {
                        applyCondition(condition);
                    }
                    v.setEnabled(true);
                    ((MaterialButton) v).setText(R.string.ai_suggest_details);
                    findViewById(R.id.btnFlagAiSuggestion).setVisibility(View.VISIBLE);
                    HapticManager.success(EditProductActivity.this);
                    UiUtils.snackbar(EditProductActivity.this.findViewById(android.R.id.content), R.string.ai_suggestions_applied);
                }

                @Override
                public void onError(String error) {
                    if (isFinishing() || isDestroyed()) return;
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
                        aiContent.put("category", lastAiCategory);
                        aiContent.put("tags", lastAiTags);
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

        setupPriceLogic();
        setupUserIdentity();

        wireFocusScroll();

        // Rule 3.3: Observe and restore state
        observeViewModel();
        
        loadDraftIfAny();
        applyPrefillExtras();
        loadEditListing();
        snapshotForm();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });
    }

    private String currentFormState() {
        StringBuilder sb = new StringBuilder();
        sb.append(etName.getText()).append('|');
        sb.append(etPrice.getText()).append('|');
        sb.append(etDescription.getText()).append('|');
        sb.append(etCustomCategory.getText()).append('|');
        sb.append(etCustomLocation.getText()).append('|');
        sb.append(etTags.getText()).append('|');
        sb.append(etFreeSlots.getText()).append('|');
        sb.append(etOriginalPrice.getText()).append('|');
        sb.append(autoCompleteCategory.getText()).append('|');
        sb.append(autoCompleteLocation.getText()).append('|');
        sb.append(autoCompleteCondition.getText()).append('|');
        sb.append(autoCompleteMajor.getText()).append('|');
        sb.append(selectedUris.size()).append('|');
        sb.append(switchAutoReply != null && switchAutoReply.isChecked()).append('|');
        sb.append(switchHideFromFriends != null && switchHideFromFriends.isChecked());
        return sb.toString();
    }

    private void snapshotForm() {
        initialFormState = currentFormState();
        formLoaded = true;
    }

    private boolean isDirty() {
        return formLoaded && !initialFormState.equals(currentFormState());
    }

    private void confirmExit() {
        if (!isDirty()) {
            finish();
            return;
        }
        ExitGuard.show(this, R.string.action_save_draft, this::saveDraftNow, this::finish);
    }

    private void wireFocusScroll() {
        NestedScrollView scroll = findViewById(R.id.formScroll);
        if (scroll == null) return;
        scrollFocusedInputIntoView(etName);
        scrollFocusedInputIntoView(etPrice);
        scrollFocusedInputIntoView(etDescription);
        scrollFocusedInputIntoView(etTags);
        scrollFocusedInputIntoView(etFreeSlots);
        scrollFocusedInputIntoView(etOriginalPrice);
        scrollFocusedInputIntoView(etCustomCategory);
        scrollFocusedInputIntoView(etCustomLocation);
        scrollFocusedInputIntoView(autoCompleteCategory);
        scrollFocusedInputIntoView(autoCompleteLocation);
        scrollFocusedInputIntoView(autoCompleteMajor);
        scrollFocusedInputIntoView(autoCompleteCondition);
    }

    private static final String[] CONDITION_ENUMS = {"New", "Used - Like New", "Used - Good", "Used - Fair"};

    private void setupConditionPicker() {
        autoCompleteCondition.setOnClickListener(v -> PickerOptionSheet.show(
                this,
                getString(R.string.condition_picker_title),
                getString(R.string.condition_picker_subtitle),
                normalizeCondition(autoCompleteCondition.getText() == null ? "" : autoCompleteCondition.getText().toString()),
                conditionOptions(),
                enumValue -> {
                    applyCondition(enumValue);
                }));
        applyCondition(normalizeCondition(autoCompleteCondition.getText() == null ? "" : autoCompleteCondition.getText().toString()));
    }

    private List<PickerOptionSheet.Option> conditionOptions() {
        List<PickerOptionSheet.Option> options = new ArrayList<>();
        for (int i = 0; i < CONDITION_ENUMS.length; i++) {
            String value = CONDITION_ENUMS[i];
            String display = getString(conditionStringRes(i));
            int descRes = conditionDescRes(i);
            options.add(new PickerOptionSheet.Option(display, descRes != 0 ? getString(descRes) : null, 0));
        }
        return options;
    }

    private int conditionStringRes(int index) {
        switch (index) {
            case 1: return R.string.condition_used_like_new;
            case 2: return R.string.condition_used_good;
            case 3: return R.string.condition_used_fair;
            default: return R.string.condition_new;
        }
    }

    private int conditionDescRes(int index) {
        switch (index) {
            case 1: return R.string.condition_desc_like_new;
            case 2: return R.string.condition_desc_good;
            case 3: return R.string.condition_desc_fair;
            default: return R.string.condition_desc_new;
        }
    }

    /** Map any previously stored (possibly localized) condition text to a canonical ENUM value. */
    private String normalizeCondition(String text) {
        if (text == null) return CONDITION_ENUMS[0];
        String t = text.trim();
        if (t.isEmpty()) return CONDITION_ENUMS[0];
        for (int i = 0; i < CONDITION_ENUMS.length; i++) {
            if (t.equalsIgnoreCase(CONDITION_ENUMS[i]) || t.equalsIgnoreCase(getString(conditionStringRes(i)))) {
                return CONDITION_ENUMS[i];
            }
        }
        return CONDITION_ENUMS[0];
    }

    private void applyCondition(String enumValue) {
        String safe = normalizeCondition(enumValue);
        autoCompleteCondition.setText(safe);
        autoCompleteCondition.setSelection(0);
    }

    private void setupPriceLogic() {
        // Professional logic: ensure Original Price >= Price
        etOriginalPrice.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    double orig = Double.parseDouble(etOriginalPrice.getText().toString());
                    double current = Double.parseDouble(etPrice.getText().toString());
                    if (orig < current) {
                        etOriginalPrice.setText(String.format(Locale.US, "%.2f", current));
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void setupUserIdentity() {
        String name = AppDataStore.userName(this);
        ((TextView) findViewById(R.id.tvUserName)).setText(name);

        String photo = AppDataStore.userProfilePic(this);
        if (!photo.isEmpty()) {
            Glide.with(this)
                    .load(photo)
                    .circleCrop()
                    .into((ImageView) findViewById(R.id.ivUserAvatar));
        }
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
        String tags = intent.getStringExtra(EXTRA_PREFILL_TAGS);
        String condition = intent.getStringExtra(EXTRA_PREFILL_CONDITION);

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
        if (tags != null && !tags.isEmpty()) etTags.setText(tags);
        if (condition != null && !condition.isEmpty()) {
            applyCondition(condition);
        }
        syncCategoryChipsFromField();
    }

    private static String joinAiTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "";
        StringJoiner joiner = new StringJoiner(", ");
        for (String tag : tags) {
            if (tag != null && !tag.trim().isEmpty()) joiner.add(tag.trim());
        }
        return joiner.toString();
    }

    private void observeViewModel() {
        viewModel.price.observe(this, value -> etPrice.setText(String.format(Locale.US, "%.2f", value)));
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

    private void updatePhotoCount() {
        if (tvPhotoCount != null) {
            tvPhotoCount.setText(getString(R.string.photo_count, selectedUris.size(), 10));
        }
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
        viewModel.setCondition(autoCompleteCondition.getText().toString());
        viewModel.setOriginalPrice(etOriginalPrice.getText() == null ? "" : etOriginalPrice.getText().toString());
    }

    private void setupLocationPicker() {
        autoCompleteLocation.setOnClickListener(v -> LandmarkPickerSheet.show(
                EditProductActivity.this,
                autoCompleteLocation.getText() == null ? "Near campus" : autoCompleteLocation.getText().toString(),
                name -> {
                    autoCompleteLocation.setText(name);
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
            autoCompleteLocation.setText("Near campus");
            applyCustomLocationVisibility(false);
            return;
        }
        String trimmed = saved.trim();
        List<String> landmarks = Arrays.asList(AppDataStore.PKS_LANDMARKS);
        if ("Other".equalsIgnoreCase(trimmed) || "Other Campus Spot".equalsIgnoreCase(trimmed)) {
            autoCompleteLocation.setText("Other");
            applyCustomLocationVisibility(true);
            return;
        }
        if (landmarks.contains(trimmed)) {
            autoCompleteLocation.setText(trimmed);
            applyCustomLocationVisibility(false);
            return;
        }
        autoCompleteLocation.setText("Other");
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
            saveDraftNow();
        });
    }

    private String currentDraftId;

    private void saveDraftNow() {
        AppDataStore.saveDraft(this, etName.getText().toString(),
                autoCompleteCategory.getText().toString().trim(),
                etPrice.getText().toString(), etDescription.getText().toString(),
                viewModel.imageUri.getValue(), selectedLocation());
        if (currentDraftId != null) AppDataStore.deleteDraft(this, currentDraftId);
        UiUtils.snackbar(findViewById(android.R.id.content), R.string.toast_draft_saved);
        finish();
    }

    private void loadDraftIfAny() {
        String draftId = getIntent().getStringExtra("draft_id");
        if (draftId == null) {
            // Restore from ViewModel if not a draft
            etName.setText(viewModel.getTitle());
            autoCompleteCategory.setText(viewModel.getCategory(), false);
            etDescription.setText(viewModel.getDescription());
            autoCompleteLocation.setText(viewModel.getLocation());
            etTags.setText(viewModel.getTags());
            autoCompleteCondition.setText(viewModel.getCondition());
            etOriginalPrice.setText(viewModel.getOriginalPrice());
            restoreLocationUi(viewModel.getLocation(), viewModel.getCustomLocation());
            syncCategoryChipsFromField();
            return;
        }
        JSONObject draft = AppDataStore.getDraft(this, draftId);
        if (draft == null) return;
        currentDraftId = draftId;
        etName.setText(draft.optString("title"));
        autoCompleteCategory.setText(draft.optString("category"), false);
        etPrice.setText(draft.optString("price"));
        etDescription.setText(draft.optString("description"));
        restoreDraftLocation(draft.optString("location", "Near campus"));
        viewModel.setImageUri(draft.optString("imageUri"));
        syncCategoryChipsFromField();
    }

    private void loadEditListing() {
        editListingId = getIntent().getStringExtra(EXTRA_LISTING_ID);
        if (editListingId == null || editListingId.trim().isEmpty()) return;

        editMode = true;
        MaterialButton save = findViewById(R.id.btnSaveProduct);
        if (save != null) save.setText(getString(R.string.save_changes));
        UiUtils.snackbar(findViewById(android.R.id.content), R.string.toast_loading_listing);

        polyGoRepository.getListing(editListingId.trim(), new Callback<PolyGoApi.ListingsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.ListingsResponse> call, Response<PolyGoApi.ListingsResponse> response) {
                PolyGoApi.ListingsResponse body = response.body();
                if (!response.isSuccessful() || body == null || body.listings == null || body.listings.isEmpty()) {
                    MaterialButton b = findViewById(R.id.btnSaveProduct);
                    if (b != null) b.setEnabled(true);
                    UiUtils.snackbarError(EditProductActivity.this.findViewById(android.R.id.content), R.string.toast_listing_error);
                    return;
                }
                PolyGoApi.Listing l = body.listings.get(0);
                etName.setText(l.title == null ? "" : l.title);
                etPrice.setText(l.price == null ? "" : l.price);
                try {
                    viewModel.setPrice(Double.parseDouble(l.price == null ? "0" : l.price));
                } catch (NumberFormatException ignored) {}
                etDescription.setText(l.description == null ? "" : l.description);
                autoCompleteCategory.setText(l.category == null ? "" : l.category, false);
                restoreDraftLocation(l.location == null ? "Near campus" : l.location);
                syncCategoryChipsFromField();
                etTags.setText(l.tags == null ? "" : l.tags);
                etFreeSlots.setText(l.free_slots == null ? "" : l.free_slots);
                etOriginalPrice.setText(l.original_price == null ? "" : l.original_price);
                String cond = normalizeCondition(l.condition);
                applyCondition(cond);

                if (switchAutoReply != null) switchAutoReply.setChecked(l.autoReply);
                if (switchHideFromFriends != null) switchHideFromFriends.setChecked(l.hideFromFriends);

                selectedUris.clear();
                if (l.image_url != null) {
                    String[] parts = l.image_url.split("\\|");
                    for (String p : parts) {
                        if (!p.trim().isEmpty()) selectedUris.add(Uri.parse(p.trim()));
                    }
                }
                photoAdapter.updateData(selectedUris);
                updatePhotoCount();
                updateViewModelUris();
                snapshotForm();

                UiUtils.snackbar(EditProductActivity.this.findViewById(android.R.id.content), R.string.toast_listing_loaded);
            }

            @Override
            public void onFailure(Call<PolyGoApi.ListingsResponse> call, Throwable t) {
                MaterialButton b = findViewById(R.id.btnSaveProduct);
                if (b != null) b.setEnabled(true);
                UiUtils.snackbarError(EditProductActivity.this.findViewById(android.R.id.content), R.string.toast_could_not_reach_server);
            }
        });
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> confirmExit());
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
                    UiUtils.rememberCategoryIcons(EditProductActivity.this, body.categories);
                    for (PolyGoApi.Category c : body.categories) {
                        names.add(c.name);
                    }
                }
                names.add("Others");
                categoryNames.clear();
                categoryNames.addAll(names);
                populateCategoryChips(names);
            }

            @Override
            public void onFailure(Call<PolyGoApi.CategoryResponse> call, Throwable t) {
                List<String> fallback = Arrays.asList("Electronics", "Fashion", "Home", "Books", "Services", "Others");
                categoryNames.clear();
                categoryNames.addAll(fallback);
                populateCategoryChips(fallback);
            }
        });
    }

    private void setupCategoryBrowse() {
        findViewById(R.id.btnBrowseCategories).setOnClickListener(v -> {
            if (categoryNames.isEmpty()) {
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_categories_loading);
                return;
            }
            String current = autoCompleteCategory.getText() == null ? "" : autoCompleteCategory.getText().toString().trim();
            List<PickerOptionSheet.Option> options = new ArrayList<>();
            for (String name : categoryNames) {
                int icon = "Others".equalsIgnoreCase(name) ? 0 : UiUtils.categoryIcon(name);
                options.add(new PickerOptionSheet.Option(name, null, icon));
            }
            PickerOptionSheet.show(
                    this,
                    getString(R.string.category_picker_title),
                    getString(R.string.category_picker_subtitle),
                    current,
                    options,
                    picked -> {
                        autoCompleteCategory.setText(picked, false);
                        isAcademic = "Books".equalsIgnoreCase(picked) || "Services".equalsIgnoreCase(picked);
                        setupRoleAwareListing();
                        if ("Others".equalsIgnoreCase(picked)) {
                            tilCustomCategory.setVisibility(View.VISIBLE);
                        } else {
                            tilCustomCategory.setVisibility(View.GONE);
                            etCustomCategory.setText("");
                        }
                        syncCategoryChipsFromField();
                    });
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
                isAcademic = "Books".equalsIgnoreCase(c.getTag().toString()) || "Services".equalsIgnoreCase(c.getTag().toString());
                setupRoleAwareListing(); // Update UI based on new category

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

    private void setupRoleAwareListing() {
        String role = AppDataStore.userRole(this);
        MaterialToolbar tb = findViewById(R.id.toolbar);
        MaterialCheckBox chk = findViewById(R.id.chkFreeForStudents);

        if ("Visitor".equals(role)) {
            if (chk != null) chk.setVisibility(View.GONE);
            if (tb != null) tb.setTitle(R.string.edit_product_visitor_title);
            return;
        }

        if ("Lecturer".equals(role)) {
            if (tb != null) tb.setTitle(R.string.edit_product_lecturer_title);
            if (chk != null) {
                chk.setVisibility(isAcademic ? View.VISIBLE : View.GONE);
                chk.setOnCheckedChangeListener((btn, checked) -> {
                    if (checked) {
                        HapticManager.selectionTick(this);
                        etPrice.setText("0.00");
                        etPrice.setEnabled(false);
                    } else {
                        etPrice.setEnabled(true);
                    }
                });
            }
        }
    }

    private void setupPublishSeatByRole() {
        // Placeholder for future specialized seating/quota logic
        // Ensuring the method exists to satisfy the call in onCreate
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

            String role = AppDataStore.userRole(this);
            if ("Visitor".equals(role)) {
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_visitor_cannot_post);
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
            String condition = normalizeCondition(autoCompleteCondition.getText() == null ? "" : autoCompleteCondition.getText().toString());
            boolean autoReply = switchAutoReply != null && switchAutoReply.isChecked();
            boolean hideFromFriends = switchHideFromFriends != null && switchHideFromFriends.isChecked();
            String originalPrice = etOriginalPrice.getText() == null ? "" : etOriginalPrice.getText().toString().trim();

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
                    .putString("original_price", originalPrice)
                    .putString("condition", condition)
                    .putString("description", description)
                    .putString("tags", finalTags)
                    .putString("location", selectedLocation())
                    .putString("free_slots", freeSlots)
                    .putStringArray("image_uris", imageUris)
                    .putBoolean("auto_reply", autoReply)
                    .putBoolean("hide_from_friends", hideFromFriends);
            if (majorId > 0) {
                dataBuilder.putInt("major_id", majorId);
            }
            Data data = dataBuilder.build();

            if (editMode) {
                publishEdit(v, ownerId, title, finalCategory, price, description, finalTags,
                        freeSlots, condition, autoReply, hideFromFriends, originalPrice, majorId);
                return;
            }

            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PublishListingWorker.class)
                    .setInputData(data)
                    .build();
            publishWorkId = request.getId();
            WorkManager.getInstance(this).enqueue(request);
            observePublishResult(v, ownerId, title, finalCategory, price, description);
        });
    }

    private void publishEdit(View btn, String ownerId, String title, String category, String price,
                              String description, String tags, String freeSlots, String condition,
                              boolean autoReply, boolean hideFromFriends, String originalPrice, int majorId) {
        List<String> kept = new ArrayList<>();
        List<Uri> toUpload = new ArrayList<>();
        for (Uri u : selectedUris) {
            String scheme = u.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                kept.add(u.toString());
            } else {
                toUpload.add(u);
            }
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<String> finalUrls = new ArrayList<>(kept);
            for (Uri u : toUpload) {
                try {
                    Uri compressed = ImageUtils.compressImage(this, u);
                    if (compressed == null) continue;
                    File file = new File(compressed.getPath());
                    if (!file.exists()) continue;
                    CountDownLatch latch = new CountDownLatch(1);
                    polyGoRepository.uploadImage(file, new Callback<PolyGoApi.UploadResponse>() {
                        @Override
                        public void onResponse(Call<PolyGoApi.UploadResponse> call, Response<PolyGoApi.UploadResponse> response) {
                            if (response.isSuccessful() && response.body() != null
                                    && response.body().isSuccess() && response.body().url != null) {
                                finalUrls.add(response.body().url);
                            }
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(Call<PolyGoApi.UploadResponse> call, Throwable t) {
                            latch.countDown();
                        }
                    });
                    try {
                        latch.await(60, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } catch (Exception ignored) {}
            }

            String imageJoined = TextUtils.join("|", finalUrls);
            if (finalUrls.isEmpty()) {
                runOnUiThread(() -> {
                    btn.setEnabled(true);
                    UiUtils.snackbarError(EditProductActivity.this.findViewById(android.R.id.content), R.string.toast_upload_failed);
                });
                executor.shutdown();
                return;
            }

            String location = selectedLocation();
            runOnUiThread(() -> {
                polyGoRepository.updateListing(editListingId, ownerId, title, category, price, description,
                        imageJoined, tags, location, freeSlots, majorId > 0 ? majorId : null, condition,
                        originalPrice, autoReply, hideFromFriends, new Callback<PolyGoApi.AddListingResponse>() {
                            @Override
                            public void onResponse(Call<PolyGoApi.AddListingResponse> call, Response<PolyGoApi.AddListingResponse> response) {
                                PolyGoApi.AddListingResponse body = response.body();
                                if (response.isSuccessful() && body != null && body.isSuccess()) {
                                    HapticManager.success(EditProductActivity.this);
                                    if (currentDraftId != null) AppDataStore.deleteDraft(EditProductActivity.this, currentDraftId);
                                    AppDataStore.addUserListing(EditProductActivity.this, editListingId,
                                            title, category, price, description, imageJoined, location);
                                    polyGoRepository.refreshListings(ownerId);
                                    UiUtils.snackbar(EditProductActivity.this.findViewById(android.R.id.content), R.string.toast_listing_updated);
                                    new Handler(Looper.getMainLooper()).postDelayed(EditProductActivity.this::finish, 1500);
                                } else {
                                    btn.setEnabled(true);
                                    String msg = body != null && body.getMessage() != null
                                            ? body.getMessage() : getString(R.string.toast_listing_error);
                                    UiUtils.snackbarError(EditProductActivity.this.findViewById(android.R.id.content), msg);
                                }
                            }

                            @Override
                            public void onFailure(Call<PolyGoApi.AddListingResponse> call, Throwable t) {
                                btn.setEnabled(true);
                                UiUtils.snackbarError(EditProductActivity.this.findViewById(android.R.id.content), R.string.toast_could_not_reach_server);
                            }
                        });
                executor.shutdown();
            });
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
                        if (currentDraftId != null) AppDataStore.deleteDraft(EditProductActivity.this, currentDraftId);
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
