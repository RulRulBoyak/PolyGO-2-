package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.TextView;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.UiUtils;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class OrderDetailActivity extends BaseActivity {
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
        }

        // Fallback: fetch from server (Order History lists server transactions)
        String userId = AppDataStore.userId(this);
        if (userId == null || userId.isEmpty()) {
            UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_order_not_found);
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
                if (order == null) {
                    UiUtils.snackbarError(OrderDetailActivity.this.findViewById(android.R.id.content), R.string.toast_order_not_found);
                    runOnUiThread(OrderDetailActivity.this::finish);
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.TransactionsResponse> call, Throwable t) {
                if (order == null) {
                    UiUtils.snackbarError(OrderDetailActivity.this.findViewById(android.R.id.content), R.string.toast_order_not_found);
                    finish();
                }
            }
        });
    }

    private void renderOrder() {
        ((TextView) findViewById(R.id.tvOrderTitle)).setText(order.title);
        ((TextView) findViewById(R.id.tvOrderAmount)).setText(order.amount);
        ((TextView) findViewById(R.id.tvOrderStatus)).setText(order.status + "  •  " + order.seller);
        ((TextView) findViewById(R.id.tvOrderLocation)).setText(order.location);
        ((TextView) findViewById(R.id.tvMapPin)).setText(getString(R.string.map_pin_label, order.location));

        boolean sellerView = "seller".equalsIgnoreCase(order.role);
        boolean completed = "Completed".equalsIgnoreCase(order.status);
        boolean canComplete = sellerView && ("Accepted".equalsIgnoreCase(order.status)
                || "Pickup".equalsIgnoreCase(order.status));
        View completeButton = findViewById(R.id.btnCompleteDeal);
        completeButton.setVisibility(sellerView && (canComplete || completed) ? View.VISIBLE : View.GONE);
        completeButton.setEnabled(canComplete);
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
                        order = order.withStatus("Completed");
                        UiUtils.snackbar(OrderDetailActivity.this.findViewById(android.R.id.content), R.string.toast_transaction_completed);
                        renderOrder();
                    } else {
                        onFailure(call, new Throwable("Update failed"));
                    }
                }

                @Override
                public void onFailure(Call<BaseResponse> call, Throwable t) {
                    v.setEnabled(true);
                    UiUtils.snackbarError(OrderDetailActivity.this.findViewById(android.R.id.content),
                            R.string.toast_could_not_reach_server_try_again);
                }
            });
        });

        // Accept & Schedule Meetup (starts the Safe Meetup flow)
        View btnAcceptOffer = findViewById(R.id.btnAcceptOffer);
        btnAcceptOffer.setVisibility(sellerView && "Offer sent".equalsIgnoreCase(order.status)
                ? View.VISIBLE : View.GONE);
        btnAcceptOffer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            String userId = AppDataStore.userId(this);
            v.setEnabled(false);
            polyGoRepository.updateTransactionStatus(userId, order.id, "accepted", new Callback<BaseResponse>() {
                @Override
                public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        AppDataStore.updateTransactionStatus(OrderDetailActivity.this, order.id, "Accepted");
                        order = order.withStatus("Accepted");
                        UiUtils.snackbar(OrderDetailActivity.this.findViewById(android.R.id.content), R.string.toast_deal_accepted_tracker);
                        renderOrder();
                    } else {
                        onFailure(call, new Throwable("Update failed"));
                    }
                }

                @Override
                public void onFailure(Call<BaseResponse> call, Throwable t) {
                    v.setEnabled(true);
                    UiUtils.snackbarError(OrderDetailActivity.this.findViewById(android.R.id.content),
                            R.string.toast_could_not_reach_server_try_again);
                }
            });
        });

        // Rule 3.3: Link to Live Deal Tracker
        findViewById(R.id.btnTrackDeal).setOnClickListener(v -> {
            HapticManager.swell(this);
            Intent tracker = new Intent(this, DealTrackerActivity.class);
            tracker.putExtra(DealTrackerActivity.EXTRA_TRANSACTION_ID, order.id);
            startActivity(tracker);
        });

        View receiptButton = findViewById(R.id.btnViewReceipt);
        receiptButton.setVisibility(completed ? View.VISIBLE : View.GONE);
        receiptButton.setOnClickListener(v -> {
            Intent i = new Intent(this, ReceiptActivity.class);
            i.putExtra(ReceiptActivity.EXTRA_TRANSACTION_ID, order.id);
            startActivity(i);
        });
    }

}
