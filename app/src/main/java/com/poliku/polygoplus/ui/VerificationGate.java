package com.poliku.polygoplus.ui;

import android.app.Activity;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;

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
        new AlertDialog.Builder(activity)
                .setTitle(R.string.verification_required_title)
                .setMessage(R.string.verification_posting_blocked)
                .setPositiveButton(R.string.verification_go_now, (dialog, which) ->
                        activity.startActivity(new Intent(activity, VerificationActivity.class)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        return false;
    }
}