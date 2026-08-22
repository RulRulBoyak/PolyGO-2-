package com.poliku.polygoplus.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MessagesFragment extends androidx.fragment.app.Fragment {
    private ConversationAdapter adapter;
    private TextView empty;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_messages, container, false);
        AppDataStore.initialize(requireContext());
        RecyclerView recyclerView = view.findViewById(R.id.rvMessages);
        adapter = new ConversationAdapter();
        recyclerView.setAdapter(adapter);
        empty = view.findViewById(R.id.tvEmptyMessages);

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
        NetworkApi.getThreads(userId, new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
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
                }
            }

            @Override
            public void onError(String message) {
                if (adapter != null) {
                    adapter.submit(AppDataStore.getThreads(requireContext()));
                    updateEmpty();
                }
            }
        });
    }

    private void updateEmpty() {
        if (empty != null) empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }
}
