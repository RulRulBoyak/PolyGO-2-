package com.poliku.polygoplus;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.EmptyStates;
import com.poliku.polygoplus.ui.ExitGuard;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.UiUtils;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@AndroidEntryPoint
public class SearchActivity extends BaseActivity {
    @Inject PolyGoRepository polyGoRepository;
    public static final String EXTRA_CATEGORY = "category";
    private final List<ListingEntity> all = new ArrayList<>();
    private ProductCardAdapter adapter;
    private EditText search;
    private ChipGroup category;
    private TextView count;
    private View empty;
    private View suggestions;
    private String currentSort = "newest";
    private int reloadSeq;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        AppDataStore.initialize(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        search = findViewById(R.id.etSearch);
        category = findViewById(R.id.chipGroupCategory);
        count = findViewById(R.id.tvResultCount);
        empty = findViewById(R.id.tvEmptySearch);
        suggestions = findViewById(R.id.searchSuggestions);
        EmptyStates.bind(empty, R.drawable.ic_search, "No results found",
                "Try another keyword or browse a PKS category.", "Browse categories",
                v -> startActivity(new Intent(this, CategoryBrowseActivity.class)));
        
        findViewById(R.id.btnSort).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            showSortDialog();
        });

        bindSuggestions();
        search.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && search.getText().toString().trim().isEmpty()) showSuggestions(true);
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.search_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        loadCategories();
        RecyclerView rv = findViewById(R.id.rvSearchResults);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new ProductCardAdapter(new ArrayList<>(), new ProductCardAdapter.Listener() {
            @Override
            public void onProduct(ProductCardAdapter adapter, ListingEntity p, View sharedView) {
                Intent i = new Intent(SearchActivity.this, ProductDetailActivity.class);
                i.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, p.id);
                startActivity(i);
            }
        });
        rv.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (c > 0) HapticManager.selectionTick(SearchActivity.this);
                showSuggestions(s.toString().trim().isEmpty());
                findViewById(R.id.btnClearSearch).setVisibility(s.toString().isEmpty() ? View.GONE : View.VISIBLE);
                filter();
            }
            public void afterTextChanged(Editable e) {}
        });
        findViewById(R.id.btnClearSearch).setOnClickListener(v -> {
            search.setText("");
            search.requestFocus();
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(search);
                showSuggestions(false);
                return true;
            }
            return false;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });
    }

    private void confirmExit() {
        if (!ExitGuard.anyText(search == null ? null : search.getText())) {
            finish();
            return;
        }
        ExitGuard.show(this, this::finish);
    }

    @Override protected void onPause() {
        super.onPause();
        if (search != null) AppDataStore.addSearchQuery(this, search.getText().toString());
    }

    @Override protected void onResume() {
        super.onResume();
        if (search != null) reloadListings();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
    }

    private void reloadListings() {
        String currentUserId = AppDataStore.userId(this);
        String q = search.getText().toString().trim();
        int seq = ++reloadSeq;
        
        if (adapter != null && q.isEmpty()) {
            all.clear();
            all.addAll(AppDataStore.listingsToEntities(AppDataStore.getActiveListings(this)));
            filter();
        }
        
        polyGoRepository.searchListings(q, currentSort, new Callback<PolyGoApi.ListingsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.ListingsResponse> call, Response<PolyGoApi.ListingsResponse> response) {
                if (seq != reloadSeq) return;
                PolyGoApi.ListingsResponse body = response.body();
                List<ListingEntity> remote = new ArrayList<>();
                if (body != null && body.listings != null) {
                    for (PolyGoApi.Listing l : body.listings) {
                        AppDataStore.ProductRecord p = AppDataStore.ProductRecord.fromListing(l, currentUserId);
                        if (p != null) {
                            remote.add(p.toEntity());
                        }
                    }
                }
                
                if (q.isEmpty()) {
                    // Refresh the local cache from the authoritative server snapshot so
                    // sold/removed listings no longer linger in any offline fallback.
                    if (body != null && body.listings != null) {
                        AppDataStore.updateListingsCache(SearchActivity.this, body.listings);
                    }
                }
                all.clear();
                all.addAll(remote);
                filter();
            }

            @Override
            public void onFailure(Call<PolyGoApi.ListingsResponse> call, Throwable t) {
                if (seq != reloadSeq) return;
                all.clear();
                all.addAll(AppDataStore.listingsToEntities(AppDataStore.getActiveListings(SearchActivity.this)));
                filter();
            }
        });
    }

    private void loadCategories() {
        polyGoRepository.getCategories(new Callback<PolyGoApi.CategoryResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.CategoryResponse> call, Response<PolyGoApi.CategoryResponse> response) {
                PolyGoApi.CategoryResponse body = response.body();
                List<String> names = new ArrayList<>();
                names.add("All categories");
                if (body != null && body.categories != null) {
                    UiUtils.rememberCategoryIcons(SearchActivity.this, body.categories);
                    for (PolyGoApi.Category c : body.categories) {
                        names.add(c.name);
                    }
                }

                populateCategoryChips(names);

                String initial = getIntent().getStringExtra(EXTRA_CATEGORY);
                if (initial != null && category != null) {
                    for (int i = 0; i < category.getChildCount(); i++) {
                        View chip = category.getChildAt(i);
                        if (chip instanceof Chip && ((Chip) chip).getText().toString().equalsIgnoreCase(initial)) {
                            ((Chip) chip).setChecked(true);
                            break;
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.CategoryResponse> call, Throwable t) {
                populateCategoryChips(Arrays.asList("All categories", "Food", "Drink", "Tech", "Electronics", "Fashion", "Books", "Repair", "Home", "Services"));
            }
        });
    }

    private void populateCategoryChips(List<String> names) {
        if (category == null) return;
        category.removeAllViews();
        for (String name : names) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_category_chip, category, false);
            chip.setId(View.generateViewId());
            chip.setText(name);
            chip.setTag(name);
            if (category.getChildCount() == 0) chip.setChecked(true);
            if (!"All categories".equalsIgnoreCase(name)) {
                chip.setChipIcon(getDrawable(UiUtils.categoryIcon(name)));
                chip.setChipIconTint(ColorStateList.valueOf(getColor(UiUtils.categoryColor(name))));
            }
            chip.setOnCheckedChangeListener((c, checked) -> {
                if (checked && category.getCheckedChipId() != View.NO_ID) {
                    HapticManager.selectionTick(SearchActivity.this);
                }
                filter();
            });
            category.addView(chip);
        }
    }

    private void showSortDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.bottom_sheet_sort);
        
        RadioGroup group = dialog.findViewById(R.id.radioGroupSort);
        for (int i = 0; i < group.getChildCount(); i++) {
            RadioButton rb = (RadioButton) group.getChildAt(i);
            if (currentSort.equals(rb.getTag())) rb.setChecked(true);
        }

        dialog.findViewById(R.id.btnApplySort).setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            int id = group.getCheckedRadioButtonId();
            if (id != View.NO_ID) {
                currentSort = group.findViewById(id).getTag().toString();
                reloadListings();
            }
            dialog.dismiss();
        });
        dialog.show();
    }

    private void filter() {
        if (search == null || category == null) return;
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int checkedId = category.getCheckedChipId();
        Chip checked = checkedId == View.NO_ID ? null : category.findViewById(checkedId);
        String cat = checked == null ? "All categories" : checked.getText().toString();
        List<ListingEntity> filtered = new ArrayList<>();
        for (ListingEntity p : all) {
            String searchable = nz(p.title) + " " + nz(p.seller) + " " + nz(p.description) + " " + nz(p.category) + " " + nz(p.distance);
            searchable = searchable.toLowerCase(Locale.ROOT);
            boolean text = q.isEmpty() || searchable.contains(q);
            boolean categoryMatch = categoryMatches(p.category, cat);
            if (text && categoryMatch) filtered.add(p);
        }
        if (adapter != null) adapter.updateData(filtered);
        if (count != null) count.setText(filtered.size() + " listings");
        if (empty != null) empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showSuggestions(boolean show) {
        if (suggestions != null) suggestions.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void bindSuggestions() {
        ChipGroup recent = findViewById(R.id.chipRecent);
        ChipGroup trending = findViewById(R.id.chipTrending);
        recent.removeAllViews();
        for (String q : AppDataStore.getSearchHistory(this)) recent.addView(chip(q));
        trending.removeAllViews();
        ioExecutor.execute(() -> {
            List<String> t = AppDataStore.computeTrending(this);
            runOnUiThread(() -> {
                for (String q : t) trending.addView(chip(q));
            });
        });
        findViewById(R.id.tvClearHistory).setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            AppDataStore.clearSearchHistory(this);
            bindSuggestions();
        });
    }

    private Chip chip(String label) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            search.setText(label);
            search.setSelection(label.length());
            AppDataStore.addSearchQuery(this, label);
            showSuggestions(false);
            hideKeyboard(search);
            filter();
        });
        return chip;
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private boolean categoryMatches(String productCategory, String selectedCategory) {
        if (selectedCategory == null || selectedCategory.toLowerCase(Locale.ROOT).startsWith("all")) return true;
        String product = productCategory == null ? "" : productCategory.toLowerCase(Locale.ROOT);
        String selected = selectedCategory.toLowerCase(Locale.ROOT);
        if (selected.equals("tech")) return product.contains("tech") || product.contains("electronic");
        if (selected.equals("repair")) return product.contains("repair") || product.contains("service");
        if (selected.equals("home")) return product.contains("home") || product.contains("furniture");
        return product.contains(selected);
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
