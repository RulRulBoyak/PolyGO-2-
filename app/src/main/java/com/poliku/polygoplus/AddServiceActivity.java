package com.poliku.polygoplus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.appbar.MaterialToolbar;
import com.poliku.polygoplus.network.AiHelper;
import com.poliku.polygoplus.viewmodel.AddServiceViewModel;
import com.poliku.polygoplus.worker.PublishListingWorker;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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
public class AddServiceActivity extends BaseActivity {

    @Inject PolyGoRepository polyGoRepository;
    private AddServiceViewModel viewModel;
    private TextInputLayout tilCustomCategory;
    private TextInputEditText etCustomCategory, etTitle, etPrice, etAvailability, etDescription, etTime, etCustomLocation, etFreeSlots;
    private AutoCompleteTextView autoCategory, autoCompleteLocation, autoCompleteMajor;
    private ChipGroup chipGroupPriceType, chipGroupFulfillment, chipGroupFreeSlotsQuick;
    private final List<PolyGoApi.Major> majors = new ArrayList<>();
    private TextInputLayout tilCustomLocation;
    private PhotoPreviewAdapter photoAdapter;
    private final List<Uri> selectedUris = new ArrayList<>();
    private UUID publishWorkId;

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
        setContentView(R.layout.activity_add_service);
        viewModel = new ViewModelProvider(this).get(AddServiceViewModel.class);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topBar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomBar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(0, 0, 0, v.getPaddingBottom() + Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        setupUI();
        setupCategory();
        setupLocationPicker();
        
        // Rule 3.3: Observe and restore state
        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.imageUri.observe(this, uriString -> {
            if (uriString != null && !uriString.isEmpty()) {
                String[] parts = uriString.split("\\|");
                if (selectedUris.isEmpty()) {
                    for (String p : parts) selectedUris.add(Uri.parse(p));
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
        restoreLocationUi(viewModel.getLocation(), viewModel.getCustomLocation());
    }

    private void updateViewModelUris() {
        StringJoiner joiner = new StringJoiner("|");
        for (Uri u : selectedUris) joiner.add(u.toString());
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
        viewModel.setLocation(autoCompleteLocation.getText() == null ? "" : autoCompleteLocation.getText().toString());
        viewModel.setCustomLocation(etCustomLocation.getText() == null ? "" : etCustomLocation.getText().toString());
    }

    private void setupUI() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());
        
        etTitle = findViewById(R.id.etServiceTitle);
        etPrice = findViewById(R.id.etServicePrice);
        etTime = findViewById(R.id.etServiceTime);
        etAvailability = findViewById(R.id.etServiceAvailability);
        etDescription = findViewById(R.id.etServiceDescription);
        autoCategory = findViewById(R.id.autoCompleteServiceCategory);
        tilCustomCategory = findViewById(R.id.tilCustomServiceCategory);
        etCustomCategory = findViewById(R.id.etCustomServiceCategory);
        autoCompleteLocation = findViewById(R.id.autoCompleteLocation);
        tilCustomLocation = findViewById(R.id.tilCustomLocation);
        etCustomLocation = findViewById(R.id.etCustomLocation);
        chipGroupPriceType = findViewById(R.id.chipGroupPriceType);
        chipGroupFulfillment = findViewById(R.id.chipGroupFulfillment);
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

        photoAdapter = new PhotoPreviewAdapter(position -> {
            selectedUris.remove(position);
            photoAdapter.updateData(selectedUris);
            updateViewModelUris();
        });
        RecyclerView rv = findViewById(R.id.rvPortfolioPreviews);
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
            ((MaterialButton) v).setText(R.string.ai_thinking);

            AiHelper.suggestListingDetails(polyGoRepository, this, selectedUris.get(0), new AiHelper.AiCallback() {
                @Override
                public void onResult(String title, String price, String description) {
                    etTitle.setText(title);
                    etPrice.setText(price);
                    etDescription.setText(description);
                    v.setEnabled(true);
                    ((MaterialButton) v).setText(R.string.ai_suggest_service);
                    HapticManager.success(AddServiceActivity.this);
                }

                @Override
                public void onError(String error) {
                    v.setEnabled(true);
                    ((MaterialButton) v).setText(R.string.ai_suggest_service);
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
                    etDescription.getText().toString(), viewModel.imageUri.getValue(), selectedLocation());
            Toast.makeText(this, "Service draft saved", Toast.LENGTH_SHORT).show();
            finish();
        });
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
        } else if (base != null && !base.isEmpty()) {
            autoCompleteLocation.setText(base, false);
            applyCustomLocationVisibility(false);
        } else {
            applyCustomLocationVisibility(false);
        }
    }

    private String selectedLocation() {
        String base = autoCompleteLocation.getText() == null ? "" : autoCompleteLocation.getText().toString().trim();
        if ("Other".equalsIgnoreCase(base)) {
            String custom = etCustomLocation.getText() == null ? "" : etCustomLocation.getText().toString().trim();
            return custom.isEmpty() ? "Other Campus Spot" : custom;
        }
        return base.isEmpty() ? "Near campus" : base;
    }

    private void setupCategory() {
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
                
                ArrayAdapter<String> adapter = new ArrayAdapter<>(AddServiceActivity.this, android.R.layout.simple_list_item_1, names);
                autoCategory.setAdapter(adapter);
                autoCategory.setOnItemClickListener((parent, view, position, id) -> {
                    if ("Others".equals(names.get(position))) {
                        tilCustomCategory.setVisibility(View.VISIBLE);
                    } else {
                        tilCustomCategory.setVisibility(View.GONE);
                        etCustomCategory.setText("");
                    }
                });
            }

            @Override
            public void onFailure(Call<PolyGoApi.CategoryResponse> call, Throwable t) {
                String[] fallback = {"Repair", "Printing", "Delivery", "Cleaning", "Lessons", "Laundry", "Others"};
                autoCategory.setAdapter(new ArrayAdapter<>(AddServiceActivity.this, android.R.layout.simple_list_item_1, fallback));
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
                    autoCompleteMajor.setAdapter(new ArrayAdapter<>(AddServiceActivity.this, android.R.layout.simple_list_item_1, names));
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.MajorsResponse> call, Throwable t) {
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
        String tags = etTime.getText() == null ? "" : etTime.getText().toString(); // Service specific tags

        if ("Others".equals(category)) {
            category = etCustomCategory.getText().toString().trim();
            // Propose new category to backend
            if (!category.isEmpty()) {
                polyGoRepository.proposeCategory(category, new Callback<BaseResponse>() {
                    @Override public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {}
                    @Override public void onFailure(Call<BaseResponse> call, Throwable t) {}
                });
            }
        }

        if (title.isEmpty() || category.isEmpty() || price.isEmpty() || description.isEmpty() || selectedUris.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields including photos", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!VerificationGate.requireApproved(this)) {
            return;
        }

        findViewById(R.id.btnPublishService).setEnabled(false);
        Toast.makeText(this, "Uploading images...", Toast.LENGTH_SHORT).show();

        final String finalCategory = category;

        String finalPriceDisplay = buildPriceDisplay(price);
        String finalDescription = description + "\n\n⏱️ Delivery: " + time + "\n📍 Mode: " + fulfillmentType() + "\n📅 Availability: " + availability;

        String ownerId = AppDataStore.userId(this);
        if (ownerId == null) {
            findViewById(R.id.btnPublishService).setEnabled(true);
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
                .putString("price", finalPriceDisplay)
                .putString("description", finalDescription)
                .putString("tags", tags)
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
        observePublishResult(title, finalCategory, finalPriceDisplay, finalDescription);
    }

    private String buildPriceDisplay(String price) {
        int checkedPriceId = chipGroupPriceType.getCheckedChipId();
        String pricePrefix = "";
        if (checkedPriceId != View.NO_ID) {
            Chip chip = findViewById(checkedPriceId);
            String type = chip.getText().toString();
            if (type.contains("Starts")) pricePrefix = "Starts at ";
            else if (type.contains("Hourly")) pricePrefix = "RM " + price + "/hr";
        }
        return pricePrefix.isEmpty() ? "RM " + price : (pricePrefix.contains("/") ? pricePrefix : pricePrefix + "RM " + price);
    }

    private String fulfillmentType() {
        int checkedFulfillId = chipGroupFulfillment.getCheckedChipId();
        String fulfillment = "In-Person";
        if (checkedFulfillId != View.NO_ID) {
            Chip chip = findViewById(checkedFulfillId);
            fulfillment = chip.getText().toString();
        }
        return fulfillment;
    }

    private void observePublishResult(String title, String category, String price, String description) {
        WorkManager workManager = WorkManager.getInstance(this);
        if (publishWorkId != null) {
            workManager.getWorkInfoByIdLiveData(publishWorkId).observe(this, workInfo -> {
                if (workInfo == null) return;
                if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                    String listingId = workInfo.getOutputData().getString("listing_id");
                    String imageUrl = workInfo.getOutputData().getString("image_url");
                    if (listingId != null && imageUrl != null) {
                        HapticManager.success(AddServiceActivity.this);
                        celebrate();
                        AppDataStore.addUserListing(AddServiceActivity.this, listingId, title, category, price, description, imageUrl, selectedLocation());
                        Toast.makeText(AddServiceActivity.this, "Service posted successfully!", Toast.LENGTH_LONG).show();
                        new Handler(Looper.getMainLooper()).postDelayed(AddServiceActivity.this::finish, 1500);
                    } else {
                        findViewById(R.id.btnPublishService).setEnabled(true);
                        Toast.makeText(AddServiceActivity.this, "Post error", Toast.LENGTH_SHORT).show();
                    }
                } else if (workInfo.getState() == WorkInfo.State.FAILED || workInfo.getState() == WorkInfo.State.CANCELLED) {
                    findViewById(R.id.btnPublishService).setEnabled(true);
                    String error = workInfo.getOutputData().getString("error");
                    Toast.makeText(AddServiceActivity.this, error != null ? error : "Upload failed", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // Common animations handled by BaseActivity
}
