package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class CategoryBrowseActivity extends AppCompatActivity {
    private static final String[] NAMES = {"Food", "Drink", "Tech", "Books", "Repair", "Fashion", "Home", "Services"};
    private static final int[] ICONS = {
        R.drawable.ic_category_food, R.drawable.ic_category_drink, R.drawable.ic_category_tech,
        R.drawable.ic_category_books, R.drawable.ic_category_repair, R.drawable.ic_category_fashion,
        R.drawable.ic_category_home, R.drawable.ic_category_repair
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_browse);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        RecyclerView rv = findViewById(R.id.rvCategories);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        rv.setAdapter(new RecyclerView.Adapter<Holder>() {
            @NonNull
            @Override
            public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_tile, parent, false);
                return new Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull Holder holder, int position) {
                holder.name.setText(NAMES[position]);
                holder.icon.setImageResource(ICONS[position]);
                holder.itemView.setOnClickListener(v -> {
                    Intent i = new Intent(CategoryBrowseActivity.this, SearchActivity.class);
                    i.putExtra(SearchActivity.EXTRA_CATEGORY, NAMES[position]);
                    startActivity(i);
                });
            }

            @Override
            public int getItemCount() {
                return NAMES.length;
            }
        });
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;

        Holder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.imgCategory);
            name = itemView.findViewById(R.id.tvCategory);
        }
    }
}
