package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.HapticManager;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class OrderDetailActivity extends AppCompatActivity {
    @Inject PolyGoRepository polyGoRepository;
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

        if (order != null) {
            renderOrder();
            return;
        }

        // Fallback: fetch from server (Order History lists server transactions)
        String userId = AppDataStore.userId(this);
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Order not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        polyGoRepository.getTransactions(userId, new Callback<PolyGoApi.TransactionsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.TransactionsResponse> call, Response<PolyGoApi.TransactionsResponse> response) {
                PolyGoApi.TransactionsResponse body = response.body();
                if (body != null && body.transactions != null) {
                    for (PolyGoApi.Transaction t : body.transactions) {
                        if (id != null && id.equals(t.id)) {
                            order = AppDataStore.TransactionRecord.fromTransaction(t);
                            runOnUiThread(() -> renderOrder());
                            return;
                        }
                    }
                }
                Toast.makeText(OrderDetailActivity.this, "Order not found", Toast.LENGTH_SHORT).show();
                runOnUiThread(OrderDetailActivity.this::finish);
            }

            @Override
            public void onFailure(Call<PolyGoApi.TransactionsResponse> call, Throwable t) {
                Toast.makeText(OrderDetailActivity.this, "Order not found", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void renderOrder() {
        ((TextView) findViewById(R.id.tvOrderTitle)).setText(order.title);
        ((TextView) findViewById(R.id.tvOrderAmount)).setText(order.amount);
        ((TextView) findViewById(R.id.tvOrderStatus)).setText(order.status + "  •  " + order.seller);
        ((TextView) findViewById(R.id.tvOrderLocation)).setText(order.location);
        ((TextView) findViewById(R.id.tvMapPin)).setText("📍\n" + order.location + "\nPKS campus landmark");

        findViewById(R.id.btnCompleteDeal).setEnabled(!"Completed".equalsIgnoreCase(order.status));
        findViewById(R.id.btnCompleteDeal).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            String userId = AppDataStore.userId(this);
            v.setEnabled(false);
            
            // Professional Server Sync
            polyGoRepository.updateTransactionStatus(userId, order.id, "completed", new Callback<BaseResponse>() {
                @Override
                public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        AppDataStore.updateTransactionStatus(OrderDetailActivity.this, order.id, "Completed");
                        Toast.makeText(OrderDetailActivity.this, "Transaction completed!", Toast.LENGTH_SHORT).show();
                        goToReview();
                    } else {
                        onFailure(call, new Throwable("Update failed"));
                    }
                }

                @Override
                public void onFailure(Call<BaseResponse> call, Throwable t) {
                    // Local fallback
                    AppDataStore.updateTransactionStatus(OrderDetailActivity.this, order.id, "Completed");
                    Toast.makeText(OrderDetailActivity.this, "Completed (Offline Mode)", Toast.LENGTH_SHORT).show();
                    goToReview();
                }
            });
        });

        // Accept & Schedule Meetup (starts the Safe Meetup flow)
        View btnAcceptOffer = findViewById(R.id.btnAcceptOffer);
        btnAcceptOffer.setVisibility("Offer sent".equalsIgnoreCase(order.status) ? View.VISIBLE : View.GONE);
        btnAcceptOffer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            String userId = AppDataStore.userId(this);
            v.setEnabled(false);
            polyGoRepository.updateTransactionStatus(userId, order.id, "accepted", new Callback<BaseResponse>() {
                @Override
                public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        AppDataStore.updateTransactionStatus(OrderDetailActivity.this, order.id, "Accepted");
                        Toast.makeText(OrderDetailActivity.this, "Deal accepted \u2014 meetup tracker is live in chat", Toast.LENGTH_SHORT).show();
                        renderOrder();
                    } else {
                        onFailure(call, new Throwable("Update failed"));
                    }
                }

                @Override
                public void onFailure(Call<BaseResponse> call, Throwable t) {
                    AppDataStore.updateTransactionStatus(OrderDetailActivity.this, order.id, "Accepted");
                    Toast.makeText(OrderDetailActivity.this, "Accepted (Offline Mode)", Toast.LENGTH_SHORT).show();
                    renderOrder();
                }
            });
        });

        // Rule 3.3: Link to Live Deal Tracker
        findViewById(R.id.btnTrackDeal).setOnClickListener(v -> {
            HapticManager.swell(this);
            startActivity(new Intent(this, DealTrackerActivity.class));
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
            review.putExtra(ReviewActivity.EXTRA_LISTING_ID, order.listingId);
            startActivity(review);
        }
        finish();
    }
}
