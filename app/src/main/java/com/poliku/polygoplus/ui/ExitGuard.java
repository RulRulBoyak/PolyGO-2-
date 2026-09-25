package com.poliku.polygoplus.ui;

import android.app.Activity;
import android.content.res.ColorStateList;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.poliku.polygoplus.R;

import java.util.Collection;

/** Shared "are you sure you want to leave?" confirmation for screens with unsaved input. */
public final class ExitGuard {

    private ExitGuard() {
    }

    /** True if any of the given editable fields currently holds non-empty text. */
    public static boolean anyText(@Nullable CharSequence... values) {
        if (values == null) {
            return false;
        }
        for (CharSequence value : values) {
            if (value != null && value.toString().trim().length() > 0) {
                return true;
            }
        }
        return false;
    }

    /** True if the given collection (e.g. picked photos) is non-empty. */
    public static boolean nonEmpty(@Nullable Collection<?> values) {
        return values != null && !values.isEmpty();
    }

    /** Two-button dialog: Cancel (stay) / Discard (leave). */
    public static void show(Activity activity, Runnable onDiscard) {
        show(activity, null, null, onDiscard);
    }

    /** Optional neutral button (e.g. "Save draft") between Cancel and Discard. */
    public static void show(Activity activity,
                            @Nullable @StringRes Integer neutralLabel,
                            @Nullable Runnable onNeutral,
                            Runnable onDiscard) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.discard_changes_title)
                .setMessage(R.string.discard_changes_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.discard, (d, w) -> onDiscard.run());
        if (neutralLabel != null && onNeutral != null) {
            builder.setNeutralButton(neutralLabel, (d, w) -> onNeutral.run());
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            ViewCompat.setBackgroundTintList(dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                    ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.semantic_error)));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(activity, R.color.white));
        });
        dialog.show();
    }
}
