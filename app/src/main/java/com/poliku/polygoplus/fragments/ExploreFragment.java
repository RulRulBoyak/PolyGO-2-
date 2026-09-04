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
import androidx.lifecycle.ViewModelProvider;
import com.poliku.polygoplus.viewmodel.ExploreViewModel;
import com.poliku.polygoplus.ui.EmptyStates;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ExploreFragment extends Fragment {
    private ProductCardAdapter adapter;
    private View empty;
    private ExploreViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_explore, container, false);
        AppDataStore.initialize(requireContext());
        viewModel = new ViewModelProvider(this).get(ExploreViewModel.class);
        RecyclerView list = view.findViewById(R.id.rvExplore);
        TabLayout tabs = view.findViewById(R.id.exploreTabs);

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.explore_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        list.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new ProductCardAdapter(new ArrayList<>(), (a, product, sharedView) -> {
            android.content.Intent intent = new android.content.Intent(requireContext(), ProductDetailActivity.class);
            intent.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, product.id);

            // Rule 3.1: Visual Clarity (Shared Element Transition)
            androidx.core.app.ActivityOptionsCompat options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(), sharedView, "product_image_hero"
            );
            startActivity(intent, options.toBundle());
        });
        list.setAdapter(adapter);
        empty = view.findViewById(R.id.emptyExplore);
        EmptyStates.bind(empty, android.R.drawable.ic_menu_search, "Nothing to explore yet",
                "Listings from PKS students will show up here.", "Browse categories",
                v -> startActivity(new android.content.Intent(requireContext(), com.poliku.polygoplus.CategoryBrowseActivity.class)));
        
        observeViewModel(view);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { viewModel.setTab(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        viewModel.loadListings();
        return view;
    }

    private void observeViewModel(View view) {
        viewModel.filteredItems.observe(getViewLifecycleOwner(), list -> {
            if (adapter != null) {
                adapter.updateData(list);
                RecyclerView rv = view.findViewById(R.id.rvExplore);
                if (rv != null) rv.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                if (empty != null) empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            View shimmer = view.findViewById(R.id.shimmerExplore);
            if (shimmer == null) return;
            RecyclerView rv = view.findViewById(R.id.rvExplore);
            
            if (isLoading) {
                shimmer.setVisibility(View.VISIBLE);
                if (shimmer instanceof com.facebook.shimmer.ShimmerFrameLayout) {
                    ((com.facebook.shimmer.ShimmerFrameLayout) shimmer).startShimmer();
                }
                if (rv != null) rv.setVisibility(View.GONE);
            } else {
                shimmer.setVisibility(View.GONE);
                if (shimmer instanceof com.facebook.shimmer.ShimmerFrameLayout) {
                    ((com.facebook.shimmer.ShimmerFrameLayout) shimmer).stopShimmer();
                }
            }
        });
    }
}
