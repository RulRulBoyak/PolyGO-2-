package com.poliku.polygoplus.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.poliku.polygoplus.AccountActivity;
import com.poliku.polygoplus.MainActivity;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.HelpActivity;
import com.poliku.polygoplus.MyListingsActivity;
import com.poliku.polygoplus.NotificationsActivity;
import com.poliku.polygoplus.SavedItemsActivity;
import com.poliku.polygoplus.TransactionsActivity;
import com.poliku.polygoplus.VerificationActivity;
import com.poliku.polygoplus.data.AppDataStore;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class ProfileFragment extends Fragment {

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
            ((android.widget.TextView)view.findViewById(R.id.tvUserName)).setText(AppDataStore.userName(requireContext()));
            ((android.widget.TextView) view.findViewById(R.id.tvUserRole)).setText("★  " + AppDataStore.userRole(requireContext()));

            String photo = AppDataStore.userProfilePic(requireContext());
            if (!photo.isEmpty()) {
                Glide.with(this)
                        .load(photo)
                        .circleCrop()
                        .placeholder(R.drawable.logo_polygo)
                        .into((android.widget.ImageView) view.findViewById(R.id.ivProfile));
            }

            view.findViewById(R.id.ivLogout).setVisibility(View.VISIBLE);
        } else {
            ((android.widget.TextView)view.findViewById(R.id.tvUserName)).setText("Guest User");
            ((android.widget.TextView) view.findViewById(R.id.tvUserRole)).setText("Log in to access all features");
            view.findViewById(R.id.ivLogout).setVisibility(View.GONE);
        }

        view.findViewById(R.id.headerProfile).setOnClickListener(v -> {
            if (loggedIn) profileIntent();
            else startActivity(new Intent(requireContext(), com.poliku.polygoplus.LoginActivity.class));
        });

        view.findViewById(R.id.menuUserProfile).setOnClickListener(v -> {
            if (loggedIn) profileIntent();
            else startActivity(new Intent(requireContext(), com.poliku.polygoplus.LoginActivity.class));
        });

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.profile_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        view.findViewById(R.id.menuChangePassword).setOnClickListener(v -> {
            if (!loggedIn) {
                startActivity(new Intent(requireContext(), com.poliku.polygoplus.LoginActivity.class));
                return;
            }
            BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
            dialog.setContentView(R.layout.bottom_sheet_change_password);
            dialog.setOnShowListener(ignored -> dialog.findViewById(R.id.btnSavePassword).setOnClickListener(button -> {
                android.widget.EditText first = dialog.findViewById(R.id.etNewPassword);
                android.widget.EditText second = dialog.findViewById(R.id.etConfirmPassword);
                String password = first == null || first.getText() == null ? "" : first.getText().toString();
                String confirmation = second == null || second.getText() == null ? "" : second.getText().toString();
                if (password.length() < 6) { if (first != null) first.setError("Use at least 6 characters"); return; }
                if (!password.equals(confirmation)) { if (second != null) second.setError("Passwords do not match"); return; }
                AppDataStore.changePassword(requireContext(), password); dialog.dismiss(); android.widget.Toast.makeText(requireContext(), "Password changed", android.widget.Toast.LENGTH_SHORT).show();
            }));
            dialog.show();
        });

        view.findViewById(R.id.menuFaqs).setOnClickListener(v -> startActivity(new Intent(requireContext(), HelpActivity.class)));
        view.findViewById(R.id.menuSavedItems).setOnClickListener(v -> {
            if (loggedIn) startActivity(new Intent(requireContext(), SavedItemsActivity.class));
            else startActivity(new Intent(requireContext(), com.poliku.polygoplus.LoginActivity.class));
        });
        view.findViewById(R.id.menuMyListings).setOnClickListener(v -> {
            if (loggedIn) startActivity(new Intent(requireContext(), MyListingsActivity.class));
            else startActivity(new Intent(requireContext(), com.poliku.polygoplus.LoginActivity.class));
        });
        view.findViewById(R.id.menuTransactions).setOnClickListener(v -> {
            if (loggedIn) startActivity(new Intent(requireContext(), TransactionsActivity.class));
            else startActivity(new Intent(requireContext(), com.poliku.polygoplus.LoginActivity.class));
        });
        view.findViewById(R.id.menuNotifications).setOnClickListener(v -> {
            if (loggedIn) startActivity(new Intent(requireContext(), NotificationsActivity.class));
            else startActivity(new Intent(requireContext(), com.poliku.polygoplus.LoginActivity.class));
        });
        view.findViewById(R.id.menuVerification).setOnClickListener(v -> {
            if (loggedIn) startActivity(new Intent(requireContext(), VerificationActivity.class));
            else startActivity(new Intent(requireContext(), com.poliku.polygoplus.LoginActivity.class));
        });
        view.findViewById(R.id.menuPrivacy).setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), com.poliku.polygoplus.LegalActivity.class);
            i.putExtra(com.poliku.polygoplus.LegalActivity.EXTRA_PAGE, "privacy");
            startActivity(i);
        });
        view.findViewById(R.id.menuTerms).setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), com.poliku.polygoplus.LegalActivity.class);
            i.putExtra(com.poliku.polygoplus.LegalActivity.EXTRA_PAGE, "terms");
            startActivity(i);
        });

        view.findViewById(R.id.ivLogout).setOnClickListener(v -> {
            AppDataStore.logout(requireContext());
            Intent intent = new Intent(requireContext(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        // Other menus can be wired here similarly
    }

    public void profileIntent() {
        startActivity(new Intent(requireContext(), AccountActivity.class));
        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}
