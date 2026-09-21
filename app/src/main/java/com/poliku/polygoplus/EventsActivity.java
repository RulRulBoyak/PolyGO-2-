package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.util.EventCardBinder;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class EventsActivity extends BaseActivity {
    @Inject PolyGoRepository repository;
    private final List<PolyGoApi.CampusEvent> events = new ArrayList<>();
    private EventAdapter adapter;
    private TextView empty;

    private void openDetail(PolyGoApi.CampusEvent event) {
        Intent intent = new Intent(this, EventDetailActivity.class);
        intent.putExtra(EventDetailActivity.EXTRA_EVENT, new Gson().toJson(event));
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_events);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        empty = findViewById(R.id.tvEmpty);
        RecyclerView list = findViewById(R.id.rvEvents);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EventAdapter();
        list.setAdapter(adapter);
        loadEvents();
    }

    private void loadEvents() {
        repository.campus("events", new Callback<PolyGoApi.CampusResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.CampusResponse> call,
                                   Response<PolyGoApi.CampusResponse> response) {
                events.clear();
                if (response.isSuccessful() && response.body() != null
                        && response.body().events != null) {
                    for (PolyGoApi.CampusEvent e : response.body().events) {
                        if (e != null) events.add(e);
                    }
                }
                adapter.notifyDataSetChanged();
                empty.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(Call<PolyGoApi.CampusResponse> call, Throwable error) {
                empty.setVisibility(View.VISIBLE);
            }
        });
    }

    private final class EventAdapter extends RecyclerView.Adapter<EventAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_campus_event, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            PolyGoApi.CampusEvent event = events.get(position);
            EventCardBinder.Bindings views = holder.views;
            views.title.setText(event.title);
            views.venue.setText(event.venue);
            views.description.setText(event.description);
            EventCardBinder.apply(views, event,
                    holder.itemView.getResources().getDisplayMetrics().density);
            holder.itemView.setOnClickListener(v -> openDetail(event));
        }

        @Override
        public int getItemCount() {
            return events.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final EventCardBinder.Bindings views;

            Holder(View view) {
                super(view);
                views = EventCardBinder.bind(view);
            }
        }
    }
}