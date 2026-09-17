package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.poliku.polygoplus.ui.BaseActivity;
import dagger.hilt.android.AndroidEntryPoint;

import com.poliku.polygoplus.data.AppDataStore;

import java.text.DateFormat;
import java.util.Date;

@AndroidEntryPoint
public class ReceiptActivity extends BaseActivity {
    public static final String EXTRA_TRANSACTION_ID = "transaction_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        AppDataStore.TransactionRecord order = AppDataStore.getTransaction(this, getIntent().getStringExtra(EXTRA_TRANSACTION_ID));
        if (order == null) {
            Toast.makeText(this, R.string.toast_receipt_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(order.time));
        ((TextView) findViewById(R.id.tvReceiptBody)).setText(
                getString(R.string.receipt_body, order.title, order.amount, order.seller,
                        AppDataStore.userName(this), order.location, order.status, when, order.id));
    }
}
