package com.poliku.polygoplus.util;

import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.api.PolyGoApi;

/**
 * Binds a CampusEvent onto the shared event card (item_campus_event.xml).
 * Used by both the campus events list and the home carousel so the two
 * always render identically, matching the admin webapp preview.
 */
public final class EventCardBinder {

    public static final class Bindings {
        public final FrameLayout header;
        public final ImageView cover;
        public final TextView emoji;
        public final TextView date;
        public final TextView featured;
        public final TextView label;
        public final TextView title;
        public final TextView venue;
        public final TextView description;

        Bindings(View itemView) {
            header = itemView.findViewById(R.id.evHeader);
            cover = itemView.findViewById(R.id.evCover);
            emoji = itemView.findViewById(R.id.evEmoji);
            date = itemView.findViewById(R.id.evDate);
            featured = itemView.findViewById(R.id.evFeatured);
            label = itemView.findViewById(R.id.evLabel);
            title = itemView.findViewById(R.id.tvEventTitle);
            venue = itemView.findViewById(R.id.tvEventVenue);
            description = itemView.findViewById(R.id.tvEventDescription);
        }
    }

    private EventCardBinder() {
    }

    @NonNull
    public static Bindings bind(View itemView) {
        return new Bindings(itemView);
    }

    public static void apply(Bindings views, PolyGoApi.CampusEvent event, float density) {
        int accent = EventPalette.accentColor(event.theme, event.accent_color, 1);

        FrameLayout header = views.header;
        header.setBackground(EventPalette.gradient(event.theme));
        header.setClipToOutline(true);
        int radiusPx = Math.round(18 * density);
        header.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });

        int headerHeight = Math.round(96 * density);
        ViewGroup.LayoutParams params = header.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, headerHeight);
        }
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = headerHeight;
        header.setLayoutParams(params);

        boolean hasCover = event.cover_url != null && !event.cover_url.isEmpty();
        views.cover.setVisibility(hasCover ? View.VISIBLE : View.GONE);
        if (hasCover) {
            Glide.with(views.cover.getContext()).load(event.cover_url).centerCrop()
                    .into(views.cover);
        }

        boolean hasEmoji = event.emoji != null && !event.emoji.isEmpty();
        views.emoji.setVisibility(hasEmoji ? View.VISIBLE : View.GONE);
        if (hasEmoji) views.emoji.setText(event.emoji);

        views.date.setText(EventTimes.pill(event.starts_at));
        views.date.setTextColor(EventPalette.validHex(event.accent_color)
                ? accent : 0xFF0F172A);

        views.featured.setVisibility(event.isFeatured != 0 ? View.VISIBLE : View.GONE);

        boolean hasLabel = event.label != null && !event.label.isEmpty();
        views.label.setVisibility(hasLabel ? View.VISIBLE : View.GONE);
        if (hasLabel) {
            views.label.setText(event.label);
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(accent);
            pill.setCornerRadius(100 * density);
            views.label.setBackground(pill);
            views.label.setTextColor(EventPalette.contrastColor(accent));
        }
    }
}