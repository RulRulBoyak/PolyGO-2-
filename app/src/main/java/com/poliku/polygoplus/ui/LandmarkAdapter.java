package com.poliku.polygoplus.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.R;
import com.poliku.polygoplus.data.AppDataStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Grid of campus landmark tiles used inside {@link LandmarkPickerSheet}.
 * Tiles show an icon, name and hint subtitle; the selected tile is tinted
 * PolyGo-blue via the selector drawables.
 */
public class LandmarkAdapter extends RecyclerView.Adapter<LandmarkAdapter.Holder> {

    public interface Listener {
        void onLandmark(String name);
    }

    public static final class Entry {
        public final String name;
        public final String subtitle;
        public final int iconRes;

        Entry(String name, String subtitle, int iconRes) {
            this.name = name;
            this.subtitle = subtitle;
            this.iconRes = iconRes;
        }
    }

    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private final Listener listener;
    private String selected;

    public LandmarkAdapter(Listener listener) {
        this.listener = listener;
        for (String name : AppDataStore.PKS_LANDMARKS) {
            if (!"Other".equalsIgnoreCase(name)) {
                all.add(entryFor(name));
            }
        }
        all.add(new Entry("Custom spot", "Type a custom campus spot", R.drawable.ic_add));
    }

    public void setSelected(String name) {
        selected = name;
        notifyDataSetChanged();
    }

    public void filter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        shown.clear();
        if (needle.isEmpty()) {
            shown.addAll(all);
        } else {
            for (Entry e : all) {
                if (e.name.toLowerCase(Locale.getDefault()).contains(needle)
                        || e.subtitle.toLowerCase(Locale.getDefault()).contains(needle)) {
                    shown.add(e);
                }
            }
        }
        notifyDataSetChanged();
    }

    private static Entry entryFor(String name) {
        String n = name == null ? "" : name;
        if (n.contains("Library")) return new Entry(n, "Books & study", R.drawable.ic_category_books);
        if (n.contains("Student")) return new Entry(n, "Clubs & services", R.drawable.ic_landmark_student_centre);
        if (n.contains("Main Hall")) return new Entry(n, "Events & ceremonies", R.drawable.ic_landmark_building);
        if (n.contains("Cafeteria")) return new Entry(n, "Food & coffee", R.drawable.ic_category_food);
        if (n.contains("Sports")) return new Entry(n, "Gym & courts", R.drawable.ic_landmark_sports);
        if (n.contains("Mosque")) return new Entry(n, "Prayer & reflection", R.drawable.ic_landmark_mosque);
        if (n.contains("Block")) return new Entry(n, "Residential block", R.drawable.ic_landmark_building);
        if (n.contains("Near")) return new Entry(n, "Main entrance meetup", R.drawable.ic_location);
        return new Entry(n, "Campus spot", R.drawable.ic_landmark_building);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_landmark_grid, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = shown.get(position);
        h.icon.setImageResource(e.iconRes);
        h.name.setText(e.name);
        h.subtitle.setText(e.subtitle);
        boolean isSelected = selected != null && selected.equalsIgnoreCase(e.name);
        h.root.setSelected(isSelected);
        h.root.setOnClickListener(v -> listener.onLandmark(e.name));
    }

    @Override
    public int getItemCount() {
        return shown.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View root;
        final ImageView icon;
        final TextView name;
        final TextView subtitle;

        Holder(@NonNull View v) {
            super(v);
            root = v;
            icon = v.findViewById(R.id.ivLandmarkIcon);
            name = v.findViewById(R.id.tvLandmarkName);
            subtitle = v.findViewById(R.id.tvLandmarkSubtitle);
        }
    }
}