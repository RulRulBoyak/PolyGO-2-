package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.ui.EmptyStates;

public class TransactionsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_transactions);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        LinearLayout list = findViewById(R.id.transactionList);
        java.util.List<AppDataStore.TransactionRecord> items = AppDataStore.getTransactions(this);
        View empty = findViewById(R.id.emptyTransactions);
        EmptyStates.bind(empty, android.R.drawable.ic_menu_agenda, "No transactions yet",
                "When you make an offer, the order will show here with meetup details.", "Browse listings",
                v -> startActivity(new Intent(this, SearchActivity.class)));
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        for (AppDataStore.TransactionRecord t : items) {
            MaterialCardView card = new MaterialCardView(this);
            card.setRadius(16);
            card.setCardElevation(0);
            card.setUseCompatPadding(true);
            TextView text = new TextView(this);
            text.setPadding(20, 18, 20, 18);
            text.setText(t.title + "\n" + t.amount + "  •  " + t.status + "\nMeetup: " + t.location);
            text.setTextSize(15);
            card.addView(text);
            card.setOnClickListener(v -> {
                Intent i = new Intent(this, OrderDetailActivity.class);
                i.putExtra(OrderDetailActivity.EXTRA_TRANSACTION_ID, t.id);
                startActivity(i);
            });
            list.addView(card);
        }
    }
}
