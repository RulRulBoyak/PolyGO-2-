package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;
import dagger.hilt.android.AndroidEntryPoint;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ServicePortfolioActivity extends BaseActivity {

    @Inject PolyGoRepository polyGoRepository;

    private RecyclerView rv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_portfolio);

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        rv = findViewById(R.id.rvPortfolio);
        rv.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        rv.setAdapter(new PortfolioAdapter(new ArrayList<>()));

        loadServerServices();
    }

    private void loadServerServices() {
        final List<PortfolioItem> services = new ArrayList<>();
        polyGoRepository.getListings(0, 40, "new", new Callback<PolyGoApi.ListingsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.ListingsResponse> call, Response<PolyGoApi.ListingsResponse> response) {
                PolyGoApi.ListingsResponse body = response.body();
                if (response.isSuccessful() && body != null && body.listings != null) {
                    for (PolyGoApi.Listing l : body.listings) {
                        if (!l.available) continue;
                        if (isServiceCategory(l.category)) {
                            services.add(new PortfolioItem(
                                    l.seller == null || l.seller.isEmpty() ? "Campus seller" : l.seller,
                                    l.title == null ? "" : l.title,
                                    l.description == null ? "" : l.description,
                                    l.image_url,
                                    l.id));
                        }
                    }
                }
                runOnUiThread(() -> render(services));
            }

            @Override
            public void onFailure(Call<PolyGoApi.ListingsResponse> call, Throwable t) {
                runOnUiThread(() -> render(services));
            }
        });
    }

    private boolean isServiceCategory(String category) {
        if (category == null) return false;
        Set<String> serviceCategories = new HashSet<>(Arrays.asList(
                "services", "repair", "delivery", "laundry", "tutoring", "coaching",
                "photography", "videography", "design", "tailoring", "cleaning"));
        return serviceCategories.contains(category.toLowerCase(Locale.ROOT));
    }

    private void render(List<PortfolioItem> items) {
        if (isFinishing() || isDestroyed()) return;
        rv.setAdapter(new PortfolioAdapter(items));
    }

    static class PortfolioItem {
        String owner, title, desc, imageUrl, listingId;
        PortfolioItem(String owner, String title, String desc, String imageUrl, String listingId) {
            this.owner = owner;
            this.title = title;
            this.desc = desc;
            this.imageUrl = imageUrl;
            this.listingId = listingId;
        }
    }

    private class PortfolioAdapter extends RecyclerView.Adapter<PortfolioAdapter.Holder> {
        private final List<PortfolioItem> items;

        PortfolioAdapter(List<PortfolioItem> i) {
            items = i;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_portfolio_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            PortfolioItem item = items.get(position);
            h.name.setText(item.owner);
            h.title.setText(item.title);
            h.desc.setText(item.desc);
            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                Glide.with(h.image.getContext()).load(item.imageUrl).centerCrop().into(h.image);
            }
            h.itemView.setOnClickListener(v -> {
                HapticManager.swell(v.getContext());
                if (item.listingId != null) {
                    Intent detail = new Intent(ServicePortfolioActivity.this, ProductDetailActivity.class);
                    detail.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, item.listingId);
                    startActivity(detail);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            TextView name, title, desc;
            ImageView image;
            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.tvCreatorName);
                title = v.findViewById(R.id.tvServiceTitle);
                desc = v.findViewById(R.id.tvServiceDescription);
                image = v.findViewById(R.id.ivPortfolioImage);
            }
        }
    }
}