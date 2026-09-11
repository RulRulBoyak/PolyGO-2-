package com.poliku.polygoplus;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.HapticManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class CampusPulseActivity extends AppCompatActivity {

    @Inject PolyGoRepository polyGoRepository;

    private final List<PolyGoApi.PulseAlert> items = new ArrayList<>();
    private PulseAdapter adapter;
    private TextView tvPulseEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_campus_pulse);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        tvPulseEmpty = findViewById(R.id.tvPulseEmpty);

        RecyclerView rv = findViewById(R.id.rvPulse);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PulseAdapter(items);
        rv.setAdapter(adapter);

        findViewById(R.id.btnPostRequest).setOnClickListener(v -> {
            HapticManager.swell(this);
            showPostDialog();
        });

        refreshPulse();
    }

    private void refreshPulse() {
        polyGoRepository.getPulse(new Callback<PolyGoApi.PulseResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.PulseResponse> call, Response<PolyGoApi.PulseResponse> response) {
                PolyGoApi.PulseResponse body = response.body();
                if (response.isSuccessful() && body != null && body.alerts != null) {
                    items.clear();
                    items.addAll(body.alerts);
                    adapter.notifyDataSetChanged();
                    tvPulseEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                } else {
                    Toast.makeText(CampusPulseActivity.this, R.string.pulse_load_failed, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.PulseResponse> call, Throwable t) {
                Toast.makeText(CampusPulseActivity.this, R.string.pulse_load_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPostDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pulse_post, null);
        ChipGroup chipTag = dialogView.findViewById(R.id.chipPulseTag);
        TextInputEditText etTitle = dialogView.findViewById(R.id.etPulseTitle);
        TextInputEditText etBody = dialogView.findViewById(R.id.etPulseBody);

        new AlertDialog.Builder(this)
                .setTitle(R.string.pulse_post_dialog_title)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.pulse_post_send, (d, w) -> {
                    int checkedId = chipTag.getCheckedChipId();
                    if (checkedId == View.NO_ID) {
                        Toast.makeText(this, R.string.pulse_tag_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Chip tagChip = chipTag.findViewById(checkedId);
                    String tag = tagChip == null ? "REQUEST" : tagChip.getText().toString();
                    String title = etTitle.getText() == null ? "" : etTitle.getText().toString().trim();
                    String body = etBody.getText() == null ? "" : etBody.getText().toString().trim();

                    if (!AppDataStore.isLoggedIn(this)) {
                        Toast.makeText(this, R.string.pulse_login_required, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (title.isEmpty() || body.isEmpty()) {
                        Toast.makeText(this, R.string.pulse_validation, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    postPulse(tag, title, body);
                })
                .show();
    }

    private void postPulse(String tag, String title, String body) {
        polyGoRepository.postPulse(tag, title, body, new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    HapticManager.success(CampusPulseActivity.this);
                    Toast.makeText(CampusPulseActivity.this, R.string.pulse_posted, Toast.LENGTH_SHORT).show();
                    refreshPulse();
                } else if (response.code() == 401) {
                    Toast.makeText(CampusPulseActivity.this, R.string.pulse_login_required, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(CampusPulseActivity.this, R.string.pulse_post_failed, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                Toast.makeText(CampusPulseActivity.this, R.string.pulse_post_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static String relativeTime(Context context, long epochSeconds) {
        long now = System.currentTimeMillis() / 1000L;
        long diff = now - epochSeconds;
        if (diff < 60L) {
            return context.getString(R.string.pulse_time_just_now);
        }
        if (diff < 3600L) {
            return context.getString(R.string.pulse_time_m, diff / 60L);
        }
        if (diff < 86400L) {
            return context.getString(R.string.pulse_time_h, diff / 3600L);
        }
        if (diff < 604800L) {
            return context.getString(R.string.pulse_time_d, diff / 86400L);
        }
        return context.getString(R.string.pulse_time_w, diff / 604800L);
    }

    private class PulseAdapter extends RecyclerView.Adapter<PulseAdapter.Holder> {
        private final List<PolyGoApi.PulseAlert> items;

        PulseAdapter(List<PolyGoApi.PulseAlert> i) {
            items = i;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pulse_alert, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            PolyGoApi.PulseAlert item = items.get(position);
            String tag = item.tag == null ? "REQUEST" : item.tag.toUpperCase(Locale.ROOT);
            h.tag.setText(tag);
            h.title.setText(item.title);
            h.body.setText(item.body);
            h.time.setText(relativeTime(h.itemView.getContext(), item.createdAt));
            h.itemView.setOnClickListener(v -> HapticManager.lightTap(v));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            TextView tag, title, body, time;

            Holder(View v) {
                super(v);
                tag = v.findViewById(R.id.tvPulseTag);
                title = v.findViewById(R.id.tvPulseTitle);
                body = v.findViewById(R.id.tvPulseBody);
                time = v.findViewById(R.id.tvPulseTime);
            }
        }
    }
}