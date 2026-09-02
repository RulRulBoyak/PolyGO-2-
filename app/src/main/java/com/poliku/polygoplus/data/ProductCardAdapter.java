package com.poliku.polygoplus.data;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import com.bumptech.glide.Glide;
import com.poliku.polygoplus.R;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.poliku.polygoplus.databinding.ItemProductCardBinding;

import java.util.ArrayList;
import java.util.List;

public class ProductCardAdapter extends RecyclerView.Adapter<ProductCardAdapter.Holder> {
    public interface Listener {
        void onProduct(ProductCardAdapter adapter, AppDataStore.ProductRecord product, View sharedView);
        default void onDataChanged() {}
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

        // Load Image using Glide (supports both LOCAL and WEB URLs)
        Object imageSource = p.imageUri.isEmpty() ? (p.imageRes != 0 ? p.imageRes : R.drawable.bg_product_home) : p.imageUri;

        Glide.with(h.itemView.getContext())
                .load(imageSource)
                .placeholder(R.drawable.bg_product_home)
                .error(R.drawable.bg_product_home)
                .centerCrop()
                .into(h.binding.imageView);

        // Rule 3.1: Visual Continuity (Shared Element Transition)
        ViewCompat.setTransitionName(h.binding.imageView, "product_image_" + p.id);

        h.binding.cardView.setAlpha(p.available ? 1f : 0.55f);
        h.binding.cardView.setOnClickListener(v -> listener.onProduct(this, p, h.binding.imageView));
        h.binding.buttonFavorite.setSelected(AppDataStore.isFavorite(vContext(h), p.id));
        h.binding.buttonFavorite.setOnClickListener(v -> {
            if (!AppDataStore.isLoggedIn(v.getContext())) {
                android.widget.Toast.makeText(v.getContext(), "Login required to save favorites", android.widget.Toast.LENGTH_SHORT).show();
                v.getContext().startActivity(new android.content.Intent(v.getContext(), com.poliku.polygoplus.LoginActivity.class));
                return;
            }
            
            // OPTIMISTIC UPDATE: Change state instantly for perception of speed
            boolean becomingFavorite = !v.isSelected();
            v.setSelected(becomingFavorite);
            
            // Background processing
            AppDataStore.toggleFavorite(v.getContext(), p.id);
            
            // Rule 3.3: Contextual Undo & Snackbar
            if (!becomingFavorite) {
                Snackbar snackbar = Snackbar.make(v, "Item removed from favorites", Snackbar.LENGTH_LONG);
                snackbar.setAction("UNDO", view -> {
                    AppDataStore.toggleFavorite(vContext(h), p.id);
                    v.setSelected(true);
                    listener.onDataChanged();
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                });
                snackbar.addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar transientBottomBar, int event) {
                        if (event != DISMISS_EVENT_ACTION) {
                            listener.onDataChanged();
                        }
                    }
                });
                snackbar.setActionTextColor(v.getContext().getResources().getColor(R.color.pks_blue_variant));
                snackbar.show();
            }

            // Subtle Scale Animation for feedback
            v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(100).withEndAction(() -> 
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            ).start();

            // Rule 3.1: Haptic Feedback for tactile response
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
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
