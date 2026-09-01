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

import com.google.android.material.tabs.TabLayout;
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
    private final List<AppDataStore.ProductRecord> allItems = new ArrayList<>();
    private int currentTab = 0; // 0 for Products, 1 for Services

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_explore, container, false);
        AppDataStore.initialize(requireContext());
        RecyclerView list = view.findViewById(R.id.rvExplore);
        TabLayout tabs = view.findViewById(R.id.exploreTabs);

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
        
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { currentTab = tab.getPosition(); filterItems(); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        reloadListings();
        return view;
    }

    private void reloadListings() {
        NetworkApi.getListings(new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                allItems.clear();
                JSONArray list = response.optJSONArray("listings");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject o = list.optJSONObject(i);
                        if (o != null) {
                            AppDataStore.ProductRecord p = AppDataStore.ProductRecord.fromJson(o);
                            if (p != null) allItems.add(p);
                        }
                    }
                }
                filterItems();
            }

            @Override
            public void onError(String message) {
                allItems.clear();
                allItems.addAll(AppDataStore.getListings(requireContext()));
                filterItems();
            }
        });
    }

    private void filterItems() {
        List<AppDataStore.ProductRecord> filtered = new ArrayList<>();
        String[] serviceCats = {"Repair", "Printing", "Delivery", "Cleaning", "Lessons", "Laundry", "Services"};
        
        for (AppDataStore.ProductRecord item : allItems) {
            boolean isService = false;
            for (String cat : serviceCats) {
                if (cat.equalsIgnoreCase(item.category)) { isService = true; break; }
            }
            
            if (currentTab == 1 && isService) filtered.add(item);
            else if (currentTab == 0 && !isService) filtered.add(item);
        }

        if (adapter != null) {
            adapter.updateData(filtered);
            if (empty != null) empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }
}
