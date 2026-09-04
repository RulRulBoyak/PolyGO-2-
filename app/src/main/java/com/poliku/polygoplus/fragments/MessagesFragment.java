package com.poliku.polygoplus.fragments;

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
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.SearchActivity;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ConversationAdapter;
import com.poliku.polygoplus.network.NetworkApi;
import com.poliku.polygoplus.ui.EmptyStates;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MessagesFragment extends androidx.fragment.app.Fragment {
    private ConversationAdapter adapter;
    private View empty;
    private View shimmer;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_messages, container, false);
        AppDataStore.initialize(requireContext());
        RecyclerView recyclerView = view.findViewById(R.id.rvMessages);
        adapter = new ConversationAdapter();
        recyclerView.setAdapter(adapter);
        empty = view.findViewById(R.id.tvEmptyMessages);
        shimmer = view.findViewById(R.id.shimmerMessages);
        EmptyStates.bind(empty, android.R.drawable.ic_dialog_email, "No messages yet",
                "Message a seller from a listing to start a campus chat.", "Find a listing",
                v -> startActivity(new android.content.Intent(requireContext(), SearchActivity.class)));

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.message_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        view.findViewById(R.id.buttonCompose).setOnClickListener(v -> startActivity(new android.content.Intent(requireContext(), SearchActivity.class)));
        EditText search = view.findViewById(R.id.editTextMessageSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { adapter.setQuery(s.toString()); updateEmpty(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        ChipGroup filters = view.findViewById(R.id.messageFilters);
        filters.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipUnread) adapter.setFilter(ConversationAdapter.Filter.UNREAD);
            else if (checkedId == R.id.chipBuying) adapter.setFilter(ConversationAdapter.Filter.BUYING);
            else adapter.setFilter(ConversationAdapter.Filter.ALL);
            updateEmpty();
        });
        loadThreads();
        return view;
    }

    @Override public void onResume() {
        super.onResume();
        if (adapter != null) loadThreads();
    }

    private void loadThreads() {
        String userId = AppDataStore.userId(requireContext());
        if (shimmer != null) {
            shimmer.setVisibility(View.VISIBLE);
            if (shimmer instanceof com.facebook.shimmer.ShimmerFrameLayout) {
                ((com.facebook.shimmer.ShimmerFrameLayout) shimmer).startShimmer();
            }
        }
        NetworkApi.getThreads(userId, new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (shimmer != null) {
                    if (shimmer instanceof com.facebook.shimmer.ShimmerFrameLayout) {
                        ((com.facebook.shimmer.ShimmerFrameLayout) shimmer).stopShimmer();
                    }
                    shimmer.setVisibility(View.GONE);
                }
                List<AppDataStore.ThreadRecord> result = new ArrayList<>();
                JSONArray list = response.optJSONArray("threads");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject o = list.optJSONObject(i);
                        if (o != null) result.add(AppDataStore.ThreadRecord.fromJson(o));
                    }
                }
                if (adapter != null) {
                    adapter.submit(result);
                    updateEmpty();
                    View rv = getView() != null ? getView().findViewById(R.id.rvMessages) : null;
                    if (rv != null) rv.setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                if (shimmer != null) {
                    if (shimmer instanceof com.facebook.shimmer.ShimmerFrameLayout) {
                        ((com.facebook.shimmer.ShimmerFrameLayout) shimmer).stopShimmer();
                    }
                    shimmer.setVisibility(View.GONE);
                }
                if (adapter != null) {
                    List<AppDataStore.ThreadRecord> local = AppDataStore.getThreads(requireContext());
                    adapter.submit(local);
                    updateEmpty();
                    View rv = getView() != null ? getView().findViewById(R.id.rvMessages) : null;
                    if (rv != null) rv.setVisibility(local.isEmpty() ? View.GONE : View.VISIBLE);
                }
            }
        });
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
