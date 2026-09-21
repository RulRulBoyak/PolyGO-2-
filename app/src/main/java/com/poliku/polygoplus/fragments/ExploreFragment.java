package com.poliku.polygoplus.fragments;

import android.app.Activity;
import android.content.Context;
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
import com.poliku.polygoplus.ProductDetailActivity;
import com.poliku.polygoplus.ServicePortfolioActivity;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.databinding.FragmentExploreBinding;
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
import retrofit2.Callback;
import retrofit2.Response;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ExploreFragment extends Fragment {
    @Inject PolyGoRepository repository;
    private ProductCardAdapter adapter;
    private View empty;
    private ExploreViewModel viewModel;
    private ActivityResultLauncher<Intent> detailLauncher;
    @Nullable private View lastProductSharedElement;
    private FragmentExploreBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentExploreBinding.inflate(inflater, container, false);
        Context context = getContext();
        if (context != null) {
            AppDataStore.initialize(context);
        }
        viewModel = new ViewModelProvider(this).get(ExploreViewModel.class);

        ViewCompat.setOnApplyWindowInsetsListener(binding.exploreMain, (v, insets) -> {
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

        if (getContext() != null) {
            binding.rvExplore.setLayoutManager(new GridLayoutManager(getContext(), 2));
        }
        adapter = new ProductCardAdapter(new ArrayList<>(), (a, product, sharedView) -> {
            if (lastProductSharedElement != null) ViewCompat.setTransitionName(lastProductSharedElement, null);
            ViewCompat.setTransitionName(sharedView, "product_image_hero");
            lastProductSharedElement = sharedView;

            Context ctx = getContext();
            if (ctx == null) return;
            Intent intent = new Intent(ctx, ProductDetailActivity.class);
            intent.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, product.id);

            if (getActivity() == null) return;
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    getActivity(), sharedView, "product_image_hero"
            );
            detailLauncher.launch(intent, options);
        });
        binding.rvExplore.setAdapter(adapter);
        empty = binding.emptyExplore;
        if (getContext() != null) {
            EmptyStates.bind(empty, R.drawable.ic_search, "Nothing to explore yet",
                    "Listings from PKS students will show up here.", "Browse categories",
                    v -> {
                        Context ctx = getContext();
                        if (ctx != null) startActivity(new Intent(ctx, CategoryBrowseActivity.class));
                    });
        }
        
        observeViewModel();

        binding.swipeRefreshExplore.setColorSchemeResources(R.color.pks_blue, R.color.polygo_purple, R.color.polygo_teal, R.color.polygo_orange);
        binding.swipeRefreshExplore.setOnRefreshListener(() -> {
            HapticManager.mediumTap(binding.swipeRefreshExplore);
            viewModel.loadListings();
        });

        binding.exploreAppBar.setOnClickListener(v -> {
            Context ctx = getContext();
            if (ctx == null) return;
            HapticManager.swell(ctx);
            startActivity(new Intent(ctx, ServicePortfolioActivity.class));
        });

        binding.exploreTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewModel.setTab(tab.getPosition());
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}

            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        binding.rvExplore.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
        loadMajors();
        return binding.getRoot();
    }

    private void loadMajors() {
        if (binding == null || repository == null) return;
        repository.getMajors(new Callback<PolyGoApi.MajorsResponse>() {
            @Override
            public void onResponse(@NonNull Call<PolyGoApi.MajorsResponse> call, @NonNull Response<PolyGoApi.MajorsResponse> response) {
                if (!response.isSuccessful()) return;
                PolyGoApi.MajorsResponse body = response.body();
                if (body == null || body.majors == null || binding == null || getContext() == null) return;
                binding.chipGroupMajors.post(() -> {
                    if (binding == null || getContext() == null) return;
                    Context ctx = getContext();
                    Chip allChip = new Chip(ctx);
                    allChip.setText(R.string.explore_all_departments);
                    allChip.setCheckable(true);
                    allChip.setChecked(true);
                    allChip.setOnCheckedChangeListener((v, checked) -> {
                        if (checked) {
                            viewModel.setMajor(null);
                            Context c = getContext();
                            if (c != null) AppDataStore.setPickedMajor(c, 0, "");
                        }
                    });
                    binding.chipGroupMajors.addView(allChip);
                    for (PolyGoApi.Major m : body.majors) {
                        if (getContext() == null || binding == null) return;
                        Chip chip = new Chip(getContext());
                        chip.setText(m.name);
                        chip.setCheckable(true);
                        chip.setOnCheckedChangeListener((v, checked) -> {
                            if (checked) {
                                viewModel.setMajor(m.id);
                                Context c = getContext();
                                if (c != null) AppDataStore.setPickedMajor(c, m.id, m.name);
                            }
                        });
                        binding.chipGroupMajors.addView(chip);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<PolyGoApi.MajorsResponse> call, @NonNull Throwable t) {
            }
        });
    }

    private void observeViewModel() {
        viewModel.listingsResource.observe(getViewLifecycleOwner(), resource -> {
            if (binding == null || resource == null) return;

            switch (resource.status) {
                case LOADING:
                    if (!binding.swipeRefreshExplore.isRefreshing()) {
                        binding.shimmerExplore.shimmerView.setVisibility(View.VISIBLE);
                        binding.shimmerExplore.shimmerView.startShimmer();
                        binding.rvExplore.setVisibility(View.GONE);
                    }
                    break;

                case SUCCESS:
                    binding.swipeRefreshExplore.setRefreshing(false);
                    binding.shimmerExplore.shimmerView.stopShimmer();
                    binding.shimmerExplore.shimmerView.setVisibility(View.GONE);

                    List<ListingEntity> list = resource.data;
                    if (adapter != null && list != null) {
                        adapter.updateData(list);
                        binding.rvExplore.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                        if (empty != null) empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                    break;

                case ERROR:
                    binding.swipeRefreshExplore.setRefreshing(false);
                    binding.shimmerExplore.shimmerView.stopShimmer();
                    binding.shimmerExplore.shimmerView.setVisibility(View.GONE);
                    // Restore previously loaded content instead of a blank grid.
                    if (adapter != null && adapter.getItemCount() > 0) {
                        binding.rvExplore.setVisibility(View.VISIBLE);
                        if (empty != null) empty.setVisibility(View.GONE);
                    }
                    // The global NetworkErrorHandler already surfaces the offline
                    // screen; here we only note the failure for cached-data users.
                    Snackbar.make(binding.getRoot(),
                        "Error: " + resource.message, Snackbar.LENGTH_LONG).show();
                    break;
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (lastProductSharedElement != null) {
            ViewCompat.setTransitionName(lastProductSharedElement, null);
            lastProductSharedElement = null;
        }
        binding = null;
        super.onDestroyView();
    }
}
