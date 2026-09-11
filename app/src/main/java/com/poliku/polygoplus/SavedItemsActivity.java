package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.ui.EmptyStates;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;

@AndroidEntryPoint
public class SavedItemsActivity extends AppCompatActivity {
    @Inject PolyGoRepository polyGoRepository;
    private ProductCardAdapter adapter;
    private View empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_saved_items);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        AppDataStore.initialize(this);
        empty = findViewById(R.id.tvEmpty);
        EmptyStates.bind(empty, R.drawable.ic_star_outline, "You haven't saved any items",
                "Tap the star on a listing to keep it here for later.", "Explore listings",
                v -> startActivity(new Intent(this, SearchActivity.class)));
        RecyclerView rv = findViewById(R.id.rvSaved);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new ProductCardAdapter(new ArrayList<>(), new ProductCardAdapter.Listener() {
            @Override
            public void onProduct(ProductCardAdapter adapter, ListingEntity p, View sharedView) {
                Intent i = new Intent(SavedItemsActivity.this, ProductDetailActivity.class);
                i.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, p.id);
                startActivity(i);
            }

            @Override
            public void onDataChanged() {
                loadFavorites();
            }
        });
        rv.setAdapter(adapter);
        loadFavorites();
    }

    private void loadFavorites() {
        String userId = AppDataStore.userId(this);
        polyGoRepository.getFavorites(userId, new Callback<PolyGoApi.ListingsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.ListingsResponse> call, Response<PolyGoApi.ListingsResponse> response) {
                List<ListingEntity> items = new ArrayList<>();
                PolyGoApi.ListingsResponse body = response.body();
                if (body != null && body.listings != null) {
                    for (PolyGoApi.Listing l : body.listings) {
                        AppDataStore.ProductRecord p = AppDataStore.ProductRecord.fromListing(l, userId);
                        if (p != null) items.add(p.toEntity());
                    }
                }
                if (adapter != null) {
                    adapter.updateData(items);
                    empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.ListingsResponse> call, Throwable t) {
                List<ListingEntity> items = AppDataStore.listingsToEntities(AppDataStore.getFavorites(SavedItemsActivity.this));
                if (adapter != null) {
                    adapter.updateData(items);
                    empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
        });
    }
}
