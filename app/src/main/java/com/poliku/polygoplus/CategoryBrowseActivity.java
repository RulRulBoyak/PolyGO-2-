package com.poliku.polygoplus;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.poliku.polygoplus.ui.BaseActivity;
import dagger.hilt.android.AndroidEntryPoint;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.ui.UiUtils;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.PolyGoRepository;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class CategoryBrowseActivity extends BaseActivity {
    @Inject PolyGoRepository repository;
    private final List<PolyGoApi.Category> categories = new ArrayList<>();
    private RecyclerView.Adapter<Holder> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_browse);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        RecyclerView rv = findViewById(R.id.rvCategories);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new RecyclerView.Adapter<Holder>() {
            @NonNull
            @Override
            public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_tile, parent, false);
                return new Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull Holder holder, int position) {
                PolyGoApi.Category category = categories.get(position);
                holder.name.setText(category.name);
                holder.icon.setImageResource(UiUtils.categoryIconKey(category.icon_res));
                holder.icon.setImageTintList(ColorStateList.valueOf(
                        getColor(UiUtils.categoryColor(category.name))));
                holder.badge.setBackgroundTintList(ColorStateList.valueOf(
                        getColor(UiUtils.categoryTint(category.name))));
                holder.itemView.setOnClickListener(v -> {
                    Intent i = new Intent(CategoryBrowseActivity.this, SearchActivity.class);
                    i.putExtra(SearchActivity.EXTRA_CATEGORY, category.name);
                    startActivity(i);
                });
            }

            @Override
            public int getItemCount() {
                return categories.size();
            }
        };
        rv.setAdapter(adapter);
        repository.getCategories(new Callback<PolyGoApi.CategoryResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.CategoryResponse> call,
                                   Response<PolyGoApi.CategoryResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().categories != null) {
                    categories.clear();
                    categories.addAll(response.body().categories);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.CategoryResponse> call, Throwable error) {
                // The empty screen is preferable to stale hard-coded categories.
            }
        });
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final FrameLayout badge;
        final TextView name;

        Holder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.imgCategory);
            badge = itemView.findViewById(R.id.layoutIconBadge);
            name = itemView.findViewById(R.id.tvCategory);
        }
    }
}
