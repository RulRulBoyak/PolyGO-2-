package com.poliku.polygoplus.ui;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.poliku.polygoplus.R;

/**
 * Modal M3 bottom sheet that replaces the old exposed-dropdown landmark list.
 * Shows a live search box over an icon-tile grid of campus landmarks. The
 * chosen landmark is returned via {@link OnPicked} and the sheet dismisses.
 */
public final class LandmarkPickerSheet {

    public interface OnPicked {
        void onPicked(@NonNull String landmark);
    }

    private LandmarkPickerSheet() {
    }

    public static void show(@NonNull Activity activity, String current, @NonNull OnPicked callback) {
        final BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.sheet_landmark_picker, null);

        RecyclerView rv = view.findViewById(R.id.rvLandmarks);
        rv.setLayoutManager(new GridLayoutManager(activity, 2));
        LandmarkAdapter adapter = new LandmarkAdapter(name -> {
            HapticManager.lightTap(activity.getWindow().getDecorView());
            callback.onPicked(name);
            dialog.dismiss();
        });
        adapter.setSelected(current);
        adapter.filter("");
        rv.setAdapter(adapter);

        TextInputEditText search = view.findViewById(R.id.etSearchLandmark);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                HapticManager.selectionTick(activity);
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        dialog.setContentView(view);
        dialog.show();
    }
}