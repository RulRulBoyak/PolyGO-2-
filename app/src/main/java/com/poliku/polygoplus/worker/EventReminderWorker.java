package com.poliku.polygoplus.worker;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.poliku.polygoplus.EventsActivity;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.network.NotificationChannels;

import java.util.concurrent.TimeUnit;

public class EventReminderWorker extends Worker {
    private static final String PREFIX = "campus-event-";

    public EventReminderWorker(@NonNull Context context,
                               @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        Intent intent = new Intent(getApplicationContext(), EventsActivity.class);
        String eventId = getInputData().getString("id");
        String title = getInputData().getString("title");
        String venue = getInputData().getString("venue");
        int requestCode = eventId == null ? 0 : eventId.hashCode();
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), requestCode,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(
                getApplicationContext(), NotificationChannels.EVENT_REMINDERS)
                .setSmallIcon(R.drawable.ic_calendar)
                .setColor(getApplicationContext().getColor(R.color.pks_green))
                .setContentTitle(title)
                .setContentText(venue)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(venue))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        try {
            NotificationManagerCompat.from(getApplicationContext())
                    .notify(requestCode, notification.build());
        } catch (SecurityException ignored) {
        }
        return Result.success();
    }

    public static void schedule(Context context, PolyGoApi.CampusEvent event, long startTime) {
        long remindAt = Math.max(System.currentTimeMillis(), startTime - TimeUnit.HOURS.toMillis(1));
        Data data = new Data.Builder().putString("id", event.id)
                .putString("title", event.title).putString("venue", event.venue).build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(EventReminderWorker.class)
                .setInputData(data)
                .setInitialDelay(remindAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(PREFIX + event.id,
                androidx.work.ExistingWorkPolicy.REPLACE, work);
    }

    public static void cancel(Context context, String eventId) {
        WorkManager.getInstance(context).cancelUniqueWork(PREFIX + eventId);
    }
}
