package com.poliku.polygoplus.ui;

import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;

import androidx.annotation.NonNull;
import androidx.annotation.AnimRes;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.R;

/**
 * Base fragment for common UI logic across the app.
 */
public abstract class BaseFragment extends Fragment {

    /**
     * Plays a one-shot entrance animation on a list. Safe to call right after
     * the adapter is attached; it animates once and does not re-run on updates.
     */
    protected void animateListEntrance(@NonNull RecyclerView list, @AnimRes int animRes) {
        if (list == null) return;
        LayoutAnimationController controller = AnimationUtils.loadLayoutAnimation(
                requireContext(), animRes);
        list.setLayoutAnimation(controller);
        list.scheduleLayoutAnimation();
    }

    /**
     * Convenience: cascade entrance for standard content lists.
     */
    protected void animateListEntrance(@NonNull RecyclerView list) {
        animateListEntrance(list, R.anim.layout_cascade);
    }
}