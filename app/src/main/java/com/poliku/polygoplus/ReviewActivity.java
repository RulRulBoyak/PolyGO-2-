package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;

import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.ExitGuard;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.UiUtils;

import androidx.activity.OnBackPressedCallback;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ReviewActivity extends BaseActivity {
    @Inject PolyGoRepository polyGoRepository;
    public static final String EXTRA_SELLER = "seller";
    public static final String EXTRA_TRANSACTION_ID = "transaction_id";
    public static final String EXTRA_LISTING_ID = "listing_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);
        findViewById(R.id.btnBack).setOnClickListener(v -> confirmExit());
        String seller = getIntent().getStringExtra(EXTRA_SELLER);
        String transactionId = getIntent().getStringExtra(EXTRA_TRANSACTION_ID);
        ((TextView) findViewById(R.id.tvReviewSeller)).setText(getString(R.string.review_prompt, seller == null ? getString(R.string.review_this_seller) : seller));

        findViewById(R.id.btnSubmitReview).setOnClickListener(v -> {
            int stars = (int) ((RatingBar) findViewById(R.id.ratingBar)).getRating();
            String comment = ((EditText) findViewById(R.id.etReview)).getText().toString().trim();
            if (stars < 1) {
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_choose_star_rating);
                return;
            }
            String sellerName = seller;
            String txId = transactionId;
            String listingIdExtra = getIntent().getStringExtra(EXTRA_LISTING_ID);
            v.setEnabled(false);

            polyGoRepository.submitReview(AppDataStore.userId(this), sellerName, listingIdExtra, stars, comment, new Callback<BaseResponse>() {
                @Override public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        AppDataStore.addReview(ReviewActivity.this, sellerName, stars, comment);
                        if (txId != null) AppDataStore.markTransactionReviewed(ReviewActivity.this, txId);
                        UiUtils.snackbar(ReviewActivity.this.findViewById(android.R.id.content), R.string.toast_thanks_for_review);
                        finish();
                    } else {
                        String msg = (response.body() != null && response.body().getMessage() != null)
                                ? response.body().getMessage()
                                : getString(R.string.review_verified_buyer_message);
                        HapticManager.error(ReviewActivity.this);
                        UiUtils.snackbarError(ReviewActivity.this.findViewById(android.R.id.content), msg);
                        v.setEnabled(true);
                    }
                }

                @Override public void onFailure(Call<BaseResponse> call, Throwable t) {
                    HapticManager.error(ReviewActivity.this);
                    UiUtils.snackbarError(ReviewActivity.this.findViewById(android.R.id.content),
                            R.string.toast_could_not_reach_server_try_again);
                    v.setEnabled(true);
                }
            });
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });
    }

    private void confirmExit() {
        RatingBar ratingBar = findViewById(R.id.ratingBar);
        EditText etReview = findViewById(R.id.etReview);
        if ((ratingBar != null && ratingBar.getRating() > 0)
                || ExitGuard.anyText(etReview == null ? null : etReview.getText())) {
            ExitGuard.show(this, this::finish);
            return;
        }
        finish();
    }
}
