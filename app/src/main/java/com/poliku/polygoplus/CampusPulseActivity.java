package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.RelativeTimeFormatter;
import com.poliku.polygoplus.ui.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class CampusPulseActivity extends BaseActivity {

    @Inject PolyGoRepository polyGoRepository;

    private final List<PolyGoApi.PulseAlert> items = new ArrayList<>();
    private final List<PolyGoApi.PulseAlert> announcements = new ArrayList<>();
    private PulseAdapter adapter;
    private PulseAdapter announcementAdapter;
    private TextView tvPulseEmpty;
    private ActivityResultLauncher<Intent> createPulseLauncher;
    private FirebaseFirestore db;
    private ListenerRegistration pulseListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_campus_pulse);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        tvPulseEmpty = findViewById(R.id.tvPulseEmpty);

        createPulseLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                refreshPulse();
            }
        });

        RecyclerView rv = findViewById(R.id.rvPulse);
        rv.setLayoutManager(new LinearLayoutManager(this));
        announcementAdapter = new PulseAdapter(announcements);
        adapter = new PulseAdapter(items);
        rv.setAdapter(new ConcatAdapter(
                new HeaderAdapter(R.string.pulse_official_announcements),
                announcementAdapter,
                new HeaderAdapter(R.string.pulse_student_threads), adapter));

        findViewById(R.id.btnPostRequest).setOnClickListener(v -> {
            HapticManager.swell(this);
            Intent intent = new Intent(this, CreatePulseActivity.class);
            createPulseLauncher.launch(intent);
            overridePendingTransition(R.anim.slide_in_up, R.anim.fade_out);
        });

        refreshPulse();
        
        db = FirebaseFirestore.getInstance();
        startRealTimeListener();
    }

    private void refreshPulse() {
        polyGoRepository.getPulse(new Callback<PolyGoApi.PulseResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.PulseResponse> call, Response<PolyGoApi.PulseResponse> response) {
                PolyGoApi.PulseResponse body = response.body();
                if (response.isSuccessful() && body != null && body.alerts != null) {
                    items.clear();
                    items.addAll(body.alerts);
                    announcements.clear();
                    if (body.announcements != null) announcements.addAll(body.announcements);
                    adapter.notifyDataSetChanged();
                    announcementAdapter.notifyDataSetChanged();
                    tvPulseEmpty.setVisibility(items.isEmpty() && announcements.isEmpty()
                            ? View.VISIBLE : View.GONE);
                } else {
                    UiUtils.snackbarError(CampusPulseActivity.this.findViewById(android.R.id.content), R.string.pulse_load_failed);
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.PulseResponse> call, Throwable t) {
                UiUtils.snackbarError(CampusPulseActivity.this.findViewById(android.R.id.content), R.string.pulse_load_failed);
            }
        });
    }

    private void startRealTimeListener() {
        pulseListener = db.collection("pulse")
                .orderBy("created_at", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    for (DocumentChange dc : value.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            PolyGoApi.PulseAlert alert = new PolyGoApi.PulseAlert();
                            alert.id = dc.getDocument().getId();
                            alert.title = dc.getDocument().getString("title");
                            alert.body = dc.getDocument().getString("body");
                            alert.tag = dc.getDocument().getString("tag");
                            alert.userName = dc.getDocument().getString("user_name");
                            Boolean global = dc.getDocument().getBoolean("is_global");
                            alert.global = Boolean.TRUE.equals(global)
                                    || "PolyGo+ Admin".equals(alert.userName);
                            Long ts = dc.getDocument().getLong("created_at");
                            alert.createdAt = ts != null ? ts : System.currentTimeMillis() / 1000;

                            if (alert.title == null) continue;

                            // Duplicate check
                            boolean exists = false;
                            List<PolyGoApi.PulseAlert> destination = alert.global
                                    ? announcements : items;
                            for (PolyGoApi.PulseAlert item : destination) {
                                if (alert.title.equals(item.title) && Math.abs(alert.createdAt - item.createdAt) < 5) {
                                    exists = true;
                                    break;
                                }
                            }

                            if (!exists) {
                                destination.add(0, alert);
                                if (alert.global) announcementAdapter.notifyItemInserted(0);
                                else adapter.notifyItemInserted(0);
                                tvPulseEmpty.setVisibility(View.GONE);
                            }
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        if (pulseListener != null) pulseListener.remove();
        super.onDestroy();
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
            h.time.setText(RelativeTimeFormatter.format(h.itemView.getContext(), item.createdAt * 1000L));
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

    private final class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.Holder> {
        private final int titleRes;

        HeaderAdapter(int titleRes) {
            this.titleRes = titleRes;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pulse_section_header, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.title.setText(titleRes);
        }

        @Override
        public int getItemCount() {
            return 1;
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView title;

            Holder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tvPulseSectionTitle);
            }
        }
    }
}
