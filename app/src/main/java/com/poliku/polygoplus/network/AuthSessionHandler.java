package com.poliku.polygoplus.network;

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.poliku.polygoplus.BannedActivity;
import com.poliku.polygoplus.LoginActivity;
import com.poliku.polygoplus.data.AppDataStore;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-flight handler for JWT / session expiry detected by the OkHttp
 * auth-response interceptor.  Clears the local session and routes to
 * {@link LoginActivity} when the app is in the foreground.
 * <p>
 * Suppressed when the top activity is already LoginActivity (avoids loops)
 * and when the app is in the background (no UI to present).
 */
public final class AuthSessionHandler {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean FIRED = new AtomicBoolean(false);

    private static volatile Context appContext;
    private static volatile WeakReference<Activity> topActivity;
    private static volatile int startedCount;

    private AuthSessionHandler() { }

    /** Call from {@link com.poliku.polygoplus.PolyGoApplication#onCreate}. */
    public static void register(Application app) {
        appContext = app.getApplicationContext();
        app.registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (startedCount == 0) {
                    FIRED.set(false);
                }
                startedCount++;
                topActivity = new WeakReference<>(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity a) { }

            @Override
            public void onActivityPaused(@NonNull Activity a) { }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                startedCount--;
                WeakReference<Activity> ref = topActivity;
                if (ref != null && ref.get() == activity) {
                    topActivity = null;
                }
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }

            @Override
            public void onActivityDestroyed(@NonNull Activity a) { }
        });
    }

    public static boolean isAppForeground() {
        return startedCount > 0;
    }

    @Nullable
    public static Activity getTopActivity() {
        WeakReference<Activity> ref = topActivity;
        return ref == null ? null : ref.get();
    }

    /**
     * Invoked from the OkHttp auth-response interceptor (background thread).
     * Single-flight: the first call within a foreground cycle triggers a logout
     * and launches {@link LoginActivity}; subsequent calls are suppressed until
     * the app re-enters the foreground.
     */
    public static void onSessionExpired() {
        if (!FIRED.compareAndSet(false, true)) {
            return;
        }
        MAIN.post(() -> {
            Context ctx = appContext;
            if (ctx == null) {
                return;
            }
            Activity top = getTopActivity();
            if (top == null || top.isFinishing() || top.isDestroyed()) {
                return;
            }
            if (top instanceof LoginActivity) {
                return;
            }
            AppDataStore.logout(ctx);
            Intent intent = new Intent(ctx, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            try {
                ctx.startActivity(intent);
            } catch (Exception ignored) {
                // No launcher intent available yet - stay silent.
            }
        });
    }

    /**
     * Account suspension (admin ban) detected from a 401 whose body says
     * "suspended". Caches the flag so cold starts route straight to
     * {@link BannedActivity}, then replaces the back stack with the banned
     * screen. Shares the single-flight guard with {@link #onSessionExpired()}
     * so a burst of 401s can't present two screens.
     */
    public static void onSuspended() {
        if (!FIRED.compareAndSet(false, true)) {
            return;
        }
        MAIN.post(() -> {
            Context ctx = appContext;
            if (ctx == null) {
                return;
            }
            Activity top = getTopActivity();
            if (top == null || top.isFinishing() || top.isDestroyed()) {
                return;
            }
            if (top instanceof BannedActivity || top instanceof LoginActivity) {
                return;
            }
            AppDataStore.markBanned(ctx);
            AppDataStore.logout(ctx);
            Intent intent = new Intent(ctx, BannedActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            try {
                ctx.startActivity(intent);
            } catch (Exception ignored) {
                // No launcher intent available yet - stay silent.
            }
        });
    }
}
