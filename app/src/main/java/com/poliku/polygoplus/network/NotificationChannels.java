package com.poliku.polygoplus.network;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;

import com.poliku.polygoplus.R;

/** Stable notification channels shared by remote pushes and local reminders. */
public final class NotificationChannels {
    public static final String LEGACY = "polygo_updates";
    public static final String MESSAGES = "polygo_messages";
    public static final String CAMPUS_ALERTS = "polygo_campus_alerts";
    public static final String EVENT_REMINDERS = "polygo_event_reminders";
    public static final String MARKETPLACE = "polygo_marketplace";

    private NotificationChannels() {
    }

    public static void createAll(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        create(manager, context, LEGACY, R.string.channel_updates,
                R.string.channel_updates_description, NotificationManager.IMPORTANCE_DEFAULT);
        create(manager, context, MESSAGES, R.string.channel_messages,
                R.string.channel_messages_description, NotificationManager.IMPORTANCE_HIGH);
        create(manager, context, CAMPUS_ALERTS, R.string.channel_campus_alerts,
                R.string.channel_campus_alerts_description, NotificationManager.IMPORTANCE_DEFAULT);
        create(manager, context, EVENT_REMINDERS, R.string.channel_event_reminders,
                R.string.channel_event_reminders_description, NotificationManager.IMPORTANCE_DEFAULT);
        create(manager, context, MARKETPLACE, R.string.channel_marketplace,
                R.string.channel_marketplace_description, NotificationManager.IMPORTANCE_DEFAULT);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void create(NotificationManager manager, Context context, String id,
                               @StringRes int name, @StringRes int description, int importance) {
        NotificationChannel channel = new NotificationChannel(id, context.getString(name), importance);
        channel.setDescription(context.getString(description));
        manager.createNotificationChannel(channel);
    }

    public static String forType(String type) {
        if (type == null) return LEGACY;
        switch (type) {
            case "chat":
            case "message":
                return MESSAGES;
            case "global_announcement":
            case "live_alert":
            case "campus_alert":
                return CAMPUS_ALERTS;
            case "event":
            case "event_reminder":
                return EVENT_REMINDERS;
            case "offer":
            case "review":
            case "transactions":
            case "marketplace":
                return MARKETPLACE;
            default:
                return LEGACY;
        }
    }

    public static int priorityFor(String channel) {
        return MESSAGES.equals(channel) ? NotificationCompat.PRIORITY_HIGH
                : NotificationCompat.PRIORITY_DEFAULT;
    }
}
