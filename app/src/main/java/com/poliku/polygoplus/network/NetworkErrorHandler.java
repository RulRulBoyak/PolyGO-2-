package com.poliku.polygoplus.network;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.poliku.polygoplus.ErrorStateActivity;

/**
 * Global network-failure hook used by the OkHttp interceptor in NetworkModule.
 * Detects unreachable servers and routes the user to ErrorStateActivity.
 * A cooldown window prevents repeated launches while the error screen is up or
 * while the user is retrying.
 */
public final class NetworkErrorHandler {

    private static final long COOLDOWN_MS = 15_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile Context appContext;
    private static volatile long lastShownAt = 0L;

    private NetworkErrorHandler() {
    }

    public static void register(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static void onNetworkError() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastShownAt < COOLDOWN_MS) {
            return;
        }
        lastShownAt = now;
        MAIN.post(() -> {
            Context context = appContext;
            if (context == null) {
                return;
            }
            Intent intent = new Intent(context, ErrorStateActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (Exception ignored) {
                // No launcher intent available yet - stay silent.
            }
        });
    }

    public static void notifyServerOk() {
        lastShownAt = 0L;
    }
}