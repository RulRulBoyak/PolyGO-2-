package com.poliku.polygoplus.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.poliku.polygoplus.R;

import java.util.ArrayList;
import java.util.List;

public class CarouselAdapter extends RecyclerView.Adapter<CarouselAdapter.Holder> {

    public interface Listener {
        void onClick(int position);
    }

    private final List<String> images = new ArrayList<>();
    private final int fallbackRes;
    private final Listener listener;

    public CarouselAdapter(int fallbackRes, Listener listener) {
        this.fallbackRes = fallbackRes;
        this.listener = listener;
    }

    public void submit(List<String> newImages) {
        images.clear();
        if (newImages != null) images.addAll(newImages);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ZoomImageView img = new ZoomImageView(parent.getContext());
        img.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return new Holder(img);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        String url = images.get(position);
        Object source = (url == null || url.isEmpty()) ? fallbackRes : url;

        Glide.with(holder.itemView.getContext())
                .load(source)
                .placeholder(R.drawable.bg_product_home)
                .error(R.drawable.bg_product_home)
                .into((ImageView) holder.itemView);
                
        holder.itemView.setOnClickListener(v -> listener.onClick(position));
    }

    @Override
    public int getItemCount() {
        return images.isEmpty() ? 1 : images.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        Holder(View itemView) {
            super(itemView);
        }
    }
}
