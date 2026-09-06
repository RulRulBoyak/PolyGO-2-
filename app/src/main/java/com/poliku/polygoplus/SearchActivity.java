package com.poliku.polygoplus;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.network.NetworkApi;
import com.poliku.polygoplus.ui.EmptyStates;
import com.poliku.polygoplus.ui.HapticManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchActivity extends AppCompatActivity {
    public static final String EXTRA_CATEGORY = "category";
    private final List<AppDataStore.ProductRecord> all = new ArrayList<>();
    private ProductCardAdapter adapter;
    private EditText search;
    private Spinner category;
    private TextView count;
    private View empty;
    private View suggestions;
    private String currentSort = "newest";

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
        EmptyStates.bind(empty, android.R.drawable.ic_menu_search, "No results found",
                "Try another keyword or browse a PKS category.", "Browse categories",
                v -> startActivity(new android.content.Intent(this, CategoryBrowseActivity.class)));
        
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

        String[] categories = {"All categories", "Food", "Drink", "Tech", "Electronics", "Fashion", "Books", "Repair", "Home", "Services"};
        category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories));
        String initial = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (initial != null) {
            boolean isCategoryShortcut = false;
            for (int i = 0; i < categories.length; i++) {
                if (categories[i].equalsIgnoreCase(initial)) {
                    category.setSelection(i);
                    isCategoryShortcut = true;
                    break;
                }
            }
            if (!isCategoryShortcut) search.setText(initial);
        }
        RecyclerView rv = findViewById(R.id.rvSearchResults);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new ProductCardAdapter(new ArrayList<>(), new ProductCardAdapter.Listener() {
            @Override
            public void onProduct(ProductCardAdapter adapter, AppDataStore.ProductRecord p, View sharedView) {
                android.content.Intent i = new android.content.Intent(SearchActivity.this, ProductDetailActivity.class);
                i.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, p.id);
                startActivity(i);
            }
        });
        rv.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                showSuggestions(s.toString().trim().isEmpty());
                filter();
            }
            public void afterTextChanged(Editable e) {}
        });
        category.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                filter();
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
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

    private void reloadListings() {
        String currentUserId = AppDataStore.userId(this);
        String q = search.getText().toString().trim();
        
        if (adapter != null && q.isEmpty()) {
             all.clear();
             all.addAll(AppDataStore.getListings(this));
             filter();
        }

        NetworkApi.searchListings(q, currentSort, new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONArray list = response.optJSONArray("listings");
                List<AppDataStore.ProductRecord> remote = new ArrayList<>();
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject o = list.optJSONObject(i);
                        if (o != null) {
                            AppDataStore.ProductRecord p = AppDataStore.ProductRecord.fromJson(o);
                            if (p != null) {
                                boolean isOwner = p.ownerId.equals(currentUserId);
                                remote.add(isOwner ? p.withOwnerStatus(true) : p);
                            }
                        }
                    }
                }
                
                if (q.isEmpty()) {
                    all.clear();
                    all.addAll(AppDataStore.getListings(SearchActivity.this));
                    for (AppDataStore.ProductRecord r : remote) {
                        boolean exists = false;
                        for (AppDataStore.ProductRecord l : all) {
                            if (l.title.equals(r.title) && l.price.equals(r.price)) { exists = true; break; }
                        }
                        if (!exists) all.add(r);
                    }
                } else {
                    all.clear();
                    all.addAll(remote);
                }
                filter();
            }

            @Override
            public void onError(String message) {
                if (q.isEmpty()) {
                    all.clear();
                    all.addAll(AppDataStore.getListings(SearchActivity.this));
                    filter();
                }
            }
        });
    }

    private void showSortDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_sort, null);
        dialog.setContentView(view);
        
        ChipGroup group = view.findViewById(R.id.sortChipGroup);
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            if (currentSort.equals(chip.getTag())) chip.setChecked(true);
        }

        view.findViewById(R.id.btnApplySort).setOnClickListener(v -> {
            HapticManager.mediumTap(v);
            int id = group.getCheckedChipId();
            if (id != View.NO_ID) {
                Chip selected = group.findViewById(id);
                currentSort = selected.getTag().toString();
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
        List<AppDataStore.ProductRecord> filtered = new ArrayList<>();
        for (AppDataStore.ProductRecord p : all) {
            String searchable = (p.title + " " + p.seller + " " + p.description + " " + p.category + " " + p.distance).toLowerCase(Locale.ROOT);
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
        for (String q : AppDataStore.TRENDING_SEARCHES) trending.addView(chip(q));
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
            filter();
        });
        return chip;
    }

    private boolean categoryMatches(String productCategory, String selectedCategory) {
        if (selectedCategory == null || selectedCategory.toLowerCase(Locale.ROOT).startsWith("all")) return true;
        String product = productCategory == null ? "" : productCategory.toLowerCase(Locale.ROOT);
        String selected = selectedCategory.toLowerCase(Locale.ROOT);
        if (selected.equals("tech")) return product.contains("tech") || product.contains("electronic");
        if (selected.equals("repair")) return product.contains("repair") || product.contains("service");
        if (selected.equals("home")) return product.contains("home") || product.contains("furniture");
        return product.equals(selected) || product.contains(selected) || selected.contains(product);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
