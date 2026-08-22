package com.poliku.polygoplus.data;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.databinding.ItemProductCardBinding;

import java.util.ArrayList;
import java.util.List;

public class ProductCardAdapter extends RecyclerView.Adapter<ProductCardAdapter.Holder> {
    public interface Listener {
        void onProduct(ProductCardAdapter adapter, AppDataStore.ProductRecord product);
    }

    private final List<AppDataStore.ProductRecord> products = new ArrayList<>();
    private final Listener listener;

    public ProductCardAdapter(List<AppDataStore.ProductRecord> data, Listener listener) {
        products.addAll(data);
        this.listener = listener;
    }

    public void updateData(List<AppDataStore.ProductRecord> newData) {
        products.clear();
        if (newData != null) products.addAll(newData);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemProductCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AppDataStore.ProductRecord p = products.get(position);
        h.binding.textViewTitle.setText(p.title);
        h.binding.textViewPrice.setText("RM " + p.price);
        h.binding.textViewRating.setText("★ " + p.rating);
        h.binding.textViewDistance.setText(p.distance);
        if (p.imageUri.isEmpty()) h.binding.imageView.setImageResource(p.imageRes);
        else h.binding.imageView.setImageURI(android.net.Uri.parse(p.imageUri));
        h.binding.cardView.setAlpha(p.available ? 1f : 0.55f);
        h.binding.cardView.setOnClickListener(v -> listener.onProduct(this, p));
        h.binding.buttonFavorite.setSelected(AppDataStore.isFavorite(vContext(h), p.id));
        h.binding.buttonFavorite.setOnClickListener(v -> {
            AppDataStore.toggleFavorite(v.getContext(), p.id);
            v.setSelected(AppDataStore.isFavorite(v.getContext(), p.id));
        });
    }

    private android.content.Context vContext(Holder h) {
        return h.itemView.getContext();
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    public static class Holder extends RecyclerView.ViewHolder {
        final ItemProductCardBinding binding;

        Holder(ItemProductCardBinding b) {
            super(b.getRoot());
            binding = b;
        }
    }
}
