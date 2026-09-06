package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.ui.HapticManager;

import java.util.ArrayList;
import java.util.List;

public class CampusPulseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_campus_pulse);

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvPulse);
        rv.setLayoutManager(new LinearLayoutManager(this));

        List<PulseAlert> items = new ArrayList<>();
        items.add(new PulseAlert("ANNOUNCEMENT", "Found: Scientific Calculator", "Found at Library Block B. Please claim at the desk.", "1h ago"));
        items.add(new PulseAlert("REQUEST", "Needed: MacBook Charger", "Anyone in Block A has an M1 charger I can borrow for 1 hour?", "3h ago"));
        items.add(new PulseAlert("FLASH SALE", "Dorm Clearance!", "Everything must go by tomorrow morning at Block C.", "5h ago"));
        items.add(new PulseAlert("EVENT", "PKS Night Run", "Registration is now open for the upcoming campus run.", "1d ago"));

        rv.setAdapter(new PulseAdapter(items));

        findViewById(R.id.btnPostRequest).setOnClickListener(v -> {
            HapticManager.swell(this);
            Toast.makeText(this, "Post Request feature coming soon!", Toast.LENGTH_SHORT).show();
        });
    }

    static class PulseAlert {
        String tag, title, body, time;
        PulseAlert(String tg, String tl, String b, String tm) { tag = tg; title = tl; body = b; time = tm; }
    }

    private class PulseAdapter extends RecyclerView.Adapter<PulseAdapter.Holder> {
        private final List<PulseAlert> items;
        PulseAdapter(List<PulseAlert> i) { items = i; }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pulse_alert, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            PulseAlert item = items.get(position);
            h.tag.setText(item.tag);
            h.title.setText(item.title);
            h.body.setText(item.body);
            h.time.setText(item.time);
            h.itemView.setOnClickListener(v -> HapticManager.lightTap(v));
        }

        @Override public int getItemCount() { return items.size(); }

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
