package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;

public class OrderDetailActivity extends AppCompatActivity {
    public static final String EXTRA_TRANSACTION_ID = "transaction_id";
    private AppDataStore.TransactionRecord order;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        String id = getIntent().getStringExtra(EXTRA_TRANSACTION_ID);
        
        // Try local first for immediate UI
        order = AppDataStore.getTransaction(this, id);
        
        if (order == null) {
            Toast.makeText(this, "Order not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        renderOrder();
    }

    private void renderOrder() {
        ((TextView) findViewById(R.id.tvOrderTitle)).setText(order.title);
        ((TextView) findViewById(R.id.tvOrderAmount)).setText(order.amount);
        ((TextView) findViewById(R.id.tvOrderStatus)).setText(order.status + "  •  " + order.seller);
        ((TextView) findViewById(R.id.tvOrderLocation)).setText(order.location);
        ((TextView) findViewById(R.id.tvMapPin)).setText("📍\n" + order.location + "\nPKS campus landmark");

        findViewById(R.id.btnCompleteDeal).setEnabled(!"Completed".equalsIgnoreCase(order.status));
        findViewById(R.id.btnCompleteDeal).setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            String userId = AppDataStore.userId(this);
            v.setEnabled(false);
            
            // Professional Server Sync
            NetworkApi.updateTransactionStatus(userId, order.id, "completed", new NetworkApi.Callback() {
                @Override
                public void onSuccess(org.json.JSONObject response) {
                    AppDataStore.updateTransactionStatus(OrderDetailActivity.this, order.id, "Completed");
                    Toast.makeText(OrderDetailActivity.this, "Transaction completed!", Toast.LENGTH_SHORT).show();
                    goToReview();
                }

                @Override
                public void onError(String message) {
                    // Local fallback
                    AppDataStore.updateTransactionStatus(OrderDetailActivity.this, order.id, "Completed");
                    Toast.makeText(OrderDetailActivity.this, "Completed (Offline Mode)", Toast.LENGTH_SHORT).show();
                    goToReview();
                }
            });
        });
        findViewById(R.id.btnViewReceipt).setOnClickListener(v -> {
            Intent i = new Intent(this, ReceiptActivity.class);
            i.putExtra(ReceiptActivity.EXTRA_TRANSACTION_ID, order.id);
            startActivity(i);
        });
    }

    private void goToReview() {
        if (!order.reviewed) {
            Intent review = new Intent(this, ReviewActivity.class);
            review.putExtra(ReviewActivity.EXTRA_SELLER, order.seller);
            review.putExtra(ReviewActivity.EXTRA_TRANSACTION_ID, order.id);
            startActivity(review);
        }
        finish();
    }
}
