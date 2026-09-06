package com.poliku.polygoplus.data;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.poliku.polygoplus.R;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.palette.graphics.Palette;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.poliku.polygoplus.databinding.ItemProductCardBinding;
import com.poliku.polygoplus.ui.HapticManager;

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
        ProductDiffCallback diffCallback = new ProductDiffCallback(this.products, newData);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        
        products.clear();
        if (newData != null) products.addAll(newData);
        diffResult.dispatchUpdatesTo(this);
    }

    private static class ProductDiffCallback extends DiffUtil.Callback {
        private final List<AppDataStore.ProductRecord> oldList;
        private final List<AppDataStore.ProductRecord> newList;

        ProductDiffCallback(List<AppDataStore.ProductRecord> oldList, List<AppDataStore.ProductRecord> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).id.equals(newList.get(newItemPosition).id);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            AppDataStore.ProductRecord oldItem = oldList.get(oldItemPosition);
            AppDataStore.ProductRecord newItem = newList.get(newItemPosition);
            return oldItem.title.equals(newItem.title) &&
                   oldItem.price.equals(newItem.price) &&
                   oldItem.available == newItem.available;
        }
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
        List<String> images = p.imageList();
        Object imageSource = (images.isEmpty()) 
                ? (p.imageRes != 0 ? p.imageRes : R.drawable.bg_product_home) 
                : images.get(0);

        Glide.with(h.itemView.getContext())
                .load(imageSource)
                .placeholder(R.drawable.bg_product_home)
                .error(R.drawable.bg_product_home)
                .centerCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@androidx.annotation.Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        if (resource instanceof BitmapDrawable) {
                            android.graphics.Bitmap bitmap = ((BitmapDrawable) resource).getBitmap();
                            Palette.from(bitmap).generate(palette -> {
                                if (palette != null) {
                                    int color = palette.getMutedColor(0xFFF5F5F5);
                                    h.binding.cardView.setCardBackgroundColor(color);
                                    // Rule 3.3: Set subtle scrim for text readability if needed
                                }
                            });
                        }
                        return false;
                    }
                })
                .into(h.binding.imageView);

        // Rule 3.1: Visual Continuity (Shared Element Transition)
        ViewCompat.setTransitionName(h.binding.imageView, "product_image_" + p.id);

        h.binding.cardView.setAlpha(p.available ? 1f : 0.55f);
        h.binding.cardView.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            listener.onProduct(this, p, h.binding.imageView);
        });
        h.binding.buttonFavorite.setSelected(AppDataStore.isFavorite(vContext(h), p.id));
        h.binding.buttonFavorite.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (!AppDataStore.isLoggedIn(v.getContext())) {
                android.widget.Toast.makeText(v.getContext(), "Login required to save favorites", android.widget.Toast.LENGTH_SHORT).show();
                v.getContext().startActivity(new android.content.Intent(v.getContext(), com.poliku.polygoplus.LoginActivity.class));
                return;
            }
            
            // OPTIMISTIC UPDATE: Change state instantly for perception of speed
            boolean becomingFavorite = !v.isSelected();
            v.setSelected(becomingFavorite);
            
            if (becomingFavorite) HapticManager.success(v.getContext());
            
            // Background processing
            AppDataStore.toggleFavorite(v.getContext(), p.id);
            
            // Rule 3.3: Contextual Undo & Snackbar
            if (!becomingFavorite) {
                Snackbar snackbar = Snackbar.make(v, "Item removed from favorites", Snackbar.LENGTH_LONG);
                snackbar.setAction("UNDO", view -> {
                    HapticManager.lightTap(view);
                    AppDataStore.toggleFavorite(vContext(h), p.id);
                    v.setSelected(true);
                    listener.onDataChanged();
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
