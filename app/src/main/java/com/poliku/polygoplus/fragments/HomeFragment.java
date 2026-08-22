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
import com.poliku.polygoplus.network.NetworkApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private ProductCardAdapter productAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
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
        reloadProducts();

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.home_top), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top + v.getPaddingTop(), v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    private void setupHeader() {
        String name = AppDataStore.userName(requireContext());
        if (name == null || name.trim().isEmpty() || "PolyGo member".equals(name)) {
            binding.tvGreeting.setText("Good to see you 👋");
        } else {
            String hourGreeting;
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            if (hour < 12) hourGreeting = "Good morning";
            else if (hour < 18) hourGreeting = "Good afternoon";
            else hourGreeting = "Good evening";
            binding.tvGreeting.setText(hourGreeting + ", " + name + " 👋");
        }
        binding.ivProfilePic.setOnClickListener(v -> startActivity(new Intent(requireContext(), AccountActivity.class)));
    }

    private void setupCategories() {
        List<Category> categories = new ArrayList<>();
        categories.add(new Category("Food", R.drawable.ic_category_food));
        categories.add(new Category("Drink", R.drawable.ic_category_drink));
        categories.add(new Category("Tech", R.drawable.ic_category_tech));
        categories.add(new Category("Books", R.drawable.ic_category_books));
        categories.add(new Category("Repair", R.drawable.ic_category_repair));

        binding.recyclerViewCategories.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerViewCategories.setAdapter(new CategoryAdapter(categories));
    }

    private void setupProducts() {
        productAdapter = new ProductCardAdapter(new ArrayList<>(), (adapter, product) -> {
            Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
            intent.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, product.id);
            startActivity(intent);
        });
        binding.recyclerViewProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.recyclerViewProducts.setAdapter(productAdapter);
    }

    private void reloadProducts() {
        NetworkApi.getListings(new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                List<AppDataStore.ProductRecord> list = new ArrayList<>();
                JSONArray arr = response.optJSONArray("listings");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null) {
                            AppDataStore.ProductRecord p = AppDataStore.ProductRecord.fromJson(o);
                            if (p != null) list.add(p);
                        }
                    }
                }
                if (productAdapter != null) productAdapter.updateData(list);
            }

            @Override
            public void onError(String message) {
                if (productAdapter != null) productAdapter.updateData(AppDataStore.getListings(requireContext()));
            }
        });
    }

    private void setupSearchActions() {
        View.OnClickListener openSearch = v -> startActivity(new Intent(requireContext(), SearchActivity.class));
        binding.searchBarCard.setOnClickListener(openSearch);
        binding.btnExploreNow.setOnClickListener(openSearch);
        binding.tvSeeAllProducts.setOnClickListener(openSearch);
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
