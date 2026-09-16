package com.poliku.polygoplus.data;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.poliku.polygoplus.LoginActivity;
import com.poliku.polygoplus.R;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.palette.graphics.Palette;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.poliku.polygoplus.databinding.ItemProductCardBinding;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.RelativeTimeFormatter;

import com.poliku.polygoplus.data.local.entity.ListingEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProductCardAdapter extends RecyclerView.Adapter<ProductCardAdapter.Holder> {
    public interface Listener {
        void onProduct(ProductCardAdapter adapter, ListingEntity product, View sharedView);
        default void onDataChanged() {}
    }

    private final List<ListingEntity> products = new ArrayList<>();
    private final Listener listener;
    private int itemWidthDp;

    public ProductCardAdapter(List<ListingEntity> data, Listener listener) {
        if (data != null) products.addAll(data);
        this.listener = listener;
    }

    /** Fixed item width in dp for horizontal rails; 0 keeps the default match-parent width. */
    public void setItemWidthDp(int itemWidthDp) {
        this.itemWidthDp = itemWidthDp;
    }

    public void updateData(List<ListingEntity> newData) {
        List<ListingEntity> safe = newData == null ? new ArrayList<>() : newData;
        ProductDiffCallback diffCallback = new ProductDiffCallback(new ArrayList<>(products), safe);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        products.clear();
        products.addAll(safe);
        diffResult.dispatchUpdatesTo(this);
    }

    public void notifyStateChanged(String productId) {
        for (int i = 0; i < products.size(); i++) {
            if (Objects.equals(productId, products.get(i).id)) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    private static class ProductDiffCallback extends DiffUtil.Callback {
        private final List<ListingEntity> oldList;
        private final List<ListingEntity> newList;

        ProductDiffCallback(List<ListingEntity> oldList, List<ListingEntity> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return Objects.equals(oldList.get(oldItemPosition).id, newList.get(newItemPosition).id);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            ListingEntity oldItem = oldList.get(oldItemPosition);
            ListingEntity newItem = newList.get(newItemPosition);
            return Objects.equals(oldItem.title, newItem.title) &&
                   Objects.equals(oldItem.price, newItem.price) &&
                   oldItem.available == newItem.available;
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Holder holder = new Holder(ItemProductCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        if (itemWidthDp > 0) {
            ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
            lp.width = Math.round(itemWidthDp * parent.getResources().getDisplayMetrics().density);
            holder.itemView.setLayoutParams(lp);
        }
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ListingEntity p = products.get(position);
        h.binding.textViewTitle.setText(p.title);
        h.binding.textViewPrice.setText("RM " + p.price);
        String ratingText = "★ " + p.rating;
        if (p.reviewCount != null && !p.reviewCount.isEmpty() && !"0".equals(p.reviewCount)) {
            ratingText += " (" + p.reviewCount + ")";
        }
        h.binding.textViewRating.setText(ratingText);
        h.binding.textViewDistance.setText(p.archived
                ? h.itemView.getContext().getString(R.string.status_archived) : p.distance);
        String postedTime = RelativeTimeFormatter.format(h.itemView.getContext(), p.postedAt);
        if (postedTime.isEmpty()) {
            h.binding.textViewPostedTime.setVisibility(View.GONE);
            h.binding.textViewMetaSep.setVisibility(View.GONE);
        } else {
            h.binding.textViewPostedTime.setText(postedTime);
            h.binding.textViewPostedTime.setVisibility(View.VISIBLE);
            h.binding.textViewMetaSep.setVisibility(View.VISIBLE);
        }
        int photoCount = photoCount(p.imageUrl);
        if (photoCount > 1) {
            h.binding.photoCountBadge.setText(String.valueOf(photoCount));
            h.binding.photoCountBadge.setContentDescription(
                    h.itemView.getContext().getString(R.string.product_photos_desc, photoCount));
            h.binding.photoCountBadge.setVisibility(View.VISIBLE);
        } else {
            h.binding.photoCountBadge.setVisibility(View.GONE);
        }
        boolean inactive = !p.available || p.archived;
        h.binding.textViewStatusBadge.setVisibility(inactive ? View.VISIBLE : View.GONE);
        h.binding.textViewStatusBadge.setText(h.itemView.getContext().getString(
                p.archived ? R.string.status_archived : R.string.status_sold));

        // Load Image using Glide (supports both LOCAL and WEB URLs).
        // Grid cards load the 200px thumbnail when a web upload exists, falling
        // back to the full image for older records that predate thumbnails.
        String fullUrl = p.imageUrl == null ? "" : p.imageUrl;
        String thumbUrl = toThumbUrl(fullUrl);

        RequestBuilder<Drawable> requestBuilder = Glide.with(h.itemView.getContext())
                .load(thumbUrl.isEmpty() ? R.drawable.bg_product_home : thumbUrl)
                .placeholder(R.drawable.bg_product_home)
                .error(R.drawable.bg_product_home)
                .override(400, 400)
                .centerCrop();

        if (!thumbUrl.isEmpty() && !thumbUrl.equals(fullUrl)) {
            requestBuilder.error(
                    Glide.with(h.itemView.getContext())
                            .load(fullUrl)
                            .placeholder(R.drawable.bg_product_home)
                            .error(R.drawable.bg_product_home)
                            .override(400, 400)
                            .centerCrop());
        }

        requestBuilder
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        if (resource instanceof BitmapDrawable) {
                            Bitmap bitmap = ((BitmapDrawable) resource).getBitmap();
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

        h.binding.cardView.setAlpha((!p.available || p.archived) ? 0.55f : 1f);
        h.binding.cardView.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            listener.onProduct(this, p, h.binding.imageView);
        });
        h.binding.buttonFavorite.setSelected(AppDataStore.isFavorite(vContext(h), p.id));
        h.binding.buttonFavorite.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (!AppDataStore.isLoggedIn(v.getContext())) {
                Toast.makeText(v.getContext(), R.string.toast_login_required_save_favorites, Toast.LENGTH_SHORT).show();
                v.getContext().startActivity(new Intent(v.getContext(), LoginActivity.class));
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
                Snackbar snackbar = Snackbar.make(v, R.string.snack_item_removed_from_favorites, Snackbar.LENGTH_LONG);
                snackbar.setAction(v.getContext().getString(R.string.action_undo), view -> {
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

    private Context vContext(Holder h) {
        return h.itemView.getContext();
    }

    private static int photoCount(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return 0;
        }
        int count = 0;
        String[] parts = imageUrl.split("\\|", -1);
        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static String toThumbUrl(String url) {
        int uploadMarker = url.indexOf("/uploads/");
        if (uploadMarker < 0) {
            return url;
        }
        String filePart = url.substring(url.lastIndexOf('/') + 1);
        String base = filePart.contains(".")
                ? filePart.substring(0, filePart.lastIndexOf('.'))
                : filePart;
        return url.substring(0, url.lastIndexOf('/')) + "/thumbs/" + base + ".thumb.jpg";
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
