package com.poliku.polygoplus.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import com.poliku.polygoplus.databinding.FragmentProfileBinding;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.UiUtils;
import com.poliku.polygoplus.network.ImageUtils;
import java.io.File;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

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
    private FragmentProfileBinding binding;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                uploadProfilePicture(uri);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getContext() == null) return;
        AppDataStore.initialize(getContext());
        
        boolean loggedIn = AppDataStore.isLoggedIn(getContext());
        int memberVisibility = loggedIn ? View.VISIBLE : View.GONE;
        binding.sellerDashboardHeader.setVisibility(memberVisibility);
        binding.sellerDashboardCard.setVisibility(memberVisibility);
        binding.accountSecurityHeader.setVisibility(memberVisibility);
        binding.accountSecurityCard.setVisibility(memberVisibility);
        binding.marketplaceHeader.setVisibility(memberVisibility);
        binding.marketplaceCard.setVisibility(memberVisibility);
        binding.swipeRefreshProfile.setEnabled(loggedIn);
        
        if (loggedIn) {
            binding.tvUserName.setText(AppDataStore.userName(getContext()));
            binding.tvUserRole.setText("PKS " + AppDataStore.userRole(getContext()).toLowerCase(Locale.ROOT));

            String photo = AppDataStore.userProfilePic(getContext());
            if (!photo.isEmpty()) {
                Glide.with(this)
                        .load(photo)
                        .circleCrop()
                        .placeholder(R.mipmap.ic_launcher_foreground)
                        .into(binding.ivProfile);
            }

            binding.ivLogout.setVisibility(View.VISIBLE);
        } else {
            binding.tvUserName.setText(getString(R.string.profile_guest_user));
            binding.tvUserRole.setText(getString(R.string.profile_login_to_access_features));
            binding.ivLogout.setVisibility(View.GONE);
        }

        String bio = AppDataStore.userBio(getContext());
        if (!bio.isEmpty()) {
            binding.tvUserBio.setVisibility(View.VISIBLE);
            binding.tvUserBio.setText(bio);
        }
        binding.ivBioEdit.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        binding.ivBioEdit.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            showEditBioDialog();
        });

        binding.headerProfile.setOnClickListener(v -> {
            if (getContext() == null) return;
            HapticManager.swell(getContext());
            if (loggedIn) profileIntent();
            else startActivity(new Intent(getContext(), LoginActivity.class));
        });

        binding.menuUserProfile.setOnClickListener(v -> {
            if (getContext() == null) return;
            if (loggedIn) profileIntent();
            else startActivity(new Intent(getContext(), LoginActivity.class));
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.profileMain, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.menuChangePassword.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (!loggedIn) {
                if (getContext() != null) startActivity(new Intent(getContext(), LoginActivity.class));
                return;
            }
            
            if (getContext() != null && AppDataStore.isBioLockEnabled(getContext())) {
                if (getActivity() != null) {
                    BioManager.authenticate(getActivity(), getString(R.string.verification_required), getString(R.string.bio_prompt_change_password), new BioManager.AuthCallback() {
                        @Override
                        public void onSuccess() {
                            showChangePasswordDialog();
                        }

                        @Override
                        public void onError(String error) {
                            if (getContext() != null) Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } else {
                showChangePasswordDialog();
            }
        });

        binding.switchBioLock.setChecked(AppDataStore.isBioLockEnabled(getContext()));
        binding.switchBioLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HapticManager.lightTap(buttonView);
            if (isChecked) {
                if (getActivity() != null) {
                    BioManager.authenticate(getActivity(), getString(R.string.bio_title_enable_biometric_lock), getString(R.string.bio_prompt_enable_lock), new BioManager.AuthCallback() {
                        @Override
                        public void onSuccess() {
                            if (getContext() != null) AppDataStore.setBioLockEnabled(getContext(), true);
                        }

                        @Override
                        public void onError(String error) {
                            if (binding != null) binding.switchBioLock.setChecked(false);
                            if (getContext() != null) Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } else {
                if (getContext() != null) AppDataStore.setBioLockEnabled(getContext(), false);
            }
        });

        binding.menuFaqs.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (getContext() != null) startActivity(new Intent(getContext(), HelpActivity.class));
        });
        binding.menuSavedItems.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (getContext() == null) return;
            if (loggedIn) startActivity(new Intent(getContext(), SavedItemsActivity.class));
            else startActivity(new Intent(getContext(), LoginActivity.class));
        });
        binding.menuMyListings.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (getContext() == null) return;
            if (loggedIn) startActivity(new Intent(getContext(), MyListingsActivity.class));
            else startActivity(new Intent(getContext(), LoginActivity.class));
        });
        binding.menuTransactions.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (getContext() == null) return;
            if (loggedIn) startActivity(new Intent(getContext(), TransactionsActivity.class));
            else startActivity(new Intent(getContext(), LoginActivity.class));
        });
        binding.menuNotifications.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (getContext() == null) return;
            if (loggedIn) startActivity(new Intent(getContext(), NotificationsActivity.class));
            else startActivity(new Intent(getContext(), LoginActivity.class));
        });
        binding.menuVerification.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (getContext() == null) return;
            if (loggedIn) startActivity(new Intent(getContext(), VerificationActivity.class));
            else startActivity(new Intent(getContext(), LoginActivity.class));
        });

        binding.menuImpact.setOnClickListener(v -> {
            if (getContext() == null) return;
            HapticManager.swell(getContext());
            startActivity(new Intent(getContext(), SustainabilityDashboardActivity.class));
        });

        BiometricManager bioManager = BiometricManager.from(getContext());
        if (bioManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            binding.menuBioLock.setVisibility(View.GONE);
        }

        binding.menuPrivacy.setOnClickListener(v -> {
            if (getContext() == null) return;
            Intent i = new Intent(getContext(), LegalActivity.class);
            i.putExtra(LegalActivity.EXTRA_PAGE, "privacy");
            startActivity(i);
        });
        binding.menuTerms.setOnClickListener(v -> {
            if (getContext() == null) return;
            Intent i = new Intent(getContext(), LegalActivity.class);
            i.putExtra(LegalActivity.EXTRA_PAGE, "terms");
            startActivity(i);
        });

        binding.ivLogout.setOnClickListener(v -> {
            if (getContext() == null) return;
            AppDataStore.logout(getContext());
            Intent intent = new Intent(getContext(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            }
        });

        binding.swipeRefreshProfile.setColorSchemeResources(R.color.pks_blue, R.color.polygo_purple);
        binding.swipeRefreshProfile.setOnRefreshListener(() -> {
            HapticManager.mediumTap(binding.swipeRefreshProfile);
            refreshProfileData();
        });

        binding.ivProfile.setOnClickListener(v -> {
            if (!loggedIn) return;
            HapticManager.lightTap(v);
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        refreshProfileData();
    }

    private void refreshProfileData() {
        Context context = getContext();
        if (context == null) {
            if (binding != null) binding.swipeRefreshProfile.setRefreshing(false);
            return;
        }

        boolean loggedIn = AppDataStore.isLoggedIn(context);
        if (loggedIn) {
            binding.tvUserName.setText(AppDataStore.userName(context));
            binding.tvUserRole.setText("PKS " + AppDataStore.userRole(context).toLowerCase(Locale.ROOT));
            String photo = AppDataStore.userProfilePic(context);
            if (!photo.isEmpty()) {
                Glide.with(this)
                        .load(photo)
                        .circleCrop()
                        .placeholder(R.mipmap.ic_launcher_foreground)
                        .into(binding.ivProfile);
            }
            binding.ivLogout.setVisibility(View.VISIBLE);
            loadSellerMetrics(binding.getRoot());
        } else {
            binding.swipeRefreshProfile.setRefreshing(false);
        }
    }

    private void uploadProfilePicture(Uri uri) {
        Context context = getContext();
        if (context == null || binding == null) return;

        Uri compressed = ImageUtils.compressImage(context, uri);
        if (compressed == null || compressed.getPath() == null) return;

        File file = new File(compressed.getPath());
        if (!file.exists()) return;

        binding.swipeRefreshProfile.setRefreshing(true);
        polyGoRepository.uploadImage(file, new Callback<PolyGoApi.UploadResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.UploadResponse> call, Response<PolyGoApi.UploadResponse> response) {
                if (!isAdded() || getContext() == null || binding == null) return;
                
                PolyGoApi.UploadResponse body = response.body();
                if (response.isSuccessful() && body != null && body.isSuccess() && body.url != null) {
                    saveProfilePhotoUrl(body.url);
                } else {
                    binding.swipeRefreshProfile.setRefreshing(false);
                    Toast.makeText(getContext(), "Upload failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.UploadResponse> call, Throwable t) {
                if (!isAdded() || binding == null) return;
                binding.swipeRefreshProfile.setRefreshing(false);
                Toast.makeText(getContext(), "Network error during upload", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveProfilePhotoUrl(String photoUrl) {
        Context context = getContext();
        if (context == null) return;

        String userId = AppDataStore.userId(context);
        String name = AppDataStore.userName(context);
        String email = AppDataStore.userEmail(context);
        String mobile = AppDataStore.userMobile(context);
        String bio = AppDataStore.userBio(context);

        polyGoRepository.updateProfile(userId, name, email, mobile, photoUrl, bio, new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (!isAdded() || getContext() == null || binding == null) return;
                binding.swipeRefreshProfile.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    AppDataStore.updateProfile(getContext(), name, email, mobile, photoUrl, bio);
                    Glide.with(ProfileFragment.this)
                            .load(photoUrl)
                            .circleCrop()
                            .into(binding.ivProfile);
                    HapticManager.success(getContext());
                    Toast.makeText(getContext(), "Profile photo updated", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                if (!isAdded() || binding == null) return;
                binding.swipeRefreshProfile.setRefreshing(false);
            }
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) refreshProfileData();
    }

    private void showChangePasswordDialog() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.bottom_sheet_change_password);
        dialog.setOnShowListener(ignored -> {
            View btnSave = dialog.findViewById(R.id.btnSavePassword);
            if (btnSave != null) {
                btnSave.setOnClickListener(button -> {
                    EditText first = dialog.findViewById(R.id.etNewPassword);
                    EditText second = dialog.findViewById(R.id.etConfirmPassword);
                    String password = first == null || first.getText() == null ? "" : first.getText().toString();
                    String confirmation = second == null || second.getText() == null ? "" : second.getText().toString();
                    if (password.length() < 6) {
                        if (first != null) {
                            first.setError(getString(R.string.error_password_min_length));
                        }
                        return;
                    }
                    if (!password.equals(confirmation)) {
                        if (second != null) {
                            second.setError(getString(R.string.error_password_mismatch));
                        }
                        return;
                    }
                    polyGoRepository.updatePassword(password, new Callback<BaseResponse>() {
                        @Override
                        public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                            if (!isAdded() || getContext() == null) return;
                            Context context = getContext();
                            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                                dialog.dismiss();
                                HapticManager.success(context);
                                Toast.makeText(context, R.string.toast_password_changed, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(context, R.string.toast_password_change_failed, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<BaseResponse> call, Throwable t) {
                            if (!isAdded() || getContext() == null) return;
                            Toast.makeText(getContext(), R.string.toast_could_not_reach_server, Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }
        });
        dialog.show();
    }

    private void showEditBioDialog() {
        if (getContext() == null) return;
        EditText input = new EditText(getContext());
        input.setHint(R.string.profile_bio_hint);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        input.setMinLines(1);
        input.setText(AppDataStore.userBio(getContext()));
        input.setSelection(input.getText().length());

        new MaterialAlertDialogBuilder(getContext())
            .setTitle(R.string.bio_edit_title)
            .setView(input)
            .setNegativeButton(R.string.bio_edit_cancel, null)
            .setPositiveButton(R.string.bio_edit_save, (d, w) -> saveBio(input.getText().toString().trim()))
            .show();
    }

    private void saveBio(String bio) {
        if (getContext() == null || !AppDataStore.isLoggedIn(getContext())) return;
        Context context = getContext();
        String name = AppDataStore.userName(context);
        String email = AppDataStore.userEmail(context);
        String mobile = AppDataStore.userMobile(context);
        String photo = AppDataStore.userProfilePic(context);

        if (binding != null) {
            if (bio.isEmpty()) {
                binding.tvUserBio.setVisibility(View.GONE);
            } else {
                binding.tvUserBio.setText(bio);
                binding.tvUserBio.setVisibility(View.VISIBLE);
            }
        }

        polyGoRepository.updateProfile(AppDataStore.userId(context), name, email, mobile, photo, bio, new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (!isAdded() || getContext() == null) return;
                Context ctx = getContext();
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    AppDataStore.updateProfile(ctx, name, email, mobile, photo, bio);
                    HapticManager.success(ctx);
                    Toast.makeText(ctx, R.string.bio_edit_save, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
            }
        });
    }

    private void loadSellerMetrics(View view) {
        if (getContext() == null || !AppDataStore.isLoggedIn(getContext())) return;

        String userId = AppDataStore.userId(getContext());
        polyGoRepository.getSellerMetrics(userId, new Callback<PolyGoApi.SellerMetricsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.SellerMetricsResponse> call, Response<PolyGoApi.SellerMetricsResponse> response) {
                if (!isAdded() || binding == null || getActivity() == null) return;
                
                PolyGoApi.SellerMetricsResponse body = response.body();
                if (binding != null) binding.swipeRefreshProfile.setRefreshing(false);
                if (body != null && body.isSuccess()) {
                    double earnings = body.total_earnings;
                    int active = body.active_listings;
                    int sold = body.items_sold;
                    double rating = body.avg_rating;
                    int trust = (int) body.trust_score;

                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            if (binding == null) return;
                            animateTextNumber(binding.tvTotalEarnings, earnings, "RM %.2f");
                            animateTextNumber(binding.tvItemsSold, sold, "%d");
                            animateTextNumber(binding.tvActiveCount, active, "%d");
                            binding.tvAvgRating.setText(String.format(Locale.getDefault(), "★ %.1f", rating));
                            
                            binding.progressTrust.setProgress(trust, true);
                            binding.tvTrustPercent.setText(trust + "%");
                        });
                    }
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.SellerMetricsResponse> call, Throwable t) {
                if (binding != null) binding.swipeRefreshProfile.setRefreshing(false);
            }
        });
    }

    private void animateTextNumber(TextView tv, Number target, String format) {
        if (tv == null) return;
        tv.setText(String.format(Locale.getDefault(), format, target));
        tv.setAlpha(0f);
        tv.animate().alpha(1f).setDuration(500).start();
    }

    public void profileIntent() {
        if (getContext() == null) return;
        startActivity(new Intent(getContext(), AccountActivity.class));
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }
}
