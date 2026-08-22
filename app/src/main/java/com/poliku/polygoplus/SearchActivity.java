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

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.network.NetworkApi;

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
    private TextView count, empty;

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
        adapter = new ProductCardAdapter(new ArrayList<>(), (a, p) -> {
            android.content.Intent i = new android.content.Intent(this, ProductDetailActivity.class);
            i.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, p.id);
            startActivity(i);
        });
        rv.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            public void onTextChanged(CharSequence s, int st, int b, int c) {
                filter();
            }

            public void afterTextChanged(Editable e) {
            }
        });
        category.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                filter();
            }

            public void onNothingSelected(android.widget.AdapterView<?> p) {
            }
        });
        reloadListings();
    }

    @Override protected void onResume() {
        super.onResume();
        if (search != null) reloadListings();
    }

    private void reloadListings() {
        String currentUserId = AppDataStore.userId(this);
        NetworkApi.getListings(new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                all.clear();
                JSONArray list = response.optJSONArray("listings");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject o = list.optJSONObject(i);
                        if (o != null) {
                            AppDataStore.ProductRecord p = AppDataStore.ProductRecord.fromJson(o);
                            if (p != null) {
                                boolean isOwner = p.ownerId.equals(currentUserId);
                                all.add(isOwner ? p.withOwnerStatus(true) : p);
                            }
                        }
                    }
                }
                filter();
            }

            @Override
            public void onError(String message) {
                // Fallback to local if server fails or handle error
                all.clear();
                all.addAll(AppDataStore.getListings(SearchActivity.this));
                filter();
            }
        });
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
        if (adapter != null) {
            adapter.updateData(filtered);
        }
        if (count != null) count.setText(filtered.size() + " listings");
        if (empty != null) empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
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
}
