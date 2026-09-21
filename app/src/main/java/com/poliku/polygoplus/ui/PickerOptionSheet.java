package com.poliku.polygoplus.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.poliku.polygoplus.R;

import java.util.Collections;
import java.util.List;

/**
 * Modal M3 bottom sheet for single-choice selection from a flat list of options
 * (e.g. listing Condition, product Category). Highlights the current selection
 * and returns the chosen label through {@link OnPicked}.
 */
public final class PickerOptionSheet extends BottomSheetDialogFragment {

    public interface OnPicked {
        void onPicked(@NonNull String label);
    }

    public static final class Option {
        @NonNull public final String label;
        @Nullable public final String description;
        public final int iconRes;

        public Option(@NonNull String label, @Nullable String description, int iconRes) {
            this.label = label;
            this.description = description;
            this.iconRes = iconRes;
        }
    }

    private static final String ARG_TITLE = "title";
    private static final String ARG_SUBTITLE = "subtitle";
    private static final String ARG_CURRENT = "current";

    private List<Option> options = Collections.emptyList();
    private OnPicked callback;

    private PickerOptionSheet() {
    }

    public static void show(@NonNull FragmentActivity activity, @NonNull String title,
                            @Nullable String subtitle, @Nullable String current,
                            @NonNull List<Option> options, @NonNull OnPicked callback) {
        PickerOptionSheet sheet = new PickerOptionSheet();
        sheet.options = options;
        sheet.callback = callback;
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_SUBTITLE, subtitle);
        args.putString(ARG_CURRENT, current);
        sheet.setArguments(args);
        sheet.show(activity.getSupportFragmentManager(), PickerOptionSheet.class.getSimpleName());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_picker_options, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        String current = args != null ? args.getString(ARG_CURRENT) : null;

        TextView title = view.findViewById(R.id.tvPickerTitle);
        if (args != null && args.getString(ARG_TITLE) != null) {
            title.setText(args.getString(ARG_TITLE));
        }
        TextView subtitle = view.findViewById(R.id.tvPickerSubtitle);
        String sub = args != null ? args.getString(ARG_SUBTITLE) : null;
        if (sub != null && !sub.isEmpty()) {
            subtitle.setText(sub);
            subtitle.setVisibility(View.VISIBLE);
        }

        RecyclerView rv = view.findViewById(R.id.rvPickerOptions);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(new PickerOptionAdapter(options, current, label -> {
            HapticManager.lightTap(view);
            if (callback != null) {
                callback.onPicked(label);
            }
            dismiss();
        }));
    }
}