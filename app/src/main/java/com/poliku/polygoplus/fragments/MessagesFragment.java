package com.poliku.polygoplus.fragments;

import android.content.Intent;
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
    private View shimmer;
    private MessagesViewModel viewModel;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_messages, container, false);
        AppDataStore.initialize(requireContext());
        viewModel = new ViewModelProvider(this).get(MessagesViewModel.class);
        RecyclerView recyclerView = view.findViewById(R.id.rvMessages);
        adapter = new ConversationAdapter(thread -> polyGoRepository.markThreadRead(thread.id));
        recyclerView.setAdapter(adapter);
        empty = view.findViewById(R.id.tvEmptyMessages);
        shimmer = view.findViewById(R.id.shimmerMessages);
        EmptyStates.bind(empty, R.drawable.ic_mail_outline, "No messages yet",
                "Message a seller from a listing to start a campus chat.", "Find a listing",
                v -> startActivity(new Intent(requireContext(), SearchActivity.class)));

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.message_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        view.findViewById(R.id.buttonCompose).setOnClickListener(v -> startActivity(new Intent(requireContext(), SearchActivity.class)));
        EditText search = view.findViewById(R.id.editTextMessageSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.setQuery(s.toString());
                updateEmpty();
            }

            @Override public void afterTextChanged(Editable s) { }
        });

        ChipGroup filters = view.findViewById(R.id.messageFilters);
        filters.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipUnread) adapter.setFilter(ConversationAdapter.Filter.UNREAD);
            else if (checkedId == R.id.chipBuying) adapter.setFilter(ConversationAdapter.Filter.BUYING);
            else adapter.setFilter(ConversationAdapter.Filter.ALL);
            updateEmpty();
        });
        
        observeViewModel();
        viewModel.loadThreads();
        return view;
    }

    private void observeViewModel() {
        viewModel.threadsResource.observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;

            switch (resource.status) {
                case LOADING:
                    if (shimmer != null) {
                        shimmer.setVisibility(View.VISIBLE);
                        if (shimmer instanceof ShimmerFrameLayout) {
                            ((ShimmerFrameLayout) shimmer).startShimmer();
                        }
                    }
                    break;

                case SUCCESS:
                    if (shimmer != null) {
                        if (shimmer instanceof ShimmerFrameLayout) {
                            ((ShimmerFrameLayout) shimmer).stopShimmer();
                        }
                        shimmer.setVisibility(View.GONE);
                    }
                    List<ThreadEntity> list = resource.data;
                    if (adapter != null && list != null) {
                        adapter.submit(list);
                        updateEmpty();
                        View rv = getView() != null ? getView().findViewById(R.id.rvMessages) : null;
                        if (rv != null) rv.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    break;

                case ERROR:
                    if (shimmer != null) {
                        if (shimmer instanceof ShimmerFrameLayout) {
                            ((ShimmerFrameLayout) shimmer).stopShimmer();
                        }
                        shimmer.setVisibility(View.GONE);
                    }
                    Snackbar.make(requireView(),
                        "Error: " + resource.message, Snackbar.LENGTH_LONG).show();
                    break;
            }
        });
    }

    @Override public void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.loadThreads();
    }

    private void updateEmpty() {
        if (empty != null) {
            boolean isEmpty = adapter.getItemCount() == 0;
            empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            View rv = getView() != null ? getView().findViewById(R.id.rvMessages) : null;
            if (rv != null && !isEmpty) rv.setVisibility(View.VISIBLE);
        }
    }
}
