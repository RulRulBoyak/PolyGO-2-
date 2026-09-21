package com.poliku.polygoplus.fragments;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.SearchActivity;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ConversationAdapter;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.data.local.entity.ThreadEntity;
import com.poliku.polygoplus.databinding.FragmentMessagesBinding;
import com.poliku.polygoplus.util.Resource;
import com.poliku.polygoplus.viewmodel.MessagesViewModel;
import com.poliku.polygoplus.ui.EmptyStates;
import androidx.lifecycle.ViewModelProvider;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;

@AndroidEntryPoint
public class MessagesFragment extends Fragment {
    @Inject PolyGoRepository polyGoRepository;
    private ConversationAdapter adapter;
    private View empty;
    private MessagesViewModel viewModel;
    private TextWatcher searchWatcher;
    private FragmentMessagesBinding binding;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMessagesBinding.inflate(inflater, container, false);
        Context context = getContext();
        if (context != null) {
            AppDataStore.initialize(context);
        }
        viewModel = new ViewModelProvider(this).get(MessagesViewModel.class);
        binding.rvMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ConversationAdapter(thread -> polyGoRepository.markThreadRead(thread.id));
        binding.rvMessages.setAdapter(adapter);
        empty = binding.tvEmptyMessages;
        if (getContext() != null) {
            EmptyStates.bind(empty, R.drawable.ic_mail_outline, "No messages yet",
                    "Message a seller from a listing to start a campus chat.", "Find a listing",
                    v -> {
                        Context ctx = getContext();
                        if (ctx != null) startActivity(new Intent(ctx, SearchActivity.class));
                    });
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.messageMain, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.buttonCompose.setOnClickListener(v -> {
            Context ctx = getContext();
            if (ctx != null) startActivity(new Intent(ctx, SearchActivity.class));
        });
        searchWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) adapter.setQuery(s.toString());
                updateEmpty();
            }

            @Override public void afterTextChanged(Editable s) { }
        };
        binding.editTextMessageSearch.addTextChangedListener(searchWatcher);

        binding.messageFilters.setOnCheckedChangeListener((group, checkedId) -> {
            if (adapter == null) return;
            if (checkedId == R.id.chipUnread) adapter.setFilter(ConversationAdapter.Filter.UNREAD);
            else if (checkedId == R.id.chipBuying) adapter.setFilter(ConversationAdapter.Filter.BUYING);
            else adapter.setFilter(ConversationAdapter.Filter.ALL);
            updateEmpty();
        });
        
        observeViewModel();
        viewModel.loadThreads();
        return binding.getRoot();
    }

    private void observeViewModel() {
        viewModel.threadsResource.observe(getViewLifecycleOwner(), resource -> {
            if (binding == null || resource == null) return;

            View shimmerView = binding.shimmerMessages.getRoot();
            switch (resource.status) {
                case LOADING:
                    if (shimmerView != null) {
                        shimmerView.setVisibility(View.VISIBLE);
                        if (shimmerView instanceof ShimmerFrameLayout) {
                            ((ShimmerFrameLayout) shimmerView).startShimmer();
                        }
                    }
                    break;

                case SUCCESS:
                    if (shimmerView != null) {
                        if (shimmerView instanceof ShimmerFrameLayout) {
                            ((ShimmerFrameLayout) shimmerView).stopShimmer();
                        }
                        shimmerView.setVisibility(View.GONE);
                    }
                    List<ThreadEntity> list = resource.data;
                    if (adapter != null && list != null) {
                        adapter.submit(list);
                        updateEmpty();
                        binding.rvMessages.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    break;

                case ERROR:
                    if (shimmerView != null) {
                        if (shimmerView instanceof ShimmerFrameLayout) {
                            ((ShimmerFrameLayout) shimmerView).stopShimmer();
                        }
                        shimmerView.setVisibility(View.GONE);
                    }
                    Snackbar.make(binding.getRoot(),
                        "Error: " + resource.message, Snackbar.LENGTH_LONG).show();
                    break;
            }
        });
    }

    @Override public void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.loadThreads();
    }

    @Override public void onDestroyView() {
        if (binding != null && searchWatcher != null) {
            binding.editTextMessageSearch.removeTextChangedListener(searchWatcher);
        }
        searchWatcher = null;
        binding = null;
        super.onDestroyView();
    }

    private void updateEmpty() {
        if (binding == null) return;
        boolean isEmpty = adapter.getItemCount() == 0;
        binding.tvEmptyMessages.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (!isEmpty) binding.rvMessages.setVisibility(View.VISIBLE);
    }
}
