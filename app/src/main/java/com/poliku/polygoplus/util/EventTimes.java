package com.poliku.polygoplus.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Shared campus-event date handling (single source of the pill/full formats). */
public final class EventTimes {

    private static final String SQL = "yyyy-MM-dd HH:mm:ss";
    private static final String PILL = "EEE, d MMM \u00B7 h:mm a";
    private static final String FULL = "EEEE, d MMMM yyyy \u00B7 h:mm a";

    private EventTimes() {
    }

    public static long parse(String value) {
        try {
            SimpleDateFormat parser = new SimpleDateFormat(SQL, Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
            Date date = parser.parse(value);
            return date == null ? 0 : date.getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

    /** Short pill format used on the event card header, e.g. "Sat, 1 Jan · 3:00 pm". */
    public static String pill(String value) {
        return format(value, PILL);
    }

    /** Long format used on the detail page, e.g. "Saturday, 1 January 2027 · 3:00 pm". */
    public static String full(String value) {
        return format(value, FULL);
    }

    private static String format(String value, String pattern) {
        long time = parse(value);
        if (time == 0) return value == null ? "" : value;
        SimpleDateFormat display = new SimpleDateFormat(pattern, Locale.getDefault());
        display.setTimeZone(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        return display.format(new Date(time));
    }
}