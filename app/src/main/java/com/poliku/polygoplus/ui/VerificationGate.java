package com.poliku.polygoplus.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.VerificationActivity;
import com.poliku.polygoplus.data.AppDataStore;

public final class VerificationGate {

    private VerificationGate() {
    }

    public static boolean requireApproved(Activity activity) {
        if ("approved".equals(AppDataStore.verificationStatus(activity))) {
            return true;
        }
        showGate(activity);
        return false;
    }

    private static void showGate(Activity activity) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(R.layout.bottom_sheet_verification_gate);

        boolean pending = "pending".equals(AppDataStore.verificationStatus(activity));

        TextView title = dialog.findViewById(R.id.tvVerifyTitle);
        TextView subtitle = dialog.findViewById(R.id.tvVerifySubtitle);
        View benefits = dialog.findViewById(R.id.llVerifyBenefits);
        MaterialButton btnVerify = dialog.findViewById(R.id.btnVerifyNow);

        title.setText(pending ? R.string.verify_gate_pending_title : R.string.verify_gate_title);
        subtitle.setText(pending ? R.string.verify_gate_pending_subtitle : R.string.verify_gate_subtitle);
        benefits.setVisibility(pending ? View.GONE : View.VISIBLE);
        btnVerify.setText(pending ? R.string.verify_gate_view_status : R.string.verify_gate_confirm);

        btnVerify.setOnClickListener(v -> {
            HapticManager.success(activity);
            dialog.dismiss();
            activity.startActivity(new Intent(activity, VerificationActivity.class));
        });
        dialog.findViewById(R.id.btnVerifyLater).setOnClickListener(v -> dialog.dismiss());

        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(android.R.id.content);
            if (sheet != null) {
                sheet.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.fade_in_up));
            }
            HapticManager.swell(activity);
        });
        dialog.show();
    }
}