package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.HapticManager;

import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class OfferSheetDialogFragment extends BottomSheetDialogFragment {

    private static final String ARG_PRODUCT_ID = "product_id";
    private static final String ARG_OWNER_ID = "owner_id";
    private static final String ARG_TITLE = "title";
    private static final String ARG_PRICE = "price";
    private static final String ARG_SELLER = "seller";

    @Inject PolyGoRepository polyGoRepository;

    private String productId, ownerId, title, price, seller;
    private EditText etAmount;
    private TextInputLayout tilAmount;
    private boolean sending;

    public static OfferSheetDialogFragment newInstance(String productId, String ownerId, String title, String price, String seller) {
        OfferSheetDialogFragment fragment = new OfferSheetDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PRODUCT_ID, productId);
        args.putString(ARG_OWNER_ID, ownerId);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_PRICE, price);
        args.putString(ARG_SELLER, seller);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_offer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            productId = getArguments().getString(ARG_PRODUCT_ID);
            ownerId = getArguments().getString(ARG_OWNER_ID);
            title = getArguments().getString(ARG_TITLE);
            price = getArguments().getString(ARG_PRICE);
            seller = getArguments().getString(ARG_SELLER);
        }
        if (seller == null) seller = "";

        TextView subtitle = view.findViewById(R.id.tvOfferSubtitle);
        subtitle.setText(getString(R.string.offer_sheet_subtitle, seller));

        etAmount = view.findViewById(R.id.etOfferAmount);
        tilAmount = view.findViewById(R.id.tilOfferAmount);

        ChipGroup chipGroup = view.findViewById(R.id.chipGroupOffers);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip chip = group.findViewById(checkedIds.get(0));
            if (chip == null) return;
            double base = parsePrice(price);
            double value = base;
            int id = chip.getId();
            if (id == R.id.chipMinus5) value = base * 0.95;
            else if (id == R.id.chipMinus10) value = base * 0.90;
            etAmount.setText(String.format(Locale.US, "%.2f", value));
            etAmount.setSelection(etAmount.getText().length());
            tilAmount.setError(null);
            HapticManager.lightTap(chip);
        });

        view.findViewById(R.id.btnOfferCancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btnOfferSend).setOnClickListener(v -> sendOffer());
    }

    private void sendOffer() {
        if (sending) return;
        String text = etAmount.getText() == null ? "" : etAmount.getText().toString().trim();
        double amount;
        try {
            amount = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            amount = 0;
        }
        if (amount <= 0) {
            tilAmount.setError(getString(R.string.offer_sheet_error_amount));
            HapticManager.error(requireContext());
            return;
        }

        sending = true;
        viewSendState(false);
        String amountText = String.format(Locale.US, "%.2f", amount);
        String userId = AppDataStore.userId(requireContext());

        polyGoRepository.addTransaction(userId, productId, ownerId, amountText, new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    HapticManager.success(requireContext());
                    AppDataStore.addTransaction(requireContext(), productId, title, "RM " + amountText);
                    Toast.makeText(requireContext(), R.string.offer_sheet_sent, Toast.LENGTH_LONG).show();
                    dismiss();
                } else {
                    onFailure(call, new Throwable("Transaction failed"));
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                if (!isAdded()) return;
                AppDataStore.addTransaction(requireContext(), productId, title, "RM " + amountText);
                Toast.makeText(requireContext(), R.string.offer_sheet_local_only, Toast.LENGTH_SHORT).show();
                dismiss();
            }
        });
    }

    private void viewSendState(boolean enabled) {
        if (getView() != null) getView().findViewById(R.id.btnOfferSend).setEnabled(enabled);
    }

    private double parsePrice(String raw) {
        if (raw == null) return 0;
        try {
            return Double.parseDouble(raw.trim().replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}