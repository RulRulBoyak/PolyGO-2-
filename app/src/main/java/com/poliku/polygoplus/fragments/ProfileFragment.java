package com.poliku.polygoplus.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricManager;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.poliku.polygoplus.AccountActivity;
import com.poliku.polygoplus.LegalActivity;
import com.poliku.polygoplus.LoginActivity;
import com.poliku.polygoplus.MainActivity;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.HelpActivity;
import com.poliku.polygoplus.MyListingsActivity;
import com.poliku.polygoplus.NotificationsActivity;
import com.poliku.polygoplus.SavedItemsActivity;
import com.poliku.polygoplus.SustainabilityDashboardActivity;
import com.poliku.polygoplus.TransactionsActivity;
import com.poliku.polygoplus.VerificationActivity;
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

import com.poliku.polygoplus.ui.BioManager;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONObject;

import java.util.Locale;

@AndroidEntryPoint
public class ProfileFragment extends Fragment {
    @Inject PolyGoRepository polyGoRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppDataStore.initialize(requireContext());
        
        boolean loggedIn = AppDataStore.isLoggedIn(requireContext());
        
        if (loggedIn) {
            ((TextView)view.findViewById(R.id.tvUserName)).setText(AppDataStore.userName(requireContext()));
            ((TextView) view.findViewById(R.id.tvUserRole)).setText("★  " + AppDataStore.userRole(requireContext()));

            String photo = AppDataStore.userProfilePic(requireContext());
            if (!photo.isEmpty()) {
                Glide.with(this)
                        .load(photo)
                        .circleCrop()
                        .placeholder(R.mipmap.ic_launcher_foreground)
                        .into((ImageView) view.findViewById(R.id.ivProfile));
            }

            view.findViewById(R.id.ivLogout).setVisibility(View.VISIBLE);
        } else {
            ((TextView)view.findViewById(R.id.tvUserName)).setText("Guest User");
            ((TextView) view.findViewById(R.id.tvUserRole)).setText("Log in to access all features");
            view.findViewById(R.id.ivLogout).setVisibility(View.GONE);
        }

