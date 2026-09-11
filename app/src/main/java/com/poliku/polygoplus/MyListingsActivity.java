package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.ui.EmptyStates;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class MyListingsActivity extends AppCompatActivity {
    @Inject PolyGoRepository polyGoRepository;
    private ProductCardAdapter adapter;
    private View empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_saved_items);
        ((TextView) findViewById(R.id.pageTitle)).setText("My listings");
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        RecyclerView rv = findViewById(R.id.rvSaved);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new ProductCardAdapter(new ArrayList<>(), new ProductCardAdapter.Listener() {
            @Override
            public void onProduct(ProductCardAdapter adapter, ListingEntity p, View sharedView) {
                Intent i = new Intent(MyListingsActivity.this, ProductDetailActivity.class);
                i.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, p.id);
                startActivity(i);
            }

            @Override
            public void onDataChanged() {
                load();
            }
        });
        rv.setAdapter(adapter);
        empty = findViewById(R.id.tvEmpty);
        EmptyStates.bind(empty, R.drawable.ic_edit_square, "You haven't published any listings",
                "Create a listing or save a draft until you are ready.",
                "Create listing", v -> startActivity(new Intent(this, EditProductActivity.class)));
        load();
    }

    private void load() {
        polyGoRepository.getMyListings(new Callback<PolyGoApi.ListingsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.ListingsResponse> call, Response<PolyGoApi.ListingsResponse> response) {
                List<ListingEntity> items = new ArrayList<>();
                PolyGoApi.ListingsResponse body = response.body();
                if (body != null && body.listings != null) {
                    for (PolyGoApi.Listing l : body.listings) {
                        AppDataStore.ProductRecord p = AppDataStore.ProductRecord.fromListing(l, AppDataStore.userId(MyListingsActivity.this));
                        if (p != null) {
                            ListingEntity e = p.toEntity();
                            e.archived = l.archivedAt != null && !l.archivedAt.isEmpty();
                            items.add(e);
                        }
                    }
                }
                render(items);
            }

            @Override
            public void onFailure(Call<PolyGoApi.ListingsResponse> call, Throwable t) {
                render(AppDataStore.getMyListings(MyListingsActivity.this).stream()
                        .map(AppDataStore.ProductRecord::toEntity)
                        .collect(java.util.stream.Collectors.toList()));
            }
        });
    }

    private void render(List<ListingEntity> items) {
        if (adapter != null) adapter.updateData(items);
        List<JSONObject> drafts = AppDataStore.getDrafts(this);
        String title = "My listings";
        if (!drafts.isEmpty()) title += " · " + drafts.size() + " draft(s)";
        long archived = items.stream().filter(i -> i.archived).count();
        if (archived > 0) title += " · " + archived + " archived";
        ((TextView) findViewById(R.id.pageTitle)).setText(title);
        findViewById(R.id.pageTitle).setOnClickListener(v -> {
            if (!drafts.isEmpty()) {
                Intent i = new Intent(this, EditProductActivity.class);
                i.putExtra("draft_id", drafts.get(0).optString("id"));
                startActivity(i);
            }
        });
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }
}