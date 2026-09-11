package com.poliku.polygoplus.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.CategoryBrowseActivity;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.ErrorStateActivity;
import com.poliku.polygoplus.ProductDetailActivity;
import com.poliku.polygoplus.ServicePortfolioActivity;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.util.Resource;
import androidx.lifecycle.ViewModelProvider;
import com.poliku.polygoplus.viewmodel.ExploreViewModel;
import com.poliku.polygoplus.ui.EmptyStates;
import com.poliku.polygoplus.ui.HapticManager;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Response;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ExploreFragment extends Fragment {
    @Inject PolyGoRepository repository;
    private ProductCardAdapter adapter;
    private View empty;
    private ExploreViewModel viewModel;
    private ActivityResultLauncher<Intent> detailLauncher;

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

        detailLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && adapter != null) {
                String changedId = result.getData().getStringExtra(ProductDetailActivity.RESULT_EXTRA_LISTING_ID);
                if (changedId != null) adapter.notifyStateChanged(changedId);
            }
        });

        list.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new ProductCardAdapter(new ArrayList<>(), (a, product, sharedView) -> {
            Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
            intent.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, product.id);

            // Rule 3.1: Visual Clarity (Shared Element Transition)
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(), sharedView, "product_image_hero"
            );
            detailLauncher.launch(intent, options);
        });
        list.setAdapter(adapter);
        empty = view.findViewById(R.id.emptyExplore);
        EmptyStates.bind(empty, R.drawable.ic_search, "Nothing to explore yet",
                "Listings from PKS students will show up here.", "Browse categories",
                v -> startActivity(new Intent(requireContext(), CategoryBrowseActivity.class)));
        
        observeViewModel(view);

        SwipeRefreshLayout swipeRefresh = view.findViewById(R.id.swipeRefreshExplore);
        swipeRefresh.setColorSchemeResources(R.color.pks_blue);
        swipeRefresh.setOnRefreshListener(() -> {
            HapticManager.mediumTap(swipeRefresh);
            viewModel.loadListings();
        });

        view.findViewById(R.id.exploreAppBar).setOnClickListener(v -> {
            // Secret entry to Student Creator Hub by tapping the app bar title area
            HapticManager.swell(requireContext());
            startActivity(new Intent(requireContext(), ServicePortfolioActivity.class));
        });

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewModel.setTab(tab.getPosition());
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}

            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) { // Scrolling down
                    GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        int visibleItemCount = layoutManager.getChildCount();
                        int totalItemCount = layoutManager.getItemCount();
                        int pastVisibleItems = layoutManager.findFirstVisibleItemPosition();

                        if ((visibleItemCount + pastVisibleItems) >= totalItemCount) {
                            viewModel.loadMore();
                        }
                    }
                }
            }
        });

        viewModel.loadListings();
        loadMajors(view);
        return view;
    }

    private void loadMajors(View view) {
        ChipGroup chipGroupMajors = view.findViewById(R.id.chipGroupMajors);
        if (chipGroupMajors == null || repository == null) return;
        repository.getMajors(new retrofit2.Callback<PolyGoApi.MajorsResponse>() {
            @Override
            public void onResponse(@NonNull Call<PolyGoApi.MajorsResponse> call, @NonNull Response<PolyGoApi.MajorsResponse> response) {
                PolyGoApi.MajorsResponse body = response.body();
                if (body == null || body.majors == null || getContext() == null) return;
                chipGroupMajors.post(() -> {
                    Chip allChip = new Chip(requireContext());
                    allChip.setText(R.string.explore_all_departments);
                    allChip.setCheckable(true);
                    allChip.setChecked(true);
                    allChip.setOnCheckedChangeListener((v, checked) -> {
                        if (checked) viewModel.setMajor(null);
                    });
                    chipGroupMajors.addView(allChip);
                    for (PolyGoApi.Major m : body.majors) {
                        Chip chip = new Chip(requireContext());
                        chip.setText(m.name);
                        chip.setCheckable(true);
                        chip.setOnCheckedChangeListener((v, checked) -> {
                            if (checked) viewModel.setMajor(m.id);
                        });
                        chipGroupMajors.addView(chip);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<PolyGoApi.MajorsResponse> call, @NonNull Throwable t) {
            }
        });
    }

    private void observeViewModel(View view) {
        viewModel.listingsResource.observe(getViewLifecycleOwner(), resource -> {
            RecyclerView rv = view.findViewById(R.id.rvExplore);
            SwipeRefreshLayout swipeRefresh = view.findViewById(R.id.swipeRefreshExplore);
            View shimmer = view.findViewById(R.id.shimmerExplore);

            if (resource == null) return;

            switch (resource.status) {
                case LOADING:
                    if (swipeRefresh != null && !swipeRefresh.isRefreshing()) {
                        if (shimmer != null) {
                            shimmer.setVisibility(View.VISIBLE);
                            if (shimmer instanceof ShimmerFrameLayout) {
                                ((ShimmerFrameLayout) shimmer).startShimmer();
                            }
                        }
                        if (rv != null) rv.setVisibility(View.GONE);
                    }
                    break;

                case SUCCESS:
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    if (shimmer != null) {
                        shimmer.setVisibility(View.GONE);
                        if (shimmer instanceof ShimmerFrameLayout) {
                            ((ShimmerFrameLayout) shimmer).stopShimmer();
                        }
                    }

                    List<ListingEntity> list = resource.data;
                    if (adapter != null && list != null) {
                        adapter.updateData(list);
                        if (rv != null) rv.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                        if (empty != null) empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                    break;

                case ERROR:
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    if (shimmer != null) {
                        shimmer.setVisibility(View.GONE);
                        if (shimmer instanceof ShimmerFrameLayout) {
                            ((ShimmerFrameLayout) shimmer).stopShimmer();
                        }
                    }
                    if (AppDataStore.getListings(requireContext()).isEmpty()) {
                        startActivity(new Intent(requireContext(), ErrorStateActivity.class));
                    } else {
                        Snackbar.make(view,
                            "Error: " + resource.message, Snackbar.LENGTH_LONG).show();
                    }
                    break;
            }
        });
    }
}
