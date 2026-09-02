package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.ui.EmptyStates;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SellerProfileActivity extends AppCompatActivity {
    public static final String EXTRA_SELLER_NAME = "seller_name";
    public static final String EXTRA_SELLER_ID = "seller_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seller_profile);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        String seller = getIntent().getStringExtra(EXTRA_SELLER_NAME);
        String sellerId = getIntent().getStringExtra(EXTRA_SELLER_ID);
        if (seller == null || seller.isEmpty()) seller = "Campus seller";

        ((TextView) findViewById(R.id.tvSellerName)).setText(seller);
        List<AppDataStore.ProductRecord> listings = AppDataStore.getListingsBySeller(this, seller, sellerId);
        List<AppDataStore.ProductRecord> active = new ArrayList<>();
        for (AppDataStore.ProductRecord p : listings) if (p.available) active.add(p);

        float rating = AppDataStore.averageRatingForSeller(this, seller);
        int sold = AppDataStore.countSoldBySeller(this, seller, sellerId);
        ((TextView) findViewById(R.id.tvSellerRating)).setText(rating <= 0 ? "New" : String.format(Locale.US, "%.1f", rating));
        ((TextView) findViewById(R.id.tvItemsSold)).setText(String.valueOf(Math.max(sold, 0)));
        ((TextView) findViewById(R.id.tvActiveCount)).setText(String.valueOf(active.size()));

        RecyclerView rv = findViewById(R.id.rvSellerListings);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        rv.setAdapter(new ProductCardAdapter(active, new ProductCardAdapter.Listener() {
            @Override
            public void onProduct(ProductCardAdapter adapter, AppDataStore.ProductRecord p, View sharedView) {
                Intent i = new Intent(SellerProfileActivity.this, ProductDetailActivity.class);
                i.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, p.id);
                startActivity(i);
            }
        }));
        View emptyListings = findViewById(R.id.emptyListings);
        emptyListings.setVisibility(active.isEmpty() ? View.VISIBLE : View.GONE);
        EmptyStates.bind(emptyListings, android.R.drawable.ic_menu_gallery, "No active listings",
                seller + " has no items for sale right now.", "Browse campus", v -> {
                    startActivity(new Intent(this, SearchActivity.class));
                });

        LinearLayout reviewList = findViewById(R.id.reviewList);
        List<AppDataStore.ReviewRecord> reviews = AppDataStore.getReviewsForSeller(this, seller);
        View emptyReviews = findViewById(R.id.emptyReviews);
        if (reviews.isEmpty()) {
            emptyReviews.setVisibility(View.VISIBLE);
            EmptyStates.bind(emptyReviews, android.R.drawable.star_off, "No reviews yet",
                    "Buyers can leave a rating after a successful meetup.", null, null);
        } else {
            emptyReviews.setVisibility(View.GONE);
            for (AppDataStore.ReviewRecord review : reviews) {
                TextView row = new TextView(this);
                row.setPadding(0, 16, 0, 16);
                row.setTextColor(getResources().getColor(R.color.airbnb_ink, getTheme()));
                row.setTextSize(15);
                String stars = "★★★★★".substring(0, Math.max(1, Math.min(5, review.stars)));
                row.setText(stars + "  " + review.reviewer + "\n" + review.comment);
                reviewList.addView(row);
            }
        }

        String finalSeller = seller;
        String finalSellerId = sellerId;
        findViewById(R.id.btnReportUser).setOnClickListener(v -> {
            Intent i = new Intent(this, ReportActivity.class);
            i.putExtra(ReportActivity.EXTRA_TARGET_TYPE, "user");
            i.putExtra(ReportActivity.EXTRA_TARGET_ID, finalSellerId);
            i.putExtra(ReportActivity.EXTRA_TARGET_NAME, finalSeller);
            startActivity(i);
        });
    }
}