        TextView tvUserBio = view.findViewById(R.id.tvUserBio);
        String bio = AppDataStore.userBio(requireContext());
        if (!bio.isEmpty()) {
            tvUserBio.setVisibility(View.VISIBLE);
            tvUserBio.setText(bio);
        }
        ImageView ivBioEdit = view.findViewById(R.id.ivBioEdit);
        ivBioEdit.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        ivBioEdit.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            showEditBioDialog();
        });

        view.findViewById(R.id.headerProfile).setOnClickListener(v -> {
            HapticManager.swell(requireContext());
            if (loggedIn) profileIntent();
            else startActivity(new Intent(requireContext(), LoginActivity.class));
        });

        view.findViewById(R.id.menuUserProfile).setOnClickListener(v -> {
            if (loggedIn) profileIntent();
            else startActivity(new Intent(requireContext(), LoginActivity.class));
        });

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.profile_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        view.findViewById(R.id.menuChangePassword).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (!loggedIn) {
                startActivity(new Intent(requireContext(), LoginActivity.class));
                return;
            }
            
            if (AppDataStore.isBioLockEnabled(requireContext())) {
                BioManager.authenticate(requireActivity(), "Verification Required", "Confirm identity to change password", new BioManager.AuthCallback() {
                    @Override
                    public void onSuccess() {
                        showChangePasswordDialog();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                showChangePasswordDialog();
            }
        });

        MaterialSwitch bioSwitch = view.findViewById(R.id.switchBioLock);
        bioSwitch.setChecked(AppDataStore.isBioLockEnabled(requireContext()));
        bioSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HapticManager.lightTap(buttonView);
            if (isChecked) {
                BioManager.authenticate(requireActivity(), "Enable Biometric Lock", "Verify to enable security layer", new BioManager.AuthCallback() {
                    @Override
                    public void onSuccess() {
                        AppDataStore.setBioLockEnabled(requireContext(), true);
                    }

                    @Override
                    public void onError(String error) {
                        bioSwitch.setChecked(false);
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                AppDataStore.setBioLockEnabled(requireContext(), false);
            }
        });

        view.findViewById(R.id.menuFaqs).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            startActivity(new Intent(requireContext(), HelpActivity.class));
        });
        view.findViewById(R.id.menuSavedItems).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (loggedIn) startActivity(new Intent(requireContext(), SavedItemsActivity.class));
            else startActivity(new Intent(requireContext(), LoginActivity.class));
        });
        view.findViewById(R.id.menuMyListings).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (loggedIn) startActivity(new Intent(requireContext(), MyListingsActivity.class));
            else startActivity(new Intent(requireContext(), LoginActivity.class));
        });
        view.findViewById(R.id.menuTransactions).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (loggedIn) startActivity(new Intent(requireContext(), TransactionsActivity.class));
            else startActivity(new Intent(requireContext(), LoginActivity.class));
        });
        view.findViewById(R.id.menuNotifications).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (loggedIn) startActivity(new Intent(requireContext(), NotificationsActivity.class));
            else startActivity(new Intent(requireContext(), LoginActivity.class));
        });
        view.findViewById(R.id.menuVerification).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (loggedIn) startActivity(new Intent(requireContext(), VerificationActivity.class));
            else startActivity(new Intent(requireContext(), LoginActivity.class));
        });

        // NEW: Sustainability Dashboard entry
        view.findViewById(R.id.menuImpact).setOnClickListener(v -> {
            HapticManager.swell(requireContext());
            startActivity(new Intent(requireContext(), SustainabilityDashboardActivity.class));
        });

        // Hide Biometric option if hardware is missing
        BiometricManager bioManager = BiometricManager.from(requireContext());
        if (bioManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            view.findViewById(R.id.menuBioLock).setVisibility(View.GONE);
        }

        view.findViewById(R.id.menuPrivacy).setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), LegalActivity.class);
            i.putExtra(LegalActivity.EXTRA_PAGE, "privacy");
            startActivity(i);
        });
        view.findViewById(R.id.menuTerms).setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), LegalActivity.class);
            i.putExtra(LegalActivity.EXTRA_PAGE, "terms");
            startActivity(i);
        });

        view.findViewById(R.id.ivLogout).setOnClickListener(v -> {
            AppDataStore.logout(requireContext());
            Intent intent = new Intent(requireContext(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        loadSellerMetrics(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) loadSellerMetrics(getView());
    }

    private void showChangePasswordDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(R.layout.bottom_sheet_change_password);
        dialog.setOnShowListener(ignored -> dialog.findViewById(R.id.btnSavePassword).setOnClickListener(button -> {
            EditText first = dialog.findViewById(R.id.etNewPassword);
            EditText second = dialog.findViewById(R.id.etConfirmPassword);
            String password = first == null || first.getText() == null ? "" : first.getText().toString();
            String confirmation = second == null || second.getText() == null ? "" : second.getText().toString();
            if (password.length() < 6) {
                if (first != null) {
                    first.setError("Use at least 6 characters");
                }
                return;
            }
            if (!password.equals(confirmation)) {
                if (second != null) {
                    second.setError("Passwords do not match");
                }
                return;
            }
            polyGoRepository.updatePassword(password, new retrofit2.Callback<BaseResponse>() {
                @Override
                public void onResponse(retrofit2.Call<BaseResponse> call, retrofit2.Response<BaseResponse> response) {
                    if (!isAdded()) return;
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        dialog.dismiss();
                        HapticManager.success(requireContext());
                        Toast.makeText(requireContext(), "Password changed", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Failed to change password", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<BaseResponse> call, Throwable t) {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Could not reach server", Toast.LENGTH_SHORT).show();
                }
            });
        }));
        dialog.show();
    }

    private void showEditBioDialog() {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.profile_bio_hint);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        input.setMinLines(1);
        input.setText(AppDataStore.userBio(requireContext()));
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.bio_edit_title)
            .setView(input)
            .setNegativeButton(R.string.bio_edit_cancel, null)
            .setPositiveButton(R.string.bio_edit_save, (d, w) -> saveBio(input.getText().toString().trim()))
            .show();
    }

    private void saveBio(String bio) {
        if (!AppDataStore.isLoggedIn(requireContext())) return;
        String name = AppDataStore.userName(requireContext());
        String email = AppDataStore.userEmail(requireContext());
        String mobile = AppDataStore.userMobile(requireContext());
        String photo = AppDataStore.userProfilePic(requireContext());

        if (getView() != null) {
            TextView tv = getView().findViewById(R.id.tvUserBio);
            if (bio.isEmpty()) {
                tv.setVisibility(View.GONE);
            } else {
                tv.setText(bio);
                tv.setVisibility(View.VISIBLE);
            }
        }

        polyGoRepository.updateProfile(AppDataStore.userId(requireContext()), name, email, mobile, photo, bio, new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    AppDataStore.updateProfile(requireContext(), name, email, mobile, photo, bio);
                    HapticManager.success(requireContext());
                    Toast.makeText(requireContext(), R.string.bio_edit_save, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
            }
        });
    }

    private void loadSellerMetrics(View view) {
        if (!AppDataStore.isLoggedIn(requireContext())) return;

        String userId = AppDataStore.userId(requireContext());
        polyGoRepository.getSellerMetrics(userId, new Callback<PolyGoApi.SellerMetricsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.SellerMetricsResponse> call, Response<PolyGoApi.SellerMetricsResponse> response) {
                if (!isAdded()) return;
                
                PolyGoApi.SellerMetricsResponse body = response.body();
                if (body != null && body.isSuccess()) {
                    double earnings = 0;
                    try {
                        earnings = Double.parseDouble(body.total_earnings);
                    } catch (Exception ignored) {
                    }
                    
                    int active = body.active_listings;
                    int sold = body.items_sold;
                    double rating = body.avg_rating;
                    int trust = (int) body.trust_score;

                    double finalEarnings = earnings;
                    requireActivity().runOnUiThread(() -> {
                        animateTextNumber((TextView) view.findViewById(R.id.tvTotalEarnings), finalEarnings, "RM %.2f");
                        animateTextNumber((TextView) view.findViewById(R.id.tvItemsSold), sold, "%d");
                        animateTextNumber((TextView) view.findViewById(R.id.tvActiveCount), active, "%d");
                        ((TextView) view.findViewById(R.id.tvAvgRating)).setText(String.format(Locale.getDefault(), "★ %.1f", rating));
                        
                        LinearProgressIndicator progress = view.findViewById(R.id.progressTrust);
                        progress.setProgress(trust, true);
                        ((TextView) view.findViewById(R.id.tvTrustPercent)).setText(trust + "%");
                    });
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.SellerMetricsResponse> call, Throwable t) {}
        });
    }

    private void animateTextNumber(TextView tv, Number target, String format) {
        tv.setText(String.format(Locale.getDefault(), format, target));
        tv.setAlpha(0f);
        tv.animate().alpha(1f).setDuration(500).start();
    }

    public void profileIntent() {
        startActivity(new Intent(requireContext(), AccountActivity.class));
        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}
