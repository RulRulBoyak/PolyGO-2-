package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.UiUtils;
import dagger.hilt.android.AndroidEntryPoint;

import java.util.Locale;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class DealTrackerActivity extends BaseActivity {

    public static final String EXTRA_TRANSACTION_ID = "transaction_id";

    @Inject PolyGoRepository polyGoRepository;

    private AppDataStore.TransactionRecord order;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deal_tracker);

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        String id = getIntent().getStringExtra(EXTRA_TRANSACTION_ID);
        if (id == null || id.isEmpty()) {
            UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_order_not_found);
            finish();
            return;
        }

        order = AppDataStore.getTransaction(this, id);
        if (order == null) {
            loadFromServer(id);
            return;
        }
        renderOrder();
    }

    private void loadFromServer(String id) {
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
                        if (id.equals(t.id)) {
                            order = AppDataStore.TransactionRecord.fromTransaction(t);
                            runOnUiThread(DealTrackerActivity.this::renderOrder);
                            return;
                        }
                    }
                }
                runOnUiThread(() -> {
                    UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_order_not_found);
                    finish();
                });
            }

            @Override
            public void onFailure(Call<PolyGoApi.TransactionsResponse> call, Throwable t) {
                runOnUiThread(() -> {
                    UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_order_not_found);
                    finish();
                });
            }
        });
    }

    private void renderOrder() {
        ((TextView) findViewById(R.id.tvDealTitle)).setText(order.title);
        ((TextView) findViewById(R.id.tvDealPrice)).setText(order.amount);

        String status = order.status == null ? "" : order.status.toLowerCase(Locale.ROOT);

        int active = 0;
        boolean declined = status.contains("declined");
        boolean cancelled = status.contains("cancelled");
        if (status.contains("offer sent") || declined || cancelled) {
            active = 1;
        } else if (status.contains("accepted")) {
            active = 2;
        } else if (status.contains("pickup")) {
            active = 4;
        } else if (status.contains("completed")) {
            active = 6;
        }

        String[][] steps = {
                {getString(R.string.deal_step_offer_sent), getString(R.string.deal_desc_waiting)},
                {getString(R.string.deal_step_price_agreed), getString(R.string.deal_desc_accepted)},
                {getString(R.string.deal_step_meetup_scheduled), getString(R.string.deal_desc_pickup)},
                {getString(R.string.deal_step_inspection), getString(R.string.deal_desc_inspection)},
                {getString(R.string.deal_step_payment_confirmed), getString(R.string.deal_desc_payment)},
                {getString(R.string.deal_step_deal_closed), getString(R.string.deal_desc_closed)}
        };
        for (int i = 0; i < steps.length; i++) {
            boolean done = i < active;
            if (declined && i == 0) {
                setStep(i + 1, steps[i][0], getString(R.string.deal_desc_declined), done);
            } else if (cancelled && i == 0) {
                setStep(i + 1, steps[i][0], getString(R.string.deal_desc_cancelled), done);
            } else {
                setStep(i + 1, steps[i][0], steps[i][1], done);
            }
        }

        View timerCard = findViewById(R.id.cardTimer);
        if (timerCard != null) {
            boolean inspection = status.contains("pickup");
            timerCard.setVisibility(inspection ? View.VISIBLE : View.GONE);
        }

        View closeBtn = findViewById(R.id.btnCloseDeal);
        boolean sellerView = "seller".equalsIgnoreCase(order.role);
        boolean canClose = sellerView && (status.contains("accepted") || status.contains("pickup"));
        closeBtn.setVisibility(sellerView && (canClose || status.contains("completed"))
                ? View.VISIBLE : View.GONE);
        closeBtn.setEnabled(canClose);

        closeBtn.setOnClickListener(v -> {
            HapticManager.success(this);
            closeDeal();
        });
    }

    private void setStep(int step, String title, String desc, boolean done) {
        View v;
        switch (step) {
            case 1: v = findViewById(R.id.step1); break;
            case 2: v = findViewById(R.id.step2); break;
            case 3: v = findViewById(R.id.step3); break;
            case 4: v = findViewById(R.id.step4); break;
            case 5: v = findViewById(R.id.step5); break;
            default: v = findViewById(R.id.step6); break;
        }
        TextView tvTitle = v.findViewById(R.id.tvStepTitle);
        TextView tvDesc = v.findViewById(R.id.tvStepDesc);
        ImageView ivDot = v.findViewById(R.id.ivStepDot);
        View line = v.findViewById(R.id.stepLine);

        tvTitle.setText(title);
        tvDesc.setText(desc);
        if (done) {
            tvTitle.setTextColor(getResources().getColor(R.color.airbnb_ink, getTheme()));
            tvDesc.setTextColor(getResources().getColor(R.color.pks_blue, getTheme()));
            ivDot.setColorFilter(getResources().getColor(R.color.pks_blue, getTheme()));
            line.setBackgroundColor(getResources().getColor(R.color.pks_blue, getTheme()));
        } else {
            tvTitle.setTextColor(getResources().getColor(R.color.airbnb_muted, getTheme()));
            tvDesc.setTextColor(getResources().getColor(R.color.airbnb_muted, getTheme()));
            ivDot.setColorFilter(getResources().getColor(R.color.grey_200, getTheme()));
            line.setBackgroundColor(getResources().getColor(R.color.grey_200, getTheme()));
        }
    }

    private void closeDeal() {
        String userId = AppDataStore.userId(this);
        if (userId == null || userId.isEmpty()) {
            UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_need_login);
            return;
        }
        findViewById(R.id.btnCloseDeal).setEnabled(false);
        polyGoRepository.updateTransactionStatus(userId, order.id, "completed", new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    AppDataStore.updateTransactionStatus(DealTrackerActivity.this, order.id, "Completed");
                    order = order.withStatus("Completed");
                    UiUtils.snackbar(DealTrackerActivity.this.findViewById(android.R.id.content), R.string.toast_transaction_completed);
                    renderOrder();
                } else {
                    findViewById(R.id.btnCloseDeal).setEnabled(true);
                    String msg = response.body() != null ? response.body().getMessage() : null;
                    if (msg != null && !msg.isEmpty()) {
                        UiUtils.snackbarError(DealTrackerActivity.this.findViewById(android.R.id.content), msg);
                    } else {
                        UiUtils.snackbarError(DealTrackerActivity.this.findViewById(android.R.id.content), R.string.toast_transaction_update_failed);
                    }
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                findViewById(R.id.btnCloseDeal).setEnabled(true);
                UiUtils.snackbarError(DealTrackerActivity.this.findViewById(android.R.id.content), R.string.toast_could_not_reach_server);
            }
        });
    }
}
