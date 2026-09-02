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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.SearchActivity;
import com.poliku.polygoplus.AccountActivity;
import com.poliku.polygoplus.ProductDetailActivity;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.databinding.FragmentHomeBinding;
import com.poliku.polygoplus.databinding.ItemCategoryBinding;
import androidx.lifecycle.ViewModelProvider;
import com.poliku.polygoplus.viewmodel.HomeViewModel;
import com.poliku.polygoplus.network.NetworkApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private ProductCardAdapter productAdapter;
    private HomeViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AppDataStore.initialize(requireContext());
        setupHeader();
        setupCategories();
        setupProducts();
        setupSearchActions();
        
        observeViewModel();
        viewModel.loadProducts();

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.appBar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });
    }

    private void observeViewModel() {
        viewModel.products.observe(getViewLifecycleOwner(), list -> {
            if (productAdapter != null) productAdapter.updateData(list);
            if (binding != null) {
                boolean isEmpty = list == null || list.isEmpty();
                binding.recyclerViewProducts.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            }
        });

        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            if (binding == null) return;
            if (isLoading) {
                binding.shimmerMarket.shimmerView.setVisibility(View.VISIBLE);
                binding.shimmerMarket.shimmerView.startShimmer();
                binding.recyclerViewProducts.setVisibility(View.GONE);
            } else {
                binding.shimmerMarket.shimmerView.stopShimmer();
                binding.shimmerMarket.shimmerView.setVisibility(View.GONE);
            }
        });
    }

    private void setupHeader() {
        boolean loggedIn = AppDataStore.isLoggedIn(requireContext());
        String name = AppDataStore.userName(requireContext());
        String firstName = name.split(" ")[0];
        
        String hourGreeting;
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12) hourGreeting = "Good morning";
        else if (hour < 18) hourGreeting = "Good afternoon";
        else hourGreeting = "Good evening";

        if (!loggedIn || name == null || name.trim().isEmpty() || "PolyGo member".equals(name)) {
            binding.tvGreeting.setText(hourGreeting + "!");
        } else {
            binding.tvGreeting.setText(hourGreeting + ",\n" + firstName);
        }

        String photo = AppDataStore.userProfilePic(requireContext());
        if (!photo.isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(photo)
                    .circleCrop()
                    .into(binding.ivProfilePic);
            binding.ivProfilePic.setPadding(0, 0, 0, 0); // Remove padding if real photo exists
        }

        binding.ivProfilePic.setOnClickListener(v -> {
            if (AppDataStore.isLoggedIn(requireContext())) {
                Intent intent = new Intent(requireContext(), AccountActivity.class);
                androidx.core.app.ActivityOptionsCompat options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                        requireActivity(), binding.ivProfilePic, "profile_pic_hero"
                );
                startActivity(intent, options.toBundle());
            } else {
                startActivity(new Intent(requireContext(), com.poliku.polygoplus.LoginActivity.class));
            }
        });
    }

    private void setupCategories() {
        List<Category> categories = new ArrayList<>();
        categories.add(new Category("Food", R.drawable.ic_category_food));
        categories.add(new Category("Drinks", R.drawable.ic_category_drink));
        categories.add(new Category("Tech", R.drawable.ic_category_tech));
        categories.add(new Category("Books", R.drawable.ic_category_books));
        categories.add(new Category("Repair", R.drawable.ic_category_repair));
        categories.add(new Category("Others", R.drawable.ic_nav_explore));

        binding.recyclerViewCategories.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerViewCategories.setAdapter(new CategoryAdapter(categories));
    }

    private void setupProducts() {
        productAdapter = new ProductCardAdapter(new ArrayList<>(), (adapter, product, sharedView) -> {
            Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
            intent.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, product.id);

            // Rule 3.1: Visual Clarity (Shared Element Transition)
            androidx.core.app.ActivityOptionsCompat options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(), sharedView, "product_image_hero"
            );
            startActivity(intent, options.toBundle());
        });
        binding.recyclerViewProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.recyclerViewProducts.setAdapter(productAdapter);
    }

    private void setupSearchActions() {
        View.OnClickListener openSearch = v -> {
            Intent intent = new Intent(requireContext(), SearchActivity.class);
            androidx.core.app.ActivityOptionsCompat options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(), binding.searchBarCard, "search_bar_hero"
            );
            startActivity(intent, options.toBundle());
        };
        binding.searchBarCard.setOnClickListener(openSearch);
        binding.btnExploreNow.setOnClickListener(v -> startActivity(new Intent(requireContext(), com.poliku.polygoplus.CategoryBrowseActivity.class)));
        binding.tvSeeAllProducts.setOnClickListener(v -> startActivity(new Intent(requireContext(), SearchActivity.class)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public static class Category {
        String name;
        int iconRes;

        Category(String name, int iconRes) {
            this.name = name;
            this.iconRes = iconRes;
        }
    }

    // --- Category adapter ---

    private static class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {
        private final List<Category> categories;

        CategoryAdapter(List<Category> categories) {
            this.categories = categories;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCategoryBinding binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Category category = categories.get(position);
            holder.binding.textViewCategoryName.setText(category.name);
            holder.binding.imageViewCategory.setImageResource(category.iconRes);
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), SearchActivity.class);
                intent.putExtra(SearchActivity.EXTRA_CATEGORY, category.name);
                v.getContext().startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return categories.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ItemCategoryBinding binding;
            ViewHolder(ItemCategoryBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

}
