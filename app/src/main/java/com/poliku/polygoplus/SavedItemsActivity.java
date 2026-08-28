package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.network.NetworkApi;
import com.poliku.polygoplus.ui.EmptyStates;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SavedItemsActivity extends AppCompatActivity {
    private ProductCardAdapter adapter;
    private View empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_saved_items);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        AppDataStore.initialize(this);
        empty = findViewById(R.id.tvEmpty);
        EmptyStates.bind(empty, android.R.drawable.btn_star_big_off, "You haven't saved any items",
                "Tap the star on a listing to keep it here for later.", "Explore listings",
                v -> startActivity(new android.content.Intent(this, SearchActivity.class)));
        RecyclerView rv = findViewById(R.id.rvSaved);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new ProductCardAdapter(new ArrayList<>(), (a, p) -> {
            android.content.Intent i = new android.content.Intent(this, ProductDetailActivity.class);
            i.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, p.id);
            startActivity(i);
        });
        rv.setAdapter(adapter);
        loadFavorites();
    }

    private void loadFavorites() {
        String userId = AppDataStore.userId(this);
        NetworkApi.getFavorites(userId, new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                List<AppDataStore.ProductRecord> items = new ArrayList<>();
                JSONArray list = response.optJSONArray("favorites");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject o = list.optJSONObject(i);
                        if (o != null) {
                            AppDataStore.ProductRecord p = AppDataStore.ProductRecord.fromJson(o);
                            if (p != null) items.add(p);
                        }
                    }
                }
                if (adapter != null) {
                    adapter.updateData(items);
                    empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onError(String message) {
                List<AppDataStore.ProductRecord> items = AppDataStore.getFavorites(SavedItemsActivity.this);
                if (adapter != null) {
                    adapter.updateData(items);
                    empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
        });
    }
}
