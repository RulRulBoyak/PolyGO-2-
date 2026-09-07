package com.poliku.polygoplus.ui;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.poliku.polygoplus.R;

public final class EmptyStates {
    private EmptyStates() {
    }

    public static void bind(View root, int iconRes, String title, String subtitle, String actionLabel, View.OnClickListener action) {
        if (root == null) return;
        ImageView icon = root.findViewById(R.id.emptyIcon);
        TextView titleView = root.findViewById(R.id.emptyTitle);
        TextView subtitleView = root.findViewById(R.id.emptySubtitle);
        MaterialButton button = root.findViewById(R.id.emptyAction);
        if (icon != null) icon.setImageResource(iconRes);
        if (titleView != null) titleView.setText(title);
        if (subtitleView != null) subtitleView.setText(subtitle);
        if (button == null) return;
        if (actionLabel == null || actionLabel.isEmpty()) {
            button.setVisibility(View.GONE);
        } else {
            button.setVisibility(View.VISIBLE);
            button.setText(actionLabel);
            button.setOnClickListener(action);
        }

        // Duolingo-style "Bobbing" animation for the empty icon
        if (icon != null) {
            icon.animate()
                .translationYBy(-20f)
                .setDuration(1500)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        animateBobbing(icon, 20f);
                    }
                }).start();
        }
    }

    private static void animateBobbing(final View view, final float amount) {
        view.animate()
            .translationYBy(amount)
            .setDuration(1500)
            .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction(() -> animateBobbing(view, -amount))
            .start();
    }
}
