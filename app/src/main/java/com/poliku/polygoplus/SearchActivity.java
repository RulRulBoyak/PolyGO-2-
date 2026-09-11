package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

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
import com.poliku.polygoplus.ui.HapticManager;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
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
    private Spinner category;
    private TextView count;
    private View empty;
    private View suggestions;
    private String currentSort = "newest";
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        AppDataStore.initialize(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        search = findViewById(R.id.etSearch);
        category = findViewById(R.id.spinnerCategory);
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
        category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                filter();
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        reloadListings();
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
        
        if (adapter != null && q.isEmpty()) {
            all.clear();
            all.addAll(AppDataStore.listingsToEntities(AppDataStore.getListings(this)));
            filter();
        }

        polyGoRepository.searchListings(q, currentSort, new Callback<PolyGoApi.ListingsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.ListingsResponse> call, Response<PolyGoApi.ListingsResponse> response) {
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
                    all.clear();
                    all.addAll(AppDataStore.listingsToEntities(AppDataStore.getListings(SearchActivity.this)));
                    java.util.Set<String> seen = new java.util.HashSet<>();
                    for (ListingEntity l : all) {
                        if (l.id != null) seen.add(l.id);
                    }
                    for (ListingEntity r : remote) {
                        if (r.id == null || r.id.isEmpty() || seen.add(r.id)) {
                            all.add(r);
                        }
                    }
                } else {
                    all.clear();
                    all.addAll(remote);
                }
                filter();
            }

            @Override
            public void onFailure(Call<PolyGoApi.ListingsResponse> call, Throwable t) {
                if (q.isEmpty()) {
                    all.clear();
                    all.addAll(AppDataStore.listingsToEntities(AppDataStore.getListings(SearchActivity.this)));
                    filter();
                }
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
                    for (PolyGoApi.Category c : body.categories) {
                        names.add(c.name);
                    }
                }
                
                ArrayAdapter<String> adapter = new ArrayAdapter<>(SearchActivity.this, android.R.layout.simple_spinner_dropdown_item, names);
                category.setAdapter(adapter);

                String initial = getIntent().getStringExtra(EXTRA_CATEGORY);
                if (initial != null) {
                    for (int i = 0; i < names.size(); i++) {
                        if (names.get(i).equalsIgnoreCase(initial)) {
                            category.setSelection(i);
                            break;
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.CategoryResponse> call, Throwable t) {
                String[] fallback = {"All categories", "Food", "Drink", "Tech", "Electronics", "Fashion", "Books", "Repair", "Home", "Services"};
                category.setAdapter(new ArrayAdapter<>(SearchActivity.this, android.R.layout.simple_spinner_dropdown_item, fallback));
            }
        });
    }

    private void showSortDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_sort, null);
        dialog.setContentView(view);
        
        RadioGroup group = view.findViewById(R.id.radioGroupSort);
        for (int i = 0; i < group.getChildCount(); i++) {
            RadioButton rb = (RadioButton) group.getChildAt(i);
            if (currentSort.equals(rb.getTag())) rb.setChecked(true);
        }

        view.findViewById(R.id.btnApplySort).setOnClickListener(v -> {
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
        String cat = category.getSelectedItem() == null ? "All categories" : category.getSelectedItem().toString();
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
