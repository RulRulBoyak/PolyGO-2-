package com.poliku.polygoplus.ui;

import android.content.Context;

import com.poliku.polygoplus.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Shared relative-time formatting for listings and campus pulse.
 * Accepts epoch MILLISECONDS. Falls back to an absolute date past 7 days.
 */
public final class RelativeTimeFormatter {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long WEEK = 7 * DAY;

    private RelativeTimeFormatter() {
    }

    /** "Just now", "5m ago", "3h ago", "2d ago", or "16 Sep" past one week. Empty if <=0. */
    public static String format(Context context, long epochMillis) {
        if (epochMillis <= 0L) {
            return "";
        }
        long diff = System.currentTimeMillis() - epochMillis;
        if (diff < MINUTE) {
            return context.getString(R.string.pulse_time_just_now);
        }
        if (diff < HOUR) {
            return context.getString(R.string.pulse_time_m, diff / MINUTE);
        }
        if (diff < DAY) {
            return context.getString(R.string.pulse_time_h, diff / HOUR);
        }
        if (diff < WEEK) {
            return context.getString(R.string.pulse_time_d, diff / DAY);
        }
        return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(epochMillis));
    }

    /** Full absolute date for the detail screen, e.g. "Sep 16, 2026". */
    public static String formatDate(long epochMillis) {
        if (epochMillis <= 0L) {
            return "";
        }
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                .format(new Date(epochMillis));
    }

    /** Detail string: "Listed 3h ago" within a week, "Listed on Sep 16, 2026" past a week. */
    public static String formatDetail(Context context, long epochMillis) {
        if (epochMillis <= 0L) {
            return "";
        }
        long diff = System.currentTimeMillis() - epochMillis;
        if (diff < WEEK) {
            return context.getString(R.string.listing_posted_prefix, format(context, epochMillis));
        }
        return context.getString(R.string.listing_listed_on, formatDate(epochMillis));
    }
}