package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;
import com.poliku.polygoplus.ui.EmptyStates;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TransactionsActivity extends AppCompatActivity {
    private LinearLayout listContainer;
    private View emptyView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_transactions);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        listContainer = findViewById(R.id.transactionList);
        emptyView = findViewById(R.id.emptyTransactions);

        EmptyStates.bind(emptyView, android.R.drawable.ic_menu_agenda, "No transactions yet",
                "When you make an offer, the order will show here with meetup details.", "Browse listings",
                v -> startActivity(new Intent(this, SearchActivity.class)));

        fetchTransactions();
    }

    private void fetchTransactions() {
        String userId = AppDataStore.userId(this);
        NetworkApi.getTransactions(userId, new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONArray arr = response.optJSONArray("transactions");
                List<AppDataStore.TransactionRecord> items = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        items.add(AppDataStore.TransactionRecord.fromJson(arr.optJSONObject(i)));
                    }
                }
                renderTransactions(items);
            }

            @Override
            public void onError(String message) {
                // FALLBACK: Show local transactions if network fails
                renderTransactions(AppDataStore.getTransactions(TransactionsActivity.this));
                Toast.makeText(TransactionsActivity.this, "Viewing offline history", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderTransactions(List<AppDataStore.TransactionRecord> items) {
        listContainer.removeAllViews();
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        
        for (AppDataStore.TransactionRecord t : items) {
            View view = getLayoutInflater().inflate(R.layout.item_transaction, listContainer, false);
            
            ((TextView) view.findViewById(R.id.tvTransactionTitle)).setText(t.title);
            ((TextView) view.findViewById(R.id.tvTransactionAmount)).setText(t.amount);
            TextView status = view.findViewById(R.id.tvTransactionStatus);
            status.setText(t.status);
            ((TextView) view.findViewById(R.id.tvTransactionLocation)).setText(t.location);
            
            if ("Completed".equalsIgnoreCase(t.status)) {
                status.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE8F5E9));
                status.setTextColor(0xFF2E7D32);
            }

            view.setOnClickListener(v -> {
                Intent i = new Intent(this, OrderDetailActivity.class);
                i.putExtra(OrderDetailActivity.EXTRA_TRANSACTION_ID, t.id);
                startActivity(i);
            });
            
            listContainer.addView(view);
        }
    }
}
