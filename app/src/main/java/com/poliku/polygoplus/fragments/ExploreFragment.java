package com.poliku.polygoplus.fragments;

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

import com.poliku.polygoplus.R;
import com.poliku.polygoplus.ProductDetailActivity;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.network.NetworkApi;
import com.poliku.polygoplus.ui.EmptyStates;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ExploreFragment extends Fragment {
    private ProductCardAdapter adapter;
    private View empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_explore, container, false);
        AppDataStore.initialize(requireContext());
        RecyclerView list = view.findViewById(R.id.rvExplore);

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.explore_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        list.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new ProductCardAdapter(new ArrayList<>(), (a, product) -> {
            android.content.Intent intent = new android.content.Intent(requireContext(), ProductDetailActivity.class);
            intent.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, product.id);
            startActivity(intent);
        });
        list.setAdapter(adapter);
        empty = view.findViewById(R.id.emptyExplore);
        EmptyStates.bind(empty, android.R.drawable.ic_menu_search, "Nothing to explore yet",
                "Listings from PKS students will show up here.", "Browse categories",
                v -> startActivity(new android.content.Intent(requireContext(), com.poliku.polygoplus.CategoryBrowseActivity.class)));
        reloadListings();
        return view;
    }

    private void reloadListings() {
        NetworkApi.getListings(new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                List<AppDataStore.ProductRecord> products = new ArrayList<>();
                JSONArray list = response.optJSONArray("listings");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject o = list.optJSONObject(i);
                        if (o != null) {
                            AppDataStore.ProductRecord p = AppDataStore.ProductRecord.fromJson(o);
                            if (p != null) products.add(p);
                        }
                    }
                }
                if (adapter != null) {
                    adapter.updateData(products);
                    if (empty != null) empty.setVisibility(products.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onError(String message) {
                java.util.List<AppDataStore.ProductRecord> local = AppDataStore.getListings(requireContext());
                if (adapter != null) adapter.updateData(local);
                if (empty != null) empty.setVisibility(local.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }
}
