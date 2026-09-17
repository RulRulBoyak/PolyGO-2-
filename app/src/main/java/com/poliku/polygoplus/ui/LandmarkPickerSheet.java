package com.poliku.polygoplus.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.poliku.polygoplus.R;

/**
 * Modal M3 bottom sheet that replaces the old exposed-dropdown landmark list.
 * Shows a live search box over an icon-tile grid of campus landmarks. The
 * chosen landmark is returned via {@link OnPicked} and the sheet dismisses.
 */
public final class LandmarkPickerSheet extends BottomSheetDialogFragment {

    public interface OnPicked {
        void onPicked(@NonNull String landmark);
    }

    private static final String ARG_SELECTED = "selected";

    private OnPicked callback;

    private LandmarkPickerSheet() {
    }

    public static void show(@NonNull FragmentActivity activity, String current, @NonNull OnPicked callback) {
        LandmarkPickerSheet sheet = new LandmarkPickerSheet();
        sheet.callback = callback;
        Bundle args = new Bundle();
        args.putString(ARG_SELECTED, current);
        sheet.setArguments(args);
        FragmentActivity host = activity instanceof FragmentActivity ? (FragmentActivity) activity : null;
        if (host != null) {
            sheet.show(host.getSupportFragmentManager(), LandmarkPickerSheet.class.getSimpleName());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_landmark_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String selected = getArguments() == null ? null : getArguments().getString(ARG_SELECTED);

        RecyclerView rv = view.findViewById(R.id.rvLandmarks);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        LandmarkAdapter adapter = new LandmarkAdapter(name -> {
            HapticManager.lightTap(view);
            if (callback != null) {
                callback.onPicked(name);
            }
            dismiss();
        });
        adapter.setSelected(selected);
        adapter.filter("");
        rv.setAdapter(adapter);

        TextInputEditText search = view.findViewById(R.id.etSearchLandmark);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                HapticManager.selectionTick(requireContext());
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }
}