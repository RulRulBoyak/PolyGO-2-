package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.data.AppDataStore;

import java.text.DateFormat;
import java.util.Date;

public class ReceiptActivity extends AppCompatActivity {
    public static final String EXTRA_TRANSACTION_ID = "transaction_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        AppDataStore.TransactionRecord order = AppDataStore.getTransaction(this, getIntent().getStringExtra(EXTRA_TRANSACTION_ID));
        if (order == null) {
            Toast.makeText(this, "Receipt not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(order.time));
        ((TextView) findViewById(R.id.tvReceiptBody)).setText(
                "Item: " + order.title + "\nAgreed price: " + order.amount
                        + "\nSeller: " + order.seller
                        + "\nBuyer: " + AppDataStore.userName(this)
                        + "\nMeetup: " + order.location
                        + "\nStatus: " + order.status
                        + "\nDate: " + when
                        + "\nReceipt ID: " + order.id);
    }
}
