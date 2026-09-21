package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class TimetableActivity extends BaseActivity {
    @Inject PolyGoRepository repository;
    private final List<PolyGoApi.TimetableEntry> entries = new ArrayList<>();
    private TimetableAdapter adapter;
    private TextView empty;
    private TextView nextClass;
    private String[] days;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_timetable);
        days = getResources().getStringArray(R.array.weekdays);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        empty = findViewById(R.id.tvEmpty);
        nextClass = findViewById(R.id.tvNextClass);
        RecyclerView list = findViewById(R.id.rvTimetable);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TimetableAdapter();
        list.setAdapter(adapter);
        findViewById(R.id.btnAddClass).setOnClickListener(v -> showEditor(null));
        load();
    }

    private void load() {
        repository.campus("timetable", new Callback<PolyGoApi.CampusResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.CampusResponse> call,
                                   Response<PolyGoApi.CampusResponse> response) {
                entries.clear();
                if (response.isSuccessful() && response.body() != null
                        && response.body().timetable != null) {
                    entries.addAll(response.body().timetable);
                }
                adapter.notifyDataSetChanged();
                empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
                updateNextClass();
            }

            @Override
            public void onFailure(Call<PolyGoApi.CampusResponse> call, Throwable error) {
                empty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showEditor(PolyGoApi.TimetableEntry existing) {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_timetable_entry, null);
        EditText course = form.findViewById(R.id.etCourse);
        EditText room = form.findViewById(R.id.etRoom);
        EditText start = form.findViewById(R.id.etStart);
        EditText end = form.findViewById(R.id.etEnd);
        Spinner day = form.findViewById(R.id.spinnerDay);
        day.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, days));
        if (existing != null) {
            course.setText(existing.course);
            room.setText(existing.room);
            day.setSelection(Math.max(0, existing.day_of_week - 1));
            start.setText(shortTime(existing.starts_at));
            end.setText(shortTime(existing.ends_at));
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.timetable_add : R.string.timetable_edit)
                .setView(form).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(-1).setOnClickListener(v -> {
            String courseValue = course.getText().toString().trim();
            String roomValue = room.getText().toString().trim();
            String startValue = normalizeTime(start.getText().toString());
            String endValue = normalizeTime(end.getText().toString());
            int dayValue = day.getSelectedItemPosition() + 1;
            if (courseValue.isEmpty() || roomValue.isEmpty() || startValue == null
                    || endValue == null || endValue.compareTo(startValue) <= 0) {
                Toast.makeText(this, R.string.timetable_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (overlaps(existing == null ? 0 : existing.id, dayValue, startValue, endValue)) {
                Toast.makeText(this, R.string.timetable_overlap, Toast.LENGTH_LONG).show();
                return;
            }
            save(existing == null ? null : String.valueOf(existing.id), courseValue, roomValue,
                    String.valueOf(dayValue), startValue, endValue, dialog);
        }));
        dialog.show();
    }

    private void save(String id, String course, String room, String day, String start,
                      String end, AlertDialog dialog) {
        repository.saveTimetable(id, course, room, day, start, end,
                new Callback<PolyGoApi.CampusResponse>() {
                    @Override
                    public void onResponse(Call<PolyGoApi.CampusResponse> call,
                                           Response<PolyGoApi.CampusResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().isSuccess()) {
                            dialog.dismiss();
                            load();
                        } else {
                            Toast.makeText(TimetableActivity.this, R.string.timetable_save_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<PolyGoApi.CampusResponse> call, Throwable error) {
                        Toast.makeText(TimetableActivity.this, R.string.timetable_save_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean overlaps(int ignoredId, int day, String start, String end) {
        for (PolyGoApi.TimetableEntry item : entries) {
            if (item.id == ignoredId || item.day_of_week != day) continue;
            if (start.compareTo(safeTime(item.ends_at)) < 0 && end.compareTo(safeTime(item.starts_at)) > 0) return true;
        }
        return false;
    }

    private String normalizeTime(String value) {
        String trimmed = value.trim();
        if (!trimmed.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) return null;
        String[] parts = trimmed.split(":");
        return String.format(Locale.US, "%02d:%s:00", Integer.parseInt(parts[0]), parts[1]);
    }

    private String shortTime(String time) {
        return time == null || time.length() < 5 ? "" : time.substring(0, 5);
    }

    private String dayLabel(int dayOfWeek) {
        return dayOfWeek >= 1 && dayOfWeek <= 7 ? days[dayOfWeek - 1] : "";
    }

    private String safeTime(String time) {
        return time == null ? "" : time;
    }

    private void updateNextClass() {
        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                ? 7 : now.get(Calendar.DAY_OF_WEEK) - 1;
        String current = String.format(Locale.US, "%02d:%02d:00",
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        PolyGoApi.TimetableEntry next = null;
        int bestDistance = Integer.MAX_VALUE;
        for (PolyGoApi.TimetableEntry entry : entries) {
            int day = entry.day_of_week;
            if (day < 1 || day > 7) continue;
            int distance = (day - today + 7) % 7;
            if (distance == 0 && entry.ends_at != null && entry.ends_at.compareTo(current) <= 0) distance = 7;
            if (next == null || distance < bestDistance
                    || (distance == bestDistance
                    && safeTime(entry.starts_at).compareTo(safeTime(next.starts_at)) < 0)) {
                next = entry;
                bestDistance = distance;
            }
        }
        if (next == null) {
            nextClass.setText(R.string.timetable_no_next);
        } else {
            nextClass.setText(getString(R.string.timetable_next_format, next.course,
                    dayLabel(next.day_of_week), shortTime(next.starts_at)));
        }
    }

    private void confirmDelete(PolyGoApi.TimetableEntry entry) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.timetable_delete)
                .setMessage(entry.course).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        repository.deleteTimetable(String.valueOf(entry.id), new ReloadCallback())).show();
    }

    private final class ReloadCallback implements Callback<PolyGoApi.CampusResponse> {
        @Override
        public void onResponse(Call<PolyGoApi.CampusResponse> call,
                               Response<PolyGoApi.CampusResponse> response) {
            load();
        }

        @Override
        public void onFailure(Call<PolyGoApi.CampusResponse> call, Throwable error) {
            Toast.makeText(TimetableActivity.this, R.string.timetable_save_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private final class TimetableAdapter
            extends RecyclerView.Adapter<TimetableAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_timetable_entry, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            PolyGoApi.TimetableEntry entry = entries.get(position);
            holder.day.setText(dayLabel(entry.day_of_week));
            holder.course.setText(entry.course);
            holder.details.setText(getString(R.string.timetable_detail_format,
                    shortTime(entry.starts_at), shortTime(entry.ends_at), entry.room));
            holder.itemView.setOnClickListener(v -> showEditor(entry));
            holder.itemView.setOnLongClickListener(v -> {
                confirmDelete(entry);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView day;
            final TextView course;
            final TextView details;

            Holder(View view) {
                super(view);
                day = view.findViewById(R.id.tvClassDay);
                course = view.findViewById(R.id.tvClassCourse);
                details = view.findViewById(R.id.tvClassDetails);
            }
        }
    }
}
