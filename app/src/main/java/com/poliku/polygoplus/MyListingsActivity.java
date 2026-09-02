package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.ui.EmptyStates;

import org.json.JSONObject;

public class MyListingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_saved_items);
        ((TextView) findViewById(R.id.pageTitle)).setText("My listings");
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        AppDataStore.initialize(this);
        java.util.List<AppDataStore.ProductRecord> items = AppDataStore.getMyListings(this);
        java.util.List<JSONObject> drafts = AppDataStore.getDrafts(this);
        if (!drafts.isEmpty()) {
            ((TextView) findViewById(R.id.pageTitle)).setText("My listings · " + drafts.size() + " draft(s)");
            findViewById(R.id.pageTitle).setOnClickListener(v -> {
                Intent i = new Intent(this, EditProductActivity.class);
                i.putExtra("draft_id", drafts.get(0).optString("id"));
                startActivity(i);
            });
        }
        RecyclerView rv = findViewById(R.id.rvSaved);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        rv.setAdapter(new ProductCardAdapter(items, new ProductCardAdapter.Listener() {
            @Override
            public void onProduct(ProductCardAdapter adapter, AppDataStore.ProductRecord p, View sharedView) {
                Intent i = new Intent(MyListingsActivity.this, ProductDetailActivity.class);
                i.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, p.id);
                startActivity(i);
            }
        }));
        View empty = findViewById(R.id.tvEmpty);
        EmptyStates.bind(empty, android.R.drawable.ic_menu_edit, "You haven't published any listings",
                drafts.isEmpty() ? "Create a listing or save a draft until you are ready." : "You have unpublished drafts. Tap the title to continue editing.",
                "Create listing", v -> startActivity(new Intent(this, EditProductActivity.class)));
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
