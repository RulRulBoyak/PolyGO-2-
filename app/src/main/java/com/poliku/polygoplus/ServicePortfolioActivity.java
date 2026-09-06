package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.poliku.polygoplus.ui.HapticManager;

import java.util.ArrayList;
import java.util.List;

public class ServicePortfolioActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_portfolio);

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvPortfolio);
        rv.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        
        List<PortfolioItem> items = new ArrayList<>();
        items.add(new PortfolioItem("Amirul F.", "Mobile App Design", "High-fidelity prototypes."));
        items.add(new PortfolioItem("Siti A.", "Custom Tailoring", "Baju Kurung & uniforms."));
        items.add(new PortfolioItem("Rizal H.", "PC Repair", "Fast hardware troubleshooting."));
        items.add(new PortfolioItem("Sarah J.", "Digital Portrait", "Perfect for gifts."));
        items.add(new PortfolioItem("Kevin L.", "Photography", "Event & graduation shoots."));

        rv.setAdapter(new PortfolioAdapter(items));
    }

    static class PortfolioItem {
        String name, title, desc;
        PortfolioItem(String n, String t, String d) { name = n; title = t; desc = d; }
    }

    private class PortfolioAdapter extends RecyclerView.Adapter<PortfolioAdapter.Holder> {
        private final List<PortfolioItem> items;
        PortfolioAdapter(List<PortfolioItem> i) { items = i; }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_portfolio_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            PortfolioItem item = items.get(position);
            h.name.setText(item.name);
            h.title.setText(item.title);
            h.desc.setText(item.desc);
            h.itemView.setOnClickListener(v -> HapticManager.swell(v.getContext()));
        }

        @Override public int getItemCount() { return items.size(); }

        class Holder extends RecyclerView.ViewHolder {
            TextView name, title, desc;
            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.tvCreatorName);
                title = v.findViewById(R.id.tvServiceTitle);
                desc = v.findViewById(R.id.tvServiceDescription);
            }
        }
    }
}
