package com.poliku.polygoplus;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.EmptyStates;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;

@AndroidEntryPoint
public class TransactionsActivity extends AppCompatActivity {
    @Inject PolyGoRepository polyGoRepository;
    private LinearLayout listContainer;
    private View emptyView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_transactions);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        listContainer = findViewById(R.id.transactionList);
        emptyView = findViewById(R.id.emptyTransactions);

        EmptyStates.bind(emptyView, R.drawable.ic_receipt_long, "No transactions yet",
                "When you make an offer, the order will show here with meetup details.", "Browse listings",
                v -> startActivity(new Intent(this, SearchActivity.class)));

        fetchTransactions();
    }

    private void fetchTransactions() {
        String userId = AppDataStore.userId(this);
        polyGoRepository.getTransactions(userId, new Callback<PolyGoApi.TransactionsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.TransactionsResponse> call, Response<PolyGoApi.TransactionsResponse> response) {
                PolyGoApi.TransactionsResponse body = response.body();
                List<AppDataStore.TransactionRecord> items = new ArrayList<>();
                if (body != null && body.transactions != null) {
                    for (PolyGoApi.Transaction t : body.transactions) {
                        items.add(AppDataStore.TransactionRecord.fromTransaction(t));
                    }
                }
                renderTransactions(items);
            }

            @Override
            public void onFailure(Call<PolyGoApi.TransactionsResponse> call, Throwable t) {
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
                status.setBackgroundTintList(ColorStateList.valueOf(0xFFE8F5E9));
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
