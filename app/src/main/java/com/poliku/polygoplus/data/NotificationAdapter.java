package com.poliku.polygoplus.data;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.R;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.Holder> {
    private final List<AppDataStore.NotificationRecord> notifications = new ArrayList<>();
    private final OnReadListener listener;

    public interface OnReadListener {
        void onRead(AppDataStore.NotificationRecord notification);
    }

    public NotificationAdapter(OnReadListener listener) {
        this.listener = listener;
    }

    public void submit(List<AppDataStore.NotificationRecord> items) {
        notifications.clear();
        notifications.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AppDataStore.NotificationRecord n = notifications.get(position);
        h.title.setText(n.title);
        h.body.setText(n.body);
        h.time.setText(formatTime(n.time));
        h.dot.setVisibility(n.read ? View.INVISIBLE : View.VISIBLE);
        h.title.setTextColor(Color.parseColor(n.read ? "#717171" : "#222222"));
        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRead(n);
            }
        });
    }

    private String formatTime(long time) {
        return time <= 0 ? "" : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(time));
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView title, body, time;
        final View dot;

        Holder(View v) {
            super(v);
            title = v.findViewById(R.id.notificationTitle);
            body = v.findViewById(R.id.notificationBody);
            time = v.findViewById(R.id.notificationTime);
            dot = v.findViewById(R.id.notificationUnreadDot);
        }
    }
}
