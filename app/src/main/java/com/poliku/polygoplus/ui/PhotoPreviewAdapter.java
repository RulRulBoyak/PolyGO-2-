package com.poliku.polygoplus.ui;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.R;

import java.util.ArrayList;
import java.util.List;

public class PhotoPreviewAdapter extends RecyclerView.Adapter<PhotoPreviewAdapter.Holder> {

    public interface Listener {
        void onRemove(int position);
    }

    private final List<Uri> uris = new ArrayList<>();
    private final Listener listener;

    public PhotoPreviewAdapter(Listener listener) {
        this.listener = listener;
    }

    public void updateData(List<Uri> newUris) {
        uris.clear();
        if (newUris != null) uris.addAll(newUris);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo_preview, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.img.setImageURI(uris.get(position));
        holder.btnRemove.setOnClickListener(v -> listener.onRemove(position));
    }

    @Override
    public int getItemCount() {
        return uris.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView img;
        final ImageButton btnRemove;

        Holder(View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.imgPreview);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}
