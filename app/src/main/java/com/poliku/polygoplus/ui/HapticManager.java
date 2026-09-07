package com.poliku.polygoplus.ui;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * Premium Haptic Feedback Manager
 * Provides specialized vibration patterns for different user actions.
 */
public final class HapticManager {

    private HapticManager() {}

    /**
     * Subtle tap for tab changes or item clicks.
     */
    public static void lightTap(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /**
     * Clear click for important actions like Apply Sort or Save.
     */
    public static void mediumTap(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }

    /**
     * Heavy pulse for major state changes or primary action buttons.
     */
    public static void heavyTap(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }

    /**
     * Futuristic "Swell" effect for transitions or opening premium features.
     */
    public static void swell(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        
        // Complex pattern for higher-end devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            long[] pattern = {0, 10, 40, 20, 80, 40, 120}; 
            v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            v.vibrate(100);
        }
    }

    /**
     * Success pattern for finished transactions or successful listings.
     */
    public static void success(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            long[] pattern = {0, 40, 100, 60}; // Quick double tap
            v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            v.vibrate(100);
        }
    }
    
    /**
     * Warning pattern for errors or deletions.
     */
    public static void error(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            long[] pattern = {0, 100, 50, 100, 50, 100}; 
            v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            v.vibrate(300);
        }
    }

    /**
     * Light "Tick" for data entry or character-by-character validation.
     * Inspired by Duolingo's delightful typing feedback.
     */
    public static void selectionTick(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
        }
    }

    /**
     * Play a subtle "pop" sound for successful actions.
     */
    public static void popSound() {
        android.media.ToneGenerator tg = new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100);
        tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP);
    }
}
